package com.bottrading.service;

import com.bottrading.config.TradingProps;
import com.bottrading.model.dto.AccountBalancesResponse;
import com.bottrading.execution.OrderSizingService;
import com.bottrading.execution.OrderSizingService.OrderSizingResult;
import com.bottrading.execution.PositionManager;
import com.bottrading.execution.StopEngine;
import com.bottrading.execution.StopEngine.StopPlan;
import com.bottrading.model.dto.ExchangeInfo;
import com.bottrading.model.dto.PriceTicker;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.execution.ExecutionEngine;
import com.bottrading.execution.ExecutionEngine.ExecutionResult;
import com.bottrading.execution.ExecutionRequest;
import com.bottrading.execution.ExecutionRequest.Urgency;
import com.bottrading.execution.MarketSnapshot;
import com.bottrading.service.anomaly.AnomalyDetector;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.risk.IntradayVarService;
import com.bottrading.service.risk.IntradayVarService.VarAssessment;
import com.bottrading.service.risk.IntradayVarService.VarInput;
import com.bottrading.service.risk.RiskGuard;
import com.bottrading.service.risk.TradeEvent;
import com.bottrading.strategy.SignalResult;
import com.bottrading.strategy.SignalSide;
import com.bottrading.strategy.StrategyDecision;
import com.bottrading.strategy.StrategyContext;
import com.bottrading.shadow.ShadowEngine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderExecutionService {

  private static final Logger log = LoggerFactory.getLogger(OrderExecutionService.class);
  private static final Tags BUY_TAG = Tags.of("side", "BUY");
  private static final Tags SELL_TAG = Tags.of("side", "SELL");

  private final TradingProps tradingProps;
  private final BinanceClient binanceClient;
  private final com.bottrading.service.trading.OrderService orderService;
  private final RiskGuard riskGuard;
  private final MeterRegistry meterRegistry;
  private final OrderSizingService orderSizingService;
  private final StopEngine stopEngine;
  private final PositionManager positionManager;
  private final ShadowEngine shadowEngine;
  private final ExecutionEngine executionEngine;
  private final IntradayVarService intradayVarService;
  private final AnomalyDetector anomalyDetector;

  public OrderExecutionService(
          TradingProps tradingProps,
          BinanceClient binanceClient,
          com.bottrading.service.trading.OrderService orderService,
          RiskGuard riskGuard,
          MeterRegistry meterRegistry,
          OrderSizingService orderSizingService,
          StopEngine stopEngine,
          PositionManager positionManager,
          ShadowEngine shadowEngine,
          ExecutionEngine executionEngine,
          IntradayVarService intradayVarService,
          AnomalyDetector anomalyDetector) {
    this.tradingProps = tradingProps;
    this.binanceClient = binanceClient;
    this.orderService = orderService;
    this.riskGuard = riskGuard;
    this.meterRegistry = meterRegistry;
    this.orderSizingService = orderSizingService;
    this.stopEngine = stopEngine;
    this.positionManager = positionManager;
    this.shadowEngine = shadowEngine;
    this.executionEngine = executionEngine;
    this.intradayVarService = intradayVarService;
    this.anomalyDetector = anomalyDetector;
  }

  public Optional<ExecutionResult> execute(
          String decisionKey,
          String symbol,
          String interval,
          StrategyDecision decision,
          long closeTime,
          double sizingMultiplier) {
    SignalResult signal = decision.signal();
    if (signal.side() == SignalSide.FLAT) {
      log.debug("Decision {} is flat, no order to execute", decisionKey);
      return Optional.empty();
    }

    ExchangeInfo exchangeInfo = binanceClient.getExchangeInfo(symbol);
    PriceTicker priceTicker = binanceClient.getPrice(symbol);
    StrategyContext context = decision.context();
    BigDecimal lastPrice =
            context.lastPrice() != null ? context.lastPrice() : priceTicker.price();
    BigDecimal volume24h = context.volume24h();
    Double normalizedAtr = context.normalizedAtr();
    BigDecimal atr =
            normalizedAtr == null || lastPrice == null
                    ? null
                    : BigDecimal.valueOf(normalizedAtr).multiply(lastPrice);

    Assets assets = resolveAssets(symbol);
    AccountBalancesResponse balances =
            orderService.getBalances(List.of(assets.base(), assets.quote()));
    BigDecimal baseBalance = balanceOf(balances, assets.base());
    BigDecimal quoteBalance = balanceOf(balances, assets.quote());

    BigDecimal equity = quoteBalance.add(baseBalance.multiply(lastPrice));
    riskGuard.onEquityUpdate(equity);

    OrderSide orderSide = signal.side() == SignalSide.BUY ? OrderSide.BUY : OrderSide.SELL;
    StopPlan stopPlan = stopEngine.plan(symbol, orderSide, lastPrice, atr);
    OrderSizingResult sizingResult;
    try {
      sizingResult =
              orderSizingService.size(
                      orderSide, lastPrice, stopPlan.stopLoss(), atr, equity, exchangeInfo, sizingMultiplier);
    } catch (IllegalStateException | IllegalArgumentException ex) {
      log.warn("Sizing failed for {}: {}", decisionKey, ex.getMessage());
      return Optional.empty();
    }

    VarAssessment varAssessment =
            intradayVarService.assess(
                    new VarInput(
                            symbol,
                            decision.preset(),
                            decision.banditSelection() != null ? decision.banditSelection().presetId() : null,
                            decision.regime() != null ? decision.regime().trend().name() : null,
                            decision.regime() != null ? decision.regime().volatility().name() : null,
                            orderSide,
                            lastPrice,
                            stopPlan.stopLoss(),
                            sizingResult.quantity(),
                            equity,
                            exchangeInfo.stepSize()));
    if (varAssessment.blocked()) {
      log.info("VAR guard blocked {} reasons={}", decisionKey, varAssessment.reasons());
      return Optional.empty();
    }
    BigDecimal quantity = varAssessment.adjustedQuantity();
    if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
      log.info("VAR adjusted quantity to zero for {}", decisionKey);
      return Optional.empty();
    }
    if (orderSide == OrderSide.BUY) {
      BigDecimal requiredQuote = quantity.multiply(lastPrice);
      if (requiredQuote.compareTo(quoteBalance) > 0) {
        log.info("Quote balance {} insufficient for {}", quoteBalance, decisionKey);
        return Optional.empty();
      }
      meterRegistry.counter("orders.queued", BUY_TAG).increment();
    } else {
      quantity = quantity.min(baseBalance);
      if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
        log.info("Base balance {} insufficient to sell for {}", baseBalance, decisionKey);
        return Optional.empty();
      }
      meterRegistry.counter("orders.queued", SELL_TAG).increment();
    }

    BigDecimal notional = quantity.multiply(lastPrice);
    ExecutionRequest request =
            new ExecutionRequest(
                    symbol,
                    orderSide,
                    quantity,
                    lastPrice,
                    notional,
                    exchangeInfo,
                    resolveUrgency(decision.signal().confidence()),
                    estimateMaxSlippageBps(lastPrice, atr),
                    Instant.ofEpochMilli(closeTime).plusSeconds(120),
                    tradingProps.isDryRun(),
                    volume24h,
                    atr,
                    estimateSpreadBps(exchangeInfo, lastPrice),
                    estimateVolatilityBps(atr, lastPrice),
                    0,
                    newClientOrderId(symbol, interval, closeTime));

    MarketSnapshot snapshot =
            new MarketSnapshot(
                    lastPrice,
                    request.spreadBps(),
                    request.expectedVolatilityBps(),
                    request.latencyMs(),
                    estimateBarVolume(volume24h),
                    estimateQuoteBarVolume(volume24h, lastPrice));

    try {
      ExecutionResult result = executionEngine.execute(request, snapshot);
      log.info(
              "Order {} executed plan={} executedQty={} avgPrice={} dryRun={}",
              decisionKey,
              result.plan().getClass().getSimpleName(),
              result.executedQty(),
              result.averagePrice(),
              tradingProps.isDryRun());
      if (!tradingProps.isDryRun() && result.executedQty().compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal executedNotional = result.averagePrice().multiply(result.executedQty());
        riskGuard.onTrade(new TradeEvent(symbol, true, BigDecimal.ZERO, null, executedNotional));
        String lastClientOrderId =
                result.orders().isEmpty()
                        ? request.baseClientOrderId()
                        : result.orders().get(result.orders().size() - 1).clientOrderId();
        positionManager.openPosition(
                new PositionManager.OpenPositionCommand(
                        symbol,
                        orderSide,
                        result.averagePrice(),
                        result.executedQty(),
                        stopPlan.stopLoss(),
                        stopPlan.takeProfit(),
                        null,
                        lastClientOrderId,
                        decision.regime() != null ? decision.regime().trend().name() : null,
                        decision.regime() != null ? decision.regime().volatility().name() : null,
                        decision.preset(),
                        decision.banditSelection() != null ? decision.banditSelection().presetId() : null));
        shadowEngine.registerShadow(
                symbol,
                orderSide,
                result.averagePrice(),
                result.executedQty(),
                stopPlan,
                decision.regime() != null ? decision.regime().trend().name() : null,
                decision.regime() != null ? decision.regime().volatility().name() : null,
                decision.preset(),
                decision.banditSelection() != null ? decision.banditSelection().presetId() : null);
      }
      if (request.quantity().compareTo(BigDecimal.ZERO) > 0) {
        double fillRate =
                result.executedQty()
                        .divide(request.quantity(), 6, RoundingMode.HALF_UP)
                        .doubleValue();
        anomalyDetector.recordFillRate(symbol, fillRate);
      }
      return Optional.of(result);
    } catch (Exception ex) {
      log.error("Failed to execute order for {}: {}", decisionKey, ex.getMessage(), ex);
      anomalyDetector.recordFillRate(symbol, 0.0);
      return Optional.empty();
    }
  }

  private Urgency resolveUrgency(double confidence) {
    if (confidence >= 0.8) {
      return Urgency.HIGH;
    }
    if (confidence <= 0.35) {
      return Urgency.LOW;
    }
    return Urgency.MEDIUM;
  }

  private double estimateMaxSlippageBps(BigDecimal price, BigDecimal atr) {
    if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
      return 10.0;
    }
    if (atr != null && atr.compareTo(BigDecimal.ZERO) > 0) {
      return atr.divide(price, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(10000)).doubleValue();
    }
    return 10.0;
  }

  private double estimateSpreadBps(ExchangeInfo exchangeInfo, BigDecimal price) {
    if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
      return 5.0;
    }
    return exchangeInfo
            .tickSize()
            .divide(price, 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(10000))
            .doubleValue();
  }

  private double estimateVolatilityBps(BigDecimal atr, BigDecimal price) {
    if (atr == null || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
      return 0.0;
    }
    return atr.divide(price, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(10000)).doubleValue();
  }

  private BigDecimal estimateBarVolume(BigDecimal volume24h) {
    if (volume24h == null) {
      return BigDecimal.ZERO;
    }
    return volume24h.divide(BigDecimal.valueOf(1440), 8, RoundingMode.HALF_UP);
  }

  private BigDecimal estimateQuoteBarVolume(BigDecimal volume24h, BigDecimal price) {
    BigDecimal barVolume = estimateBarVolume(volume24h);
    if (price == null) {
      return BigDecimal.ZERO;
    }
    return barVolume.multiply(price);
  }

  private String newClientOrderId(String symbol, String interval, long closeTime) {
    return "candle-%s-%s-%d".formatted(symbol, interval, closeTime);
  }

  private BigDecimal balanceOf(AccountBalancesResponse response, String asset) {
    if (response == null || response.balances() == null) {
      return BigDecimal.ZERO;
    }
    return response.balances().stream()
            .filter(balance -> balance.asset().equalsIgnoreCase(asset))
            .findFirst()
            .map(AccountBalancesResponse.Balance::free)
            .orElse(BigDecimal.ZERO);
  }

  private Assets resolveAssets(String symbol) {
    String upper = symbol.toUpperCase();
    List<String> knownQuotes =
            List.of("USDT", "BUSD", "USDC", "BTC", "ETH", "BNB", "EUR", "TRY", "BIDR", "AUD");
    for (String quote : knownQuotes) {
      if (upper.endsWith(quote)) {
        String base = upper.substring(0, upper.length() - quote.length());
        return new Assets(base, quote);
      }
    }
    int mid = symbol.length() / 2;
    return new Assets(upper.substring(0, mid), upper.substring(mid));
  }

  private record Assets(String base, String quote) {}
}