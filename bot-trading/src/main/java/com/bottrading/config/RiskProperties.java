package com.bottrading.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "risk")
public class RiskProperties {

  private BigDecimal maxDailyLossPct = BigDecimal.ZERO;
  private BigDecimal maxDrawdownPct = BigDecimal.ZERO;
  private BigDecimal maxApiErrorPct = BigDecimal.ZERO;
  private int maxWsReconnectsPerHour = 0;
  private boolean forceCloseOnPause = false;
  private int maxOpeningsPerDay = 0;
  private BigDecimal maxTradeNotional = BigDecimal.ZERO;

  public BigDecimal getMaxDailyLossPct() {
    return maxDailyLossPct;
  }

  public void setMaxDailyLossPct(BigDecimal maxDailyLossPct) {
    this.maxDailyLossPct = maxDailyLossPct;
  }

  public BigDecimal getMaxDrawdownPct() {
    return maxDrawdownPct;
  }

  public void setMaxDrawdownPct(BigDecimal maxDrawdownPct) {
    this.maxDrawdownPct = maxDrawdownPct;
  }

  public BigDecimal getMaxApiErrorPct() {
    return maxApiErrorPct;
  }

  public void setMaxApiErrorPct(BigDecimal maxApiErrorPct) {
    this.maxApiErrorPct = maxApiErrorPct;
  }

  public int getMaxWsReconnectsPerHour() {
    return maxWsReconnectsPerHour;
  }

  public void setMaxWsReconnectsPerHour(int maxWsReconnectsPerHour) {
    this.maxWsReconnectsPerHour = maxWsReconnectsPerHour;
  }

  public boolean isForceCloseOnPause() {
    return forceCloseOnPause;
  }

  public void setForceCloseOnPause(boolean forceCloseOnPause) {
    this.forceCloseOnPause = forceCloseOnPause;
  }

  public int getMaxOpeningsPerDay() {
    return maxOpeningsPerDay;
  }

  public void setMaxOpeningsPerDay(int maxOpeningsPerDay) {
    this.maxOpeningsPerDay = maxOpeningsPerDay;
  }

  public BigDecimal getMaxTradeNotional() {
    return maxTradeNotional;
  }

  public void setMaxTradeNotional(BigDecimal maxTradeNotional) {
    this.maxTradeNotional = maxTradeNotional;
  }
}
