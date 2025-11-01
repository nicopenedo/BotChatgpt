package com.bottrading.service.risk;

import com.bottrading.config.TradingProps;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RiskGuard {

  private static final Logger log = LoggerFactory.getLogger(RiskGuard.class);

  private final TradingProps tradingProperties;
  private final TradingState tradingState;
  private final Counter stopouts;
  private final AtomicReference<BigDecimal> equityStart =
      new AtomicReference<>(BigDecimal.ZERO);
  private final AtomicReference<BigDecimal> equityPeak =
      new AtomicReference<>(BigDecimal.ZERO);
  private final AtomicReference<BigDecimal> currentEquity =
      new AtomicReference<>(BigDecimal.ZERO);
  private final AtomicReference<Instant> lastReset = new AtomicReference<>(Instant.now());

  public RiskGuard(TradingProps tradingProperties, TradingState tradingState, MeterRegistry meterRegistry) {
    this.tradingProperties = tradingProperties;
    this.tradingState = tradingState;
    this.stopouts = meterRegistry.counter("risk.stopouts", Tags.empty());
    meterRegistry.gauge("risk.equity", Tags.empty(), currentEquity, ref -> ref.get().doubleValue());
    meterRegistry.gauge("risk.drawdown", Tags.empty(), this, guard -> guard.currentDrawdown().doubleValue());
  }

  public synchronized void onEquityUpdate(BigDecimal equity) {
    if (equity == null) {
      return;
    }
    resetIfNeeded();
    currentEquity.set(equity);
    equityStart.updateAndGet(prev -> prev.compareTo(BigDecimal.ZERO) == 0 ? equity : prev);
    equityPeak.updateAndGet(prev -> prev.compareTo(BigDecimal.ZERO) == 0 ? equity : prev.max(equity));
    checkLimits();
  }

  public synchronized boolean canTrade() {
    resetIfNeeded();
    if (tradingState.isKillSwitchActive()) {
      return false;
    }
    if (tradingState.isCoolingDown()) {
      return false;
    }
    checkLimits();
    return !tradingState.isCoolingDown();
  }

  private void resetIfNeeded() {
    Instant now = Instant.now();
    Instant last = lastReset.get();
    if (Duration.between(last, now).toHours() >= 24) {
      equityStart.set(currentEquity.get());
      equityPeak.set(currentEquity.get());
      lastReset.set(now);
    }
  }

  private void checkLimits() {
    BigDecimal start = equityStart.get();
    BigDecimal peak = equityPeak.get();
    BigDecimal current = currentEquity.get();
    if (start.compareTo(BigDecimal.ZERO) == 0) {
      return;
    }
    BigDecimal drawdownPct = currentDrawdown();
    BigDecimal lossPct = start.subtract(current).divide(start, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

    if (drawdownPct.compareTo(tradingProperties.getMaxDrawdownPct()) > 0
        || lossPct.compareTo(tradingProperties.getMaxDailyLossPct()) > 0) {
      log.warn("Risk thresholds breached: drawdown={} loss={}. Activating cooldown", drawdownPct, lossPct);
      stopouts.increment();
      tradingState.activateKillSwitch();
      tradingState.setCooldownUntil(Instant.now().plus(Duration.ofMinutes(tradingProperties.getCooldownMinutes())));
    }
  }

  private BigDecimal currentDrawdown() {
    BigDecimal peak = equityPeak.get();
    BigDecimal current = currentEquity.get();
    if (peak.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return peak.subtract(current).divide(peak, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
  }
}
