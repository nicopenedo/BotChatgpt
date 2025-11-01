package com.bottrading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chaos")
public class ChaosProperties {

  private boolean enabled = false;
  private int wsDropRatePct = 0;
  private int apiBurst429Seconds = 0;
  private double latencyMultiplier = 1.0;
  private GapPattern candlesGapPattern = GapPattern.NONE;
  private long clockDriftMs = 0;
  private final Safety safety = new Safety();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getWsDropRatePct() {
    return wsDropRatePct;
  }

  public void setWsDropRatePct(int wsDropRatePct) {
    this.wsDropRatePct = wsDropRatePct;
  }

  public int getApiBurst429Seconds() {
    return apiBurst429Seconds;
  }

  public void setApiBurst429Seconds(int apiBurst429Seconds) {
    this.apiBurst429Seconds = apiBurst429Seconds;
  }

  public double getLatencyMultiplier() {
    return latencyMultiplier;
  }

  public void setLatencyMultiplier(double latencyMultiplier) {
    this.latencyMultiplier = latencyMultiplier;
  }

  public GapPattern getCandlesGapPattern() {
    return candlesGapPattern;
  }

  public void setCandlesGapPattern(GapPattern candlesGapPattern) {
    this.candlesGapPattern = candlesGapPattern;
  }

  public long getClockDriftMs() {
    return clockDriftMs;
  }

  public void setClockDriftMs(long clockDriftMs) {
    this.clockDriftMs = clockDriftMs;
  }

  public Safety getSafety() {
    return safety;
  }

  public enum GapPattern {
    NONE,
    SKIP_EVERY_10,
    RANDOM_1PCT
  }

  public static class Safety {
    private int maxDurationSec = 600;

    public int getMaxDurationSec() {
      return maxDurationSec;
    }

    public void setMaxDurationSec(int maxDurationSec) {
      this.maxDurationSec = maxDurationSec;
    }
  }
}
