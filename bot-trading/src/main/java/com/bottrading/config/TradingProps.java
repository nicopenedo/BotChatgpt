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
  private java.util.List<String> symbols = java.util.List.of("BTCUSDT");
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
  private RouterProperties router = new RouterProperties();
  private AllocatorProperties allocator = new AllocatorProperties();
  private DriftProperties drift = new DriftProperties();
  private HealthProperties health = new HealthProperties();
  private TcaProperties tca = new TcaProperties();

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public java.util.List<String> getSymbols() {
    return symbols;
  }

  public void setSymbols(java.util.List<String> symbols) {
    if (symbols == null || symbols.isEmpty()) {
      this.symbols = java.util.List.of(this.symbol);
    } else {
      this.symbols = java.util.List.copyOf(symbols);
    }
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

  public RouterProperties getRouter() {
    return router;
  }

  public void setRouter(RouterProperties router) {
    this.router = router;
  }

  public AllocatorProperties getAllocator() {
    return allocator;
  }

  public void setAllocator(AllocatorProperties allocator) {
    this.allocator = allocator;
  }

  public DriftProperties getDrift() {
    return drift;
  }

  public void setDrift(DriftProperties drift) {
    this.drift = drift;
  }

  public HealthProperties getHealth() {
    return health;
  }

  public void setHealth(HealthProperties health) {
    this.health = health;
  }

  public TcaProperties getTca() {
    return tca;
  }

  public void setTca(TcaProperties tca) {
    this.tca = tca;
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

  public static class RouterProperties {
    private boolean enabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  public static class AllocatorProperties {
    private boolean enabled = true;
    private int maxSimultaneous = 1;
    private BigDecimal perSymbolMaxRiskPct = BigDecimal.valueOf(0.5);
    private BigDecimal portfolioMaxTotalRiskPct = BigDecimal.ONE;
    private int corrLookbackDays = 30;
    private double corrMaxPairwise = 0.85;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getMaxSimultaneous() {
      return maxSimultaneous;
    }

    public void setMaxSimultaneous(int maxSimultaneous) {
      this.maxSimultaneous = maxSimultaneous;
    }

    public BigDecimal getPerSymbolMaxRiskPct() {
      return perSymbolMaxRiskPct;
    }

    public void setPerSymbolMaxRiskPct(BigDecimal perSymbolMaxRiskPct) {
      this.perSymbolMaxRiskPct = perSymbolMaxRiskPct;
    }

    public BigDecimal getPortfolioMaxTotalRiskPct() {
      return portfolioMaxTotalRiskPct;
    }

    public void setPortfolioMaxTotalRiskPct(BigDecimal portfolioMaxTotalRiskPct) {
      this.portfolioMaxTotalRiskPct = portfolioMaxTotalRiskPct;
    }

    public int getCorrLookbackDays() {
      return corrLookbackDays;
    }

    public void setCorrLookbackDays(int corrLookbackDays) {
      this.corrLookbackDays = corrLookbackDays;
    }

    public double getCorrMaxPairwise() {
      return corrMaxPairwise;
    }

    public void setCorrMaxPairwise(double corrMaxPairwise) {
      this.corrMaxPairwise = corrMaxPairwise;
    }
  }

  public static class DriftProperties {
    private boolean enabled = true;
    private int windowTrades = 50;
    private double thresholdPfDrop = 0.35;
    private double thresholdMaxddPct = 8.0;
    private boolean actionsAutoDowngrade = true;
    private double expectedProfitFactor = 1.5;
    private double expectedWinRate = 0.55;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getWindowTrades() {
      return windowTrades;
    }

    public void setWindowTrades(int windowTrades) {
      this.windowTrades = windowTrades;
    }

    public double getThresholdPfDrop() {
      return thresholdPfDrop;
    }

    public void setThresholdPfDrop(double thresholdPfDrop) {
      this.thresholdPfDrop = thresholdPfDrop;
    }

    public double getThresholdMaxddPct() {
      return thresholdMaxddPct;
    }

    public void setThresholdMaxddPct(double thresholdMaxddPct) {
      this.thresholdMaxddPct = thresholdMaxddPct;
    }

    public boolean isActionsAutoDowngrade() {
      return actionsAutoDowngrade;
    }

    public void setActionsAutoDowngrade(boolean actionsAutoDowngrade) {
      this.actionsAutoDowngrade = actionsAutoDowngrade;
    }

    public double getExpectedProfitFactor() {
      return expectedProfitFactor;
    }

    public void setExpectedProfitFactor(double expectedProfitFactor) {
      this.expectedProfitFactor = expectedProfitFactor;
    }

    public double getExpectedWinRate() {
      return expectedWinRate;
    }

    public void setExpectedWinRate(double expectedWinRate) {
      this.expectedWinRate = expectedWinRate;
    }
  }

  public static class HealthProperties {
    private boolean enabled = true;
    private int wsMaxReconnectsPerHour = 6;
    private double apiMaxErrorRatePct = 5.0;
    private long apiLatencyThresholdMs = 500;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getWsMaxReconnectsPerHour() {
      return wsMaxReconnectsPerHour;
    }

    public void setWsMaxReconnectsPerHour(int wsMaxReconnectsPerHour) {
      this.wsMaxReconnectsPerHour = wsMaxReconnectsPerHour;
    }

    public double getApiMaxErrorRatePct() {
      return apiMaxErrorRatePct;
    }

    public void setApiMaxErrorRatePct(double apiMaxErrorRatePct) {
      this.apiMaxErrorRatePct = apiMaxErrorRatePct;
    }

    public long getApiLatencyThresholdMs() {
      return apiLatencyThresholdMs;
    }

    public void setApiLatencyThresholdMs(long apiLatencyThresholdMs) {
      this.apiLatencyThresholdMs = apiLatencyThresholdMs;
    }
  }

  public static class TcaProperties {
    private boolean enabled = true;
    private int historySize = 5000;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getHistorySize() {
      return historySize;
    }

    public void setHistorySize(int historySize) {
      this.historySize = historySize;
    }
  }
}
