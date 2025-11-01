package com.bottrading.service.risk;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class RiskState {

  private final RiskMode mode;
  private final BigDecimal dailyPnl;
  private final BigDecimal dailyLossPct;
  private final BigDecimal maxDrawdownPct;
  private final BigDecimal currentDrawdownPct;
  private final BigDecimal apiErrorRate;
  private final int wsReconnects;
  private final int openingsToday;
  private final BigDecimal currentEquity;
  private final Set<RiskFlag> flags;
  private final Instant lastReset;
  private final BigDecimal varExposure;
  private final BigDecimal varLimit;
  private final BigDecimal varRatio;
  private final boolean marketDataStale;

  public RiskState(
      RiskMode mode,
      BigDecimal dailyPnl,
      BigDecimal dailyLossPct,
      BigDecimal maxDrawdownPct,
      BigDecimal currentDrawdownPct,
      BigDecimal apiErrorRate,
      int wsReconnects,
      int openingsToday,
      BigDecimal currentEquity,
      Set<RiskFlag> flags,
      Instant lastReset,
      BigDecimal varExposure,
      BigDecimal varLimit,
      BigDecimal varRatio,
      boolean marketDataStale) {
    this.mode = mode;
    this.dailyPnl = dailyPnl;
    this.dailyLossPct = dailyLossPct;
    this.maxDrawdownPct = maxDrawdownPct;
    this.currentDrawdownPct = currentDrawdownPct;
    this.apiErrorRate = apiErrorRate;
    this.wsReconnects = wsReconnects;
    this.openingsToday = openingsToday;
    this.currentEquity = currentEquity;
    this.flags =
        Collections.unmodifiableSet(
            flags.isEmpty() ? EnumSet.noneOf(RiskFlag.class) : EnumSet.copyOf(flags));
    this.lastReset = lastReset;
    this.varExposure = varExposure;
    this.varLimit = varLimit;
    this.varRatio = varRatio;
    this.marketDataStale = marketDataStale;
  }

  public RiskMode mode() {
    return mode;
  }

  public BigDecimal dailyPnl() {
    return dailyPnl;
  }

  public BigDecimal dailyLossPct() {
    return dailyLossPct;
  }

  public BigDecimal maxDrawdownPct() {
    return maxDrawdownPct;
  }

  public BigDecimal currentDrawdownPct() {
    return currentDrawdownPct;
  }

  public BigDecimal apiErrorRate() {
    return apiErrorRate;
  }

  public int wsReconnects() {
    return wsReconnects;
  }

  public int openingsToday() {
    return openingsToday;
  }

  public BigDecimal currentEquity() {
    return currentEquity;
  }

  public Set<RiskFlag> flags() {
    return flags;
  }

  public Instant lastReset() {
    return lastReset;
  }

  public BigDecimal varExposure() {
    return varExposure;
  }

  public BigDecimal varLimit() {
    return varLimit;
  }

  public BigDecimal varRatio() {
    return varRatio;
  }

  public boolean marketDataStale() {
    return marketDataStale;
  }
}
