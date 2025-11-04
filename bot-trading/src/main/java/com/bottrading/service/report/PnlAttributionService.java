package com.bottrading.service.report;

import com.bottrading.fees.FeeService;
import com.bottrading.fees.FeeService.FeeInfo;
import com.bottrading.model.dto.Kline;
import com.bottrading.model.entity.DecisionEntity;
import com.bottrading.model.entity.PnlAttributionEntity;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.entity.TradeEntity;
import com.bottrading.model.entity.TradeFillEntity;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.repository.DecisionRepository;
import com.bottrading.repository.PnlAttributionRepository;
import com.bottrading.repository.TradeFillRepository;
import com.bottrading.service.market.MarketDataService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PnlAttributionService {

  private static final BigDecimal TEN_THOUSAND = BigDecimal.valueOf(10_000L);

  private final PnlAttributionRepository repository;
  private final DecisionRepository decisionRepository;
  private final TradeFillRepository tradeFillRepository;
  private final MarketDataService marketDataService;
  private final FeeService feeService;
  private final MeterRegistry meterRegistry;

  private final ConcurrentMap<String, SymbolStats> slippageStats = new ConcurrentHashMap<>();
  private final DoubleAdder timingSum = new DoubleAdder();
  private final DoubleAdder timingNotional = new DoubleAdder();
  private final DoubleAdder feesSum = new DoubleAdder();
  private final DoubleAdder feesNotional = new DoubleAdder();
  private final AtomicReference<Double> timingGauge = new AtomicReference<>(0.0);
  private final AtomicReference<Double> feesGauge = new AtomicReference<>(0.0);

  public PnlAttributionService(
      PnlAttributionRepository repository,
      DecisionRepository decisionRepository,
      TradeFillRepository tradeFillRepository,
      MarketDataService marketDataService,
      FeeService feeService,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.decisionRepository = decisionRepository;
    this.tradeFillRepository = tradeFillRepository;
    this.marketDataService = marketDataService;
    this.feeService = feeService;
    this.meterRegistry = meterRegistry;
    Gauge.builder("attr.timing.avg_bps", timingGauge, AtomicReference::get)
        .register(meterRegistry);
    Gauge.builder("attr.fees.avg_bps", feesGauge, AtomicReference::get)
        .register(meterRegistry);
  }

  @Transactional
  public void record(PositionEntity position, TradeEntity trade) {
    if (position == null || trade == null || trade.getId() == null) {
      return;
    }
    BigDecimal quantity = Optional.ofNullable(trade.getQuantity()).orElse(BigDecimal.ZERO);
    if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
      return;
    }
    BigDecimal entryPrice = position.getEntryPrice();
    BigDecimal fillPrice = Optional.ofNullable(trade.getPrice()).orElse(entryPrice);
    if (entryPrice == null || fillPrice == null) {
      return;
    }
    BigDecimal direction = position.getSide() == OrderSide.BUY ? BigDecimal.ONE : BigDecimal.valueOf(-1);
    BigDecimal pnlGross = fillPrice.subtract(entryPrice).multiply(quantity).multiply(direction);
    pnlGross = scaleMoney(pnlGross);

    DecisionEntity decision = resolveDecision(position, trade);
    BigDecimal benchmark = resolveBenchmarkPrice(position, decision, entryPrice);
    BigDecimal signalEdge = pnlGross;
    BigDecimal timingCost = BigDecimal.ZERO;
    if (benchmark != null) {
      signalEdge = fillPrice.subtract(benchmark).multiply(quantity).multiply(direction);
      signalEdge = scaleMoney(signalEdge);
      timingCost = signalEdge.subtract(pnlGross);
      timingCost = scaleMoney(timingCost);
    }

    TradeFillEntity fill = resolveFill(trade.getOrderId());
    BigDecimal referencePrice =
        fill != null && fill.getRefPrice() != null
            ? fill.getRefPrice()
            : Objects.requireNonNullElse(benchmark, entryPrice);
    BigDecimal slippageCost = BigDecimal.ZERO;
    BigDecimal slippageBps = BigDecimal.ZERO;
    if (referencePrice != null && referencePrice.compareTo(BigDecimal.ZERO) > 0) {
      slippageCost = fillPrice.subtract(referencePrice).multiply(quantity).multiply(direction);
      slippageCost = scaleMoney(slippageCost);
      BigDecimal denom = referencePrice.multiply(quantity).abs();
      if (denom.compareTo(BigDecimal.ZERO) > 0) {
        slippageBps =
            slippageCost.divide(denom, 8, RoundingMode.HALF_UP).multiply(TEN_THOUSAND).setScale(4, RoundingMode.HALF_UP);
      }
    }

    BigDecimal notional = fillPrice.multiply(quantity).abs();
    notional = scaleMoney(notional);
    FeeInfo feeInfo = fetchFees(position.getSymbol());
    BigDecimal feeRate = feeInfo == null ? BigDecimal.ZERO : Optional.ofNullable(feeInfo.taker()).orElse(BigDecimal.ZERO);
    BigDecimal feesCost = notional.multiply(feeRate);
    feesCost = scaleMoney(feesCost);
    BigDecimal feesBps = feeRate.multiply(TEN_THOUSAND).setScale(4, RoundingMode.HALF_UP);

    BigDecimal timingBps = BigDecimal.ZERO;
    if (notional.compareTo(BigDecimal.ZERO) > 0) {
      timingBps =
          timingCost.divide(notional, 8, RoundingMode.HALF_UP).multiply(TEN_THOUSAND).setScale(4, RoundingMode.HALF_UP);
      if (slippageBps.compareTo(BigDecimal.ZERO) == 0 && slippageCost.compareTo(BigDecimal.ZERO) != 0) {
        slippageBps =
            slippageCost
                .divide(notional, 8, RoundingMode.HALF_UP)
                .multiply(TEN_THOUSAND)
                .setScale(4, RoundingMode.HALF_UP);
      }
    }

    BigDecimal pnlNet = pnlGross.subtract(feesCost).subtract(slippageCost).subtract(timingCost);
    pnlNet = scaleMoney(pnlNet);

    PnlAttributionEntity entity = new PnlAttributionEntity();
    entity.setTradeId(trade.getId());
    entity.setSymbol(position.getSymbol());
    entity.setSignal(resolveSignal(position, decision));
    entity.setRegime(resolveRegime(position, decision));
    entity.setPreset(resolvePreset(position, decision));
    entity.setPnlGross(pnlGross);
    entity.setSignalEdge(signalEdge);
    entity.setTimingCost(timingCost);
    entity.setTimingBps(timingBps);
    entity.setSlippageCost(slippageCost);
    entity.setSlippageBps(slippageBps);
    entity.setFeesCost(feesCost);
    entity.setFeesBps(feesBps);
    entity.setPnlNet(pnlNet);
    entity.setNotional(notional);
    entity.setTimestamp(Optional.ofNullable(trade.getExecutedAt()).orElse(Instant.now()));
    repository.save(entity);

    updateMetrics(position.getSymbol(), slippageBps, slippageCost, timingBps, timingCost, feesBps, feesCost, notional);
  }

  private DecisionEntity resolveDecision(PositionEntity position, TradeEntity trade) {
    String key = Optional.ofNullable(position.getCorrelationId()).orElse(trade.getOrderId());
    if (key == null) {
      return null;
    }
    return decisionRepository.findByDecisionKey(key).orElse(null);
  }

  private TradeFillEntity resolveFill(String orderId) {
    if (orderId == null) {
      return null;
    }
    return tradeFillRepository.findTopByOrderIdOrderByExecutedAtDesc(orderId);
  }

  private BigDecimal resolveBenchmarkPrice(PositionEntity position, DecisionEntity decision, BigDecimal fallback) {
    if (decision == null || decision.getCloseTime() == null || decision.getInterval() == null) {
      return fallback;
    }
    try {
      List<Kline> klines =
          marketDataService.getKlines(
              position.getSymbol(), decision.getInterval(), decision.getCloseTime(), decision.getCloseTime(), 5);
      return klines.stream()
          .filter(k -> decision.getCloseTime().equals(k.closeTime()))
          .map(Kline::close)
          .findFirst()
          .orElse(fallback);
    } catch (Exception ex) {
      return fallback;
    }
  }

  private FeeInfo fetchFees(String symbol) {
    try {
      return feeService.effectiveFees(symbol, true);
    } catch (Exception ex) {
      return null;
    }
  }

  private String resolveSignal(PositionEntity position, DecisionEntity decision) {
    if (decision != null && decision.getDecisionKey() != null) {
      return decision.getDecisionKey();
    }
    return position.getCorrelationId();
  }

  private String resolvePreset(PositionEntity position, DecisionEntity decision) {
    if (position.getPresetKey() != null) {
      return position.getPresetKey();
    }
    if (decision != null) {
      return decision.getPresetKey();
    }
    return null;
  }

  private String resolveRegime(PositionEntity position, DecisionEntity decision) {
    String trend = Optional.ofNullable(position.getRegimeTrend()).orElseGet(() -> decision != null ? decision.getRegimeTrend() : null);
    String vol = Optional.ofNullable(position.getRegimeVolatility()).orElseGet(() -> decision != null ? decision.getRegimeVolatility() : null);
    if (trend == null && vol == null) {
      return null;
    }
    if (trend == null) {
      return vol;
    }
    if (vol == null) {
      return trend;
    }
    return trend + "/" + vol;
  }

  private BigDecimal scaleMoney(BigDecimal value) {
    return value == null ? null : value.setScale(8, RoundingMode.HALF_UP);
  }

  private void updateMetrics(
      String symbol,
      BigDecimal slippageBps,
      BigDecimal slippageCost,
      BigDecimal timingBps,
      BigDecimal timingCost,
      BigDecimal feesBps,
      BigDecimal feesCost,
      BigDecimal notional) {
    if (symbol != null && slippageBps != null) {
      SymbolStats stats =
          slippageStats.computeIfAbsent(
              symbol,
              key -> {
                SymbolStats created = new SymbolStats();
                Gauge.builder("attr.slippage.avg_bps", created.averageBps, AtomicReference::get)
                    .tags("symbol", key)
                    .register(meterRegistry);
                return created;
              });
      stats.accumulate(slippageBps.doubleValue(), notional);
    }

    if (timingBps != null && notional != null && notional.compareTo(BigDecimal.ZERO) > 0) {
      timingSum.add(timingCost.doubleValue());
      timingNotional.add(notional.doubleValue());
      double denom = timingNotional.doubleValue();
      timingGauge.set(denom == 0.0 ? 0.0 : timingSum.doubleValue() / denom * 10_000d);
    }

    if (feesBps != null && notional != null && notional.compareTo(BigDecimal.ZERO) > 0) {
      feesSum.add(feesCost.doubleValue());
      feesNotional.add(notional.doubleValue());
      double denom = feesNotional.doubleValue();
      feesGauge.set(denom == 0.0 ? 0.0 : feesSum.doubleValue() / denom * 10_000d);
    }
  }

  private static class SymbolStats {
    private final DoubleAdder slippageCost = new DoubleAdder();
    private final DoubleAdder notional = new DoubleAdder();
    private final AtomicReference<Double> averageBps = new AtomicReference<>(0.0);

    void accumulate(double slippage, BigDecimal tradeNotional) {
      if (tradeNotional == null) {
        return;
      }
      double n = tradeNotional.doubleValue();
      if (!Double.isFinite(slippage) || n <= 0.0) {
        return;
      }
      double cost = slippage / 10_000d * n;
      slippageCost.add(cost);
      notional.add(n);
      double denom = notional.doubleValue();
      averageBps.set(denom == 0.0 ? 0.0 : slippageCost.doubleValue() / denom * 10_000d);
    }
  }
}
