package com.bottrading.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading")
public class TradingProperties {

  private String symbol = "BTCUSDT";
  private boolean liveEnabled = false;
  private BigDecimal maxDailyLossPct = BigDecimal.valueOf(2.0);
  private BigDecimal maxDrawdownPct = BigDecimal.valueOf(3.0);
  private BigDecimal riskPerTradePct = BigDecimal.valueOf(0.25);
  private int cooldownMinutes = 60;
  private int maxOrdersPerMinute = 5;
  private BigDecimal minVolume24h = BigDecimal.valueOf(1000);
  private boolean dryRun = true;

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public boolean isLiveEnabled() {
    return liveEnabled;
  }

  public void setLiveEnabled(boolean liveEnabled) {
    this.liveEnabled = liveEnabled;
  }

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

  public BigDecimal getRiskPerTradePct() {
    return riskPerTradePct;
  }

  public void setRiskPerTradePct(BigDecimal riskPerTradePct) {
    this.riskPerTradePct = riskPerTradePct;
  }

  public int getCooldownMinutes() {
    return cooldownMinutes;
  }

  public void setCooldownMinutes(int cooldownMinutes) {
    this.cooldownMinutes = cooldownMinutes;
  }

  public int getMaxOrdersPerMinute() {
    return maxOrdersPerMinute;
  }

  public void setMaxOrdersPerMinute(int maxOrdersPerMinute) {
    this.maxOrdersPerMinute = maxOrdersPerMinute;
  }

  public BigDecimal getMinVolume24h() {
    return minVolume24h;
  }

  public void setMinVolume24h(BigDecimal minVolume24h) {
    this.minVolume24h = minVolume24h;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public void setDryRun(boolean dryRun) {
    this.dryRun = dryRun;
  }
}
