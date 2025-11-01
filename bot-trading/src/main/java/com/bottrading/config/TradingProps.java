package com.bottrading.config;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading")
public class TradingProps {

  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

  public enum Mode {
    WEBSOCKET,
    POLLING
  }

  private String symbol = "BTCUSDT";
  private String interval = "1m";
  private boolean liveEnabled = false;
  private BigDecimal maxDailyLossPct = BigDecimal.valueOf(2.0);
  private BigDecimal maxDrawdownPct = BigDecimal.valueOf(3.0);
  private BigDecimal riskPerTradePct = BigDecimal.valueOf(0.25);
  private int cooldownMinutes = 60;
  private int maxOrdersPerMinute = 5;
  private BigDecimal minVolume24h = BigDecimal.valueOf(1000);
  private boolean dryRun = true;
  private String tradingHours = "00:00-23:59";
  private Mode mode = Mode.WEBSOCKET;
  private int jitterSeconds = 2;

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public String getInterval() {
    return interval;
  }

  public void setInterval(String interval) {
    this.interval = interval;
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

  public String getTradingHours() {
    return tradingHours;
  }

  public void setTradingHours(String tradingHours) {
    this.tradingHours = tradingHours;
  }

  public Mode getMode() {
    return mode;
  }

  public void setMode(Mode mode) {
    this.mode = mode;
  }

  public int getJitterSeconds() {
    return jitterSeconds;
  }

  public void setJitterSeconds(int jitterSeconds) {
    this.jitterSeconds = jitterSeconds;
  }

  public LocalTime getTradingWindowStart() {
    return parseTradingWindow()[0];
  }

  public LocalTime getTradingWindowEnd() {
    return parseTradingWindow()[1];
  }

  private LocalTime[] parseTradingWindow() {
    if (tradingHours == null || !tradingHours.contains("-")) {
      return new LocalTime[] {LocalTime.MIN, LocalTime.MAX};
    }
    String[] parts = tradingHours.split("-");
    try {
      LocalTime start = LocalTime.parse(parts[0].trim(), TIME_FORMAT.withLocale(Locale.US));
      LocalTime end = LocalTime.parse(parts[1].trim(), TIME_FORMAT.withLocale(Locale.US));
      return new LocalTime[] {start, end};
    } catch (DateTimeParseException ex) {
      return new LocalTime[] {LocalTime.MIN, LocalTime.MAX};
    }
  }
}
