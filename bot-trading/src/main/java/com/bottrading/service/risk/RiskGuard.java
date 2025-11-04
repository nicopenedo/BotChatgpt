package com.bottrading.service.risk;

import com.bottrading.config.RiskProperties;
import com.bottrading.config.TradingProps;
import com.bottrading.model.entity.RiskEventEntity;
import com.bottrading.repository.RiskEventRepository;
import com.bottrading.saas.security.TenantContext;
import com.bottrading.saas.service.TenantAccountService;
import com.bottrading.saas.service.TenantMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RiskGuard {

  private static final Logger log = LoggerFactory.getLogger(RiskGuard.class);

  private final TradingProps tradingProperties;
  private final RiskProperties riskProperties;
  private final TradingState tradingState;
  private final RiskAction riskAction;
  private final RiskEventRepository riskEventRepository;
  private final Counter stopouts;
  private final IntradayVarService intradayVarService;
  private final TenantMetrics tenantMetrics;
  private final TenantAccountService tenantAccountService;

  private final AtomicReference<BigDecimal> equityStart = new AtomicReference<>(BigDecimal.ZERO);
  private final AtomicReference<BigDecimal> equityPeak = new AtomicReference<>(BigDecimal.ZERO);
  private final AtomicReference<BigDecimal> currentEquity = new AtomicReference<>(BigDecimal.ZERO);
  private final AtomicReference<BigDecimal> dailyPnl = new AtomicReference<>(BigDecimal.ZERO);
  private final AtomicReference<BigDecimal> dailyLossPct = new AtomicReference<>(BigDecimal.ZERO);
  private final AtomicReference<BigDecimal> currentDrawdownPct = new AtomicReference<>(BigDecimal.ZERO);
  private final AtomicReference<BigDecimal> maxDrawdownPct = new AtomicReference<>(BigDecimal.ZERO);
  private final AtomicReference<Double> apiErrorRate = new AtomicReference<>(0.0);
  private final AtomicInteger openingsToday = new AtomicInteger();
  private final AtomicInteger apiErrors = new AtomicInteger();
  private final AtomicInteger apiCalls = new AtomicInteger();
  private final Deque<Instant> wsReconnects = new ArrayDeque<>();
  private final AtomicInteger wsReconnectGauge = new AtomicInteger();
  private final AtomicInteger modeGauge = new AtomicInteger();
  private final AtomicBoolean marketDataStale = new AtomicBoolean();
  private final AtomicInteger marketDataGauge = new AtomicInteger();

  private final EnumSet<RiskFlag> flags = EnumSet.noneOf(RiskFlag.class);
  private final java.util.Map<RiskFlag, Instant> temporaryFlags = new java.util.EnumMap<>(RiskFlag.class);
  private final java.util.Map<RiskFlag, String> temporaryDetails = new java.util.EnumMap<>(RiskFlag.class);

  private LocalDate currentDay = LocalDate.now(ZoneOffset.UTC);
  private Instant lastReset = Instant.now();

  public RiskGuard(
      TradingProps tradingProperties,
      RiskProperties riskProperties,
      TradingState tradingState,
      RiskAction riskAction,
      RiskEventRepository riskEventRepository,
      MeterRegistry meterRegistry,
      IntradayVarService intradayVarService,
      TenantMetrics tenantMetrics,
      TenantAccountService tenantAccountService) {
    this.tradingProperties = tradingProperties;
    this.riskProperties = riskProperties;
    this.tradingState = tradingState;
    this.riskAction = riskAction;
    this.riskEventRepository = riskEventRepository;
    this.stopouts = meterRegistry.counter("risk.stopouts", tenantMetrics.tags(tradingProperties.getSymbol()));
    this.intradayVarService = intradayVarService;
    this.tenantMetrics = tenantMetrics;
    this.tenantAccountService = tenantAccountService;
    Tags tags = tenantMetrics.tags(tradingProperties.getSymbol());
    Gauge.builder("risk.equity", currentEquity, ref -> ref.get().doubleValue())
        .tags(tags)
        .register(meterRegistry);
    Gauge.builder("risk.daily_pnl", dailyPnl, ref -> ref.get().doubleValue())
        .tags(tags)
        .register(meterRegistry);
    Gauge.builder("risk.dd_max", maxDrawdownPct, ref -> ref.get().doubleValue())
        .tags(tags)
        .register(meterRegistry);
    Gauge.builder("risk.api_error_rate", apiErrorRate, AtomicReference::get)
        .tags(tags)
        .register(meterRegistry);
    Gauge.builder("risk.ws_reconnects", wsReconnectGauge, AtomicInteger::get)
        .tags(tags)
        .register(meterRegistry);
    Gauge.builder("bot.mode", modeGauge, AtomicInteger::get)
        .tags(tags)
        .register(meterRegistry);
    Gauge.builder("risk.market_data_stale", marketDataGauge, AtomicInteger::get)
        .tags(tags)
        .register(meterRegistry);
    Gauge.builder(
            "risk.daily_loss.limit_pct",
            riskProperties,
            props -> props.getMaxDailyLossPct() != null ? props.getMaxDailyLossPct().doubleValue() : 0.0)
        .tags(tags)
        .register(meterRegistry);
    updateModeGauge();
  }

  public synchronized void onEquityUpdate(BigDecimal equity) {
    if (equity == null) {
      return;
    }
    resetIfNeeded();
    currentEquity.set(equity);
    if (intradayVarService != null && intradayVarService.isEnabled()) {
      intradayVarService.updateEquity(equity);
    }
    if (equityStart.get().compareTo(BigDecimal.ZERO) == 0) {
      equityStart.set(equity);
      equityPeak.set(equity);
    }
    if (equity.compareTo(equityPeak.get()) > 0) {
      equityPeak.set(equity);
    }
    evaluateLosses();
  }

  public synchronized boolean canTrade() {
    return canOpen(tradingProperties.getSymbol());
  }

  public synchronized boolean canOpen(String symbol) {
    resetIfNeeded();
    pruneTemporaryFlags();
    java.util.UUID tenantId = TenantContext.getTenantId();
    if (tenantId != null && tenantAccountService != null && tenantAccountService.isTradingPaused(tenantId)) {
      return false;
    }
    if (tradingState.isKillSwitchActive() || tradingState.isCoolingDown()) {
      return false;
    }
    if (marketDataStale.get()) {
      return false;
    }
    if (!flags.isEmpty() && tradingState.getMode() == TradingState.Mode.PAUSED) {
      return false;
    }
    if (riskProperties.getMaxOpeningsPerDay() > 0
        && openingsToday.get() >= riskProperties.getMaxOpeningsPerDay()) {
      triggerPause(
          RiskFlag.TRADE_LIMIT,
          "Max openings reached: " + openingsToday.get() + "/" + riskProperties.getMaxOpeningsPerDay());
      return false;
    }
    if (intradayVarService != null
        && intradayVarService.isEnabled()
        && currentEquity.get().compareTo(BigDecimal.ZERO) > 0) {
      IntradayVarService.ExposureSnapshot exposure = intradayVarService.exposure(currentEquity.get());
      if (exposure.limit().compareTo(BigDecimal.ZERO) > 0
          && exposure.exposure().compareTo(exposure.limit()) >= 0) {
        log.debug(
            "VAR daily budget reached for {} exposure={} limit={}",
            symbol,
            exposure.exposure(),
            exposure.limit());
        return false;
      }
    }
    return true;
  }

  public synchronized void onTrade(TradeEvent event) {
    Objects.requireNonNull(event, "event");
    resetIfNeeded();
    if (event.opening()) {
      int count = openingsToday.incrementAndGet();
      if (riskProperties.getMaxOpeningsPerDay() > 0
          && count > riskProperties.getMaxOpeningsPerDay()) {
        triggerPause(
            RiskFlag.TRADE_LIMIT,
            "Openings today=" + count + ", limit=" + riskProperties.getMaxOpeningsPerDay());
      }
    }
    if (event.pnl() != null) {
      dailyPnl.updateAndGet(prev -> prev.add(event.pnl()));
    }
    if (event.notional() != null
        && riskProperties.getMaxTradeNotional().compareTo(BigDecimal.ZERO) > 0
        && event.notional().compareTo(riskProperties.getMaxTradeNotional()) > 0) {
      triggerPause(
          RiskFlag.TRADE_SIZE,
          "Trade notional=" + event.notional() + ", limit=" + riskProperties.getMaxTradeNotional());
    }
    if (event.equityAfter() != null) {
      onEquityUpdate(event.equityAfter());
    }
  }

  public synchronized void onApiError() {
    resetIfNeeded();
    apiErrors.incrementAndGet();
    apiCalls.incrementAndGet();
    updateApiErrorRate();
  }

  public synchronized void onApiSuccess() {
    resetIfNeeded();
    apiCalls.incrementAndGet();
    updateApiErrorRate();
  }

  public synchronized void onWsReconnect() {
    resetIfNeeded();
    Instant now = Instant.now();
    wsReconnects.addLast(now);
    trimReconnects(now);
    wsReconnectGauge.set(wsReconnects.size());
    if (riskProperties.getMaxWsReconnectsPerHour() > 0
        && wsReconnects.size() > riskProperties.getMaxWsReconnectsPerHour()) {
      triggerPause(
          RiskFlag.WS_RECONNECTS,
          "WS reconnects="
              + wsReconnects.size()
              + ", limit="
              + riskProperties.getMaxWsReconnectsPerHour());
    }
  }

  public synchronized void acknowledge() {
    flags.clear();
  }

  public synchronized void setMarketDataStale(boolean stale) {
    marketDataStale.set(stale);
    marketDataGauge.set(stale ? 1 : 0);
  }

  public boolean isMarketDataStale() {
    return marketDataStale.get();
  }

  public synchronized void setMode(RiskMode mode) {
    tradingState.setMode(mode.toTradingMode());
    if (mode == RiskMode.PAUSED) {
      tradingState.activateKillSwitch();
    } else {
      tradingState.deactivateKillSwitch();
    }
    updateModeGauge();
  }

  public synchronized RiskState getState() {
    pruneTemporaryFlags();
    IntradayVarService.ExposureSnapshot exposure =
        intradayVarService != null
                && intradayVarService.isEnabled()
                && currentEquity.get().compareTo(BigDecimal.ZERO) > 0
            ? intradayVarService.exposure(currentEquity.get())
            : new IntradayVarService.ExposureSnapshot(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    return new RiskState(
        RiskMode.fromTradingState(tradingState.getMode()),
        dailyPnl.get(),
        dailyLossPct.get(),
        maxDrawdownPct.get(),
        currentDrawdownPct.get(),
        BigDecimal.valueOf(apiErrorRate.get()),
        wsReconnectGauge.get(),
        openingsToday.get(),
        currentEquity.get(),
        flags,
        java.util.Map.copyOf(temporaryFlags),
        lastReset,
        exposure.exposure(),
        exposure.limit(),
        exposure.ratio(),
        marketDataStale.get());
  }

  public synchronized void applyTemporaryFlag(RiskFlag flag, Duration duration, String detail) {
    if (flag == null || duration == null) {
      return;
    }
    pruneTemporaryFlags();
    Instant expiry = Instant.now().plus(duration);
    temporaryFlags.put(flag, expiry);
    if (detail != null) {
      temporaryDetails.put(flag, detail);
    }
    tradingState.setCooldownUntil(expiry);
  }

  private void evaluateLosses() {
    BigDecimal start = equityStart.get();
    BigDecimal peak = equityPeak.get();
    BigDecimal current = currentEquity.get();
    if (start.compareTo(BigDecimal.ZERO) == 0 || peak.compareTo(BigDecimal.ZERO) == 0) {
      return;
    }
    BigDecimal drawdown =
        peak.subtract(current).max(BigDecimal.ZERO).divide(peak, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    currentDrawdownPct.set(drawdown);
    maxDrawdownPct.updateAndGet(prev -> prev.max(drawdown));
    BigDecimal loss =
        start.subtract(current).max(BigDecimal.ZERO).divide(start, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    dailyLossPct.set(loss);
    if (riskProperties.getMaxDrawdownPct().compareTo(BigDecimal.ZERO) > 0
        && drawdown.compareTo(riskProperties.getMaxDrawdownPct()) > 0) {
      triggerPause(
          RiskFlag.DRAWDOWN,
          "Drawdown=" + drawdown.setScale(2, RoundingMode.HALF_UP) + "% exceeded threshold");
    }
    if (riskProperties.getMaxDailyLossPct().compareTo(BigDecimal.ZERO) > 0
        && loss.compareTo(riskProperties.getMaxDailyLossPct()) > 0) {
      triggerPause(
          RiskFlag.DAILY_LOSS,
          "Daily loss=" + loss.setScale(2, RoundingMode.HALF_UP) + "% exceeded threshold");
    }
  }

  private void updateApiErrorRate() {
    int total = apiCalls.get();
    double rate = total == 0 ? 0.0 : (apiErrors.get() * 100.0) / total;
    apiErrorRate.set(rate);
    if (riskProperties.getMaxApiErrorPct().compareTo(BigDecimal.ZERO) > 0
        && rate > riskProperties.getMaxApiErrorPct().doubleValue()) {
      triggerPause(
          RiskFlag.API_ERRORS,
          "API error rate=" + String.format("%.2f", rate) + "% exceeded threshold");
    }
  }

  private void triggerPause(RiskFlag flag, String detail) {
    if (flags.contains(flag)) {
      return;
    }
    flags.add(flag);
    stopouts.increment();
    tradingState.activateKillSwitch();
    tradingState.setMode(TradingState.Mode.PAUSED);
    updateModeGauge();
    persistEvent(flag, detail);
    riskAction.onPause(flag, detail, getState());
  }

  private void persistEvent(RiskFlag flag, String detail) {
    try {
      riskEventRepository.save(new RiskEventEntity(flag.name(), detail, Instant.now()));
    } catch (Exception ex) {
      log.warn("Failed to persist risk event {}: {}", flag, ex.getMessage());
    }
  }

  private void resetIfNeeded() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    if (!today.equals(currentDay)) {
      currentDay = today;
      lastReset = Instant.now();
      openingsToday.set(0);
      apiErrors.set(0);
      apiCalls.set(0);
      apiErrorRate.set(0.0);
      wsReconnects.clear();
      wsReconnectGauge.set(0);
      temporaryFlags.clear();
      temporaryDetails.clear();
      dailyPnl.set(BigDecimal.ZERO);
      dailyLossPct.set(BigDecimal.ZERO);
      currentDrawdownPct.set(BigDecimal.ZERO);
      maxDrawdownPct.set(BigDecimal.ZERO);
      equityStart.set(currentEquity.get());
      equityPeak.set(currentEquity.get());
    }
  }

  private void trimReconnects(Instant now) {
    while (!wsReconnects.isEmpty()) {
      Instant first = wsReconnects.peekFirst();
      if (Duration.between(first, now).toHours() >= 1) {
        wsReconnects.removeFirst();
      } else {
        break;
      }
    }
  }

  private void updateModeGauge() {
    modeGauge.set(RiskMode.fromTradingState(tradingState.getMode()).ordinal());
  }

  private void pruneTemporaryFlags() {
    if (temporaryFlags.isEmpty()) {
      return;
    }
    Instant now = Instant.now();
    java.util.Iterator<java.util.Map.Entry<RiskFlag, Instant>> it = temporaryFlags.entrySet().iterator();
    while (it.hasNext()) {
      java.util.Map.Entry<RiskFlag, Instant> entry = it.next();
      if (entry.getValue().isBefore(now)) {
        temporaryDetails.remove(entry.getKey());
        it.remove();
      }
    }
  }
}
