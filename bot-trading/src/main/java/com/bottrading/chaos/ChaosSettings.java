package com.bottrading.chaos;

import com.bottrading.config.ChaosProperties.GapPattern;

public record ChaosSettings(
    boolean enabled,
    int wsDropRatePct,
    int apiBurst429Seconds,
    double latencyMultiplier,
    GapPattern gapPattern,
    long clockDriftMs,
    int maxDurationSec) {

  public static Builder builder() {
    return new Builder();
  }

  public ChaosSettings withEnabled(boolean value) {
    return new ChaosSettings(
        value,
        wsDropRatePct,
        apiBurst429Seconds,
        latencyMultiplier,
        gapPattern,
        clockDriftMs,
        maxDurationSec);
  }

  public static class Builder {
    private boolean enabled;
    private int wsDropRatePct;
    private int apiBurst429Seconds;
    private double latencyMultiplier = 1.0;
    private GapPattern gapPattern = GapPattern.NONE;
    private long clockDriftMs;
    private int maxDurationSec = 600;

    public Builder enabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    public Builder wsDropRatePct(int wsDropRatePct) {
      this.wsDropRatePct = Math.max(0, wsDropRatePct);
      return this;
    }

    public Builder apiBurst429Seconds(int apiBurst429Seconds) {
      this.apiBurst429Seconds = Math.max(0, apiBurst429Seconds);
      return this;
    }

    public Builder latencyMultiplier(double latencyMultiplier) {
      this.latencyMultiplier = latencyMultiplier <= 0 ? 1.0 : latencyMultiplier;
      return this;
    }

    public Builder gapPattern(GapPattern gapPattern) {
      if (gapPattern != null) {
        this.gapPattern = gapPattern;
      }
      return this;
    }

    public Builder clockDriftMs(long clockDriftMs) {
      this.clockDriftMs = clockDriftMs;
      return this;
    }

    public Builder maxDurationSec(int maxDurationSec) {
      this.maxDurationSec = Math.max(1, maxDurationSec);
      return this;
    }

    public ChaosSettings build() {
      return new ChaosSettings(
          enabled, wsDropRatePct, apiBurst429Seconds, latencyMultiplier, gapPattern, clockDriftMs, maxDurationSec);
    }
  }
}
