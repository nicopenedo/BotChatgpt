package com.bottrading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "var")
public class VarProperties {

  private boolean enabled = false;
  private double quantile = 0.99;
  private double cvarTargetPctPerTrade = 0.25;
  private double cvarTargetPctPerDay = 1.5;
  private int lookbackTrades = 250;
  private int minTradesForSymbolPreset = 80;
  private boolean fallbackToRegimePool = true;
  private int mcIterations = 20000;
  private boolean heavyTails = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public double getQuantile() {
    return quantile;
  }

  public void setQuantile(double quantile) {
    this.quantile = quantile;
  }

  public double getCvarTargetPctPerTrade() {
    return cvarTargetPctPerTrade;
  }

  public void setCvarTargetPctPerTrade(double cvarTargetPctPerTrade) {
    this.cvarTargetPctPerTrade = cvarTargetPctPerTrade;
  }

  public double getCvarTargetPctPerDay() {
    return cvarTargetPctPerDay;
  }

  public void setCvarTargetPctPerDay(double cvarTargetPctPerDay) {
    this.cvarTargetPctPerDay = cvarTargetPctPerDay;
  }

  public int getLookbackTrades() {
    return lookbackTrades;
  }

  public void setLookbackTrades(int lookbackTrades) {
    this.lookbackTrades = lookbackTrades;
  }

  public int getMinTradesForSymbolPreset() {
    return minTradesForSymbolPreset;
  }

  public void setMinTradesForSymbolPreset(int minTradesForSymbolPreset) {
    this.minTradesForSymbolPreset = minTradesForSymbolPreset;
  }

  public boolean isFallbackToRegimePool() {
    return fallbackToRegimePool;
  }

  public void setFallbackToRegimePool(boolean fallbackToRegimePool) {
    this.fallbackToRegimePool = fallbackToRegimePool;
  }

  public int getMcIterations() {
    return mcIterations;
  }

  public void setMcIterations(int mcIterations) {
    this.mcIterations = mcIterations;
  }

  public boolean isHeavyTails() {
    return heavyTails;
  }

  public void setHeavyTails(boolean heavyTails) {
    this.heavyTails = heavyTails;
  }
}
