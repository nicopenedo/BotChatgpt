package com.bottrading.shadow;

import com.bottrading.config.ShadowProperties;
import com.bottrading.execution.StopEngine.StopPlan;
import com.bottrading.model.entity.ShadowPositionEntity;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PositionStatus;
import com.bottrading.notify.TelegramNotifier;
import com.bottrading.repository.ShadowPositionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import com.bottrading.service.risk.drift.DriftWatchdog;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class ShadowEngine {

  private final ShadowProperties properties;
  private final ShadowPositionRepository repository;
  private final TelegramNotifier notifier;
  private final Clock clock;
  private final Counter divergenceAlerts;
  private final ConcurrentMap<String, BigDecimal> livePnl = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, BigDecimal> shadowPnl = new ConcurrentHashMap<>();
  private final DriftWatchdog driftWatchdog;

  public ShadowEngine(
      ShadowProperties properties,
      ShadowPositionRepository repository,
      TelegramNotifier notifier,
      MeterRegistry meterRegistry,
      Optional<Clock> clock,
      DriftWatchdog driftWatchdog) {
    this.properties = properties;
    this.repository = repository;
    this.notifier = notifier;
    this.clock = clock.orElse(Clock.systemUTC());
    this.driftWatchdog = driftWatchdog;
    this.divergenceAlerts = meterRegistry.counter("shadow.divergence.alerts");
    Gauge.builder("shadow.pnl.live", () -> livePnl.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue())
        .register(meterRegistry);
    Gauge.builder(
            "shadow.pnl.shadow",
            () -> shadowPnl.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue())
        .register(meterRegistry);
  }

  public boolean isEnabled() {
    return properties.isEnabled();
  }

  public ShadowPositionEntity registerShadow(
      String symbol, OrderSide side, BigDecimal entryPrice, BigDecimal quantity, StopPlan plan) {
    if (!isEnabled()) {
      return null;
    }
    ShadowPositionEntity entity = new ShadowPositionEntity();
    entity.setSymbol(symbol);
    entity.setSide(side);
    entity.setEntryPrice(entryPrice);
    entity.setQuantity(quantity);
    entity.setStopLoss(plan.stopLoss());
    entity.setTakeProfit(plan.takeProfit());
    entity.setOpenedAt(Instant.now(clock));
    return repository.save(entity);
  }

  public void onPriceUpdate(String symbol, BigDecimal price) {
    if (!isEnabled()) {
      return;
    }
    List<ShadowPositionEntity> openPositions = repository.findBySymbolOrderByOpenedAtDesc(symbol);
    for (ShadowPositionEntity position : openPositions) {
      if (position.getStatus() != PositionStatus.OPEN) {
        continue;
      }
      boolean hit = false;
      if (position.getSide() == OrderSide.BUY) {
        if (price.compareTo(position.getStopLoss()) <= 0) {
          closeShadow(position, price);
          hit = true;
        } else if (price.compareTo(position.getTakeProfit()) >= 0) {
          closeShadow(position, price);
          hit = true;
        }
      } else {
        if (price.compareTo(position.getStopLoss()) >= 0) {
          closeShadow(position, price);
          hit = true;
        } else if (price.compareTo(position.getTakeProfit()) <= 0) {
          closeShadow(position, price);
          hit = true;
        }
      }
      if (hit) {
        evaluateDivergence(symbol);
      }
    }
  }

  public void registerLiveFill(String symbol, BigDecimal pnl) {
    livePnl.merge(symbol, pnl, BigDecimal::add);
    evaluateDivergence(symbol);
  }

  public void registerShadowFill(String symbol, BigDecimal pnl) {
    shadowPnl.merge(symbol, pnl, BigDecimal::add);
    evaluateDivergence(symbol);
    driftWatchdog.recordShadowTrade(symbol, pnl.doubleValue());
  }

  private void closeShadow(ShadowPositionEntity position, BigDecimal exitPrice) {
    position.setExitPrice(exitPrice);
    position.setClosedAt(Instant.now(clock));
    position.setStatus(PositionStatus.CLOSED);
    BigDecimal pnl = exitPrice.subtract(position.getEntryPrice());
    if (position.getSide() == OrderSide.SELL) {
      pnl = pnl.negate();
    }
    pnl = pnl.multiply(position.getQuantity()).setScale(8, RoundingMode.HALF_UP);
    position.setRealizedPnl(pnl);
    position.setTrades(position.getTrades() + 1);
    repository.save(position);
    registerShadowFill(position.getSymbol(), pnl);
  }

  private void evaluateDivergence(String symbol) {
    BigDecimal live = livePnl.getOrDefault(symbol, BigDecimal.ZERO);
    BigDecimal shadow = shadowPnl.getOrDefault(symbol, BigDecimal.ZERO);
    BigDecimal diff = shadow.subtract(live);
    BigDecimal base = live.abs().max(BigDecimal.ONE);
    BigDecimal diffPct = diff.divide(base, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    long trades = repository.findBySymbolOrderByOpenedAtDesc(symbol).stream().filter(p -> p.getStatus() != PositionStatus.OPEN).count();
    if (diffPct.abs().compareTo(properties.getDivergencePctThreshold()) >= 0
        && trades >= properties.getDivergenceMinTrades()) {
      notifier.notifyDivergence(symbol, live, shadow, diffPct);
      divergenceAlerts.increment();
    }
  }

  public ShadowStatus status(String symbol) {
    List<ShadowPositionEntity> positions = repository.findBySymbolOrderByOpenedAtDesc(symbol);
    BigDecimal live = livePnl.getOrDefault(symbol, BigDecimal.ZERO);
    BigDecimal shadow = shadowPnl.getOrDefault(symbol, BigDecimal.ZERO);
    return new ShadowStatus(live, shadow, positions);
  }

  public record ShadowStatus(BigDecimal livePnl, BigDecimal shadowPnl, List<ShadowPositionEntity> positions) {}
}
