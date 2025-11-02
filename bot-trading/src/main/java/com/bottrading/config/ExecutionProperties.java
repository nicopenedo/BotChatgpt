package com.bottrading.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "exec")
public class ExecutionProperties {

  private DefaultOrder defaultOrder = new DefaultOrder();
  private Limit limit = new Limit();
  private Twap twap = new Twap();
  private Pov pov = new Pov();
  private Metrics metrics = new Metrics();

  public DefaultOrder getDefaultOrder() {
    return defaultOrder;
  }

  public void setDefaultOrder(DefaultOrder defaultOrder) {
    this.defaultOrder = defaultOrder;
  }

  public Limit getLimit() {
    return limit;
  }

  public void setLimit(Limit limit) {
    this.limit = limit;
  }

  public Twap getTwap() {
    return twap;
  }

  public void setTwap(Twap twap) {
    this.twap = twap;
  }

  public Pov getPov() {
    return pov;
  }

  public void setPov(Pov pov) {
    this.pov = pov;
  }

  public Metrics getMetrics() {
    return metrics;
  }

  public void setMetrics(Metrics metrics) {
    this.metrics = metrics;
  }

  public static class DefaultOrder {
    private String type = "LIMIT";

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }
  }

  public static class Limit {
    private long ttlMs = 4000L;
    private double bufferBps = 2.0;
    private int maxRetries = 1;
    private double spreadThresholdBps = 5.0;

    public long getTtlMs() {
      return ttlMs;
    }

    public void setTtlMs(long ttlMs) {
      this.ttlMs = ttlMs;
    }

    public double getBufferBps() {
      return bufferBps;
    }

    public void setBufferBps(double bufferBps) {
      this.bufferBps = bufferBps;
    }

    public int getMaxRetries() {
      return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
    }

    public double getSpreadThresholdBps() {
      return spreadThresholdBps;
    }

    public void setSpreadThresholdBps(double spreadThresholdBps) {
      this.spreadThresholdBps = spreadThresholdBps;
    }
  }

  public static class Twap {
    private int slices = 5;
    private long windowSec = 20;

    public int getSlices() {
      return slices;
    }

    public void setSlices(int slices) {
      this.slices = slices;
    }

    public long getWindowSec() {
      return windowSec;
    }

    public void setWindowSec(long windowSec) {
      this.windowSec = windowSec;
    }

    public Duration windowDuration() {
      return Duration.ofSeconds(windowSec);
    }
  }

  public static class Pov {
    private double targetPct = 0.10;
    private long reassessIntervalSec = 60;

    public double getTargetPct() {
      return targetPct;
    }

    public void setTargetPct(double targetPct) {
      this.targetPct = targetPct;
    }

    public long getReassessIntervalSec() {
      return reassessIntervalSec;
    }

    public void setReassessIntervalSec(long reassessIntervalSec) {
      this.reassessIntervalSec = reassessIntervalSec;
    }

    public Duration reassessInterval() {
      return Duration.ofSeconds(reassessIntervalSec);
    }
  }

  public static class Metrics {
    private long cleanupMs = Duration.ofMinutes(10).toMillis();
    private long ttlMs = Duration.ofMinutes(30).toMillis();

    public long getCleanupMs() {
      return cleanupMs;
    }

    public void setCleanupMs(long cleanupMs) {
      this.cleanupMs = cleanupMs;
    }

    public long getTtlMs() {
      return ttlMs;
    }

    public void setTtlMs(long ttlMs) {
      this.ttlMs = ttlMs;
    }
  }
}
