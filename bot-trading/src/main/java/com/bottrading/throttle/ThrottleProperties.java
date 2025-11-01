package com.bottrading.throttle;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "throttle")
public class ThrottleProperties {

  private long window1s = 1200;
  private long window60s = 72000;
  private BackoffProperties on429 = new BackoffProperties(500);
  private BackoffProperties on5xx = new BackoffProperties(300);
  private QueueProperties queue = new QueueProperties();

  public long getWindow1s() {
    return window1s;
  }

  public void setWindow1s(long window1s) {
    this.window1s = window1s;
  }

  public long getWindow60s() {
    return window60s;
  }

  public void setWindow60s(long window60s) {
    this.window60s = window60s;
  }

  public BackoffProperties getOn429() {
    return on429;
  }

  public void setOn429(BackoffProperties on429) {
    this.on429 = on429;
  }

  public BackoffProperties getOn5xx() {
    return on5xx;
  }

  public void setOn5xx(BackoffProperties on5xx) {
    this.on5xx = on5xx;
  }

  public QueueProperties getQueue() {
    return queue;
  }

  public void setQueue(QueueProperties queue) {
    this.queue = queue;
  }

  public static class BackoffProperties {

    private long backoffMs;

    public BackoffProperties() {}

    public BackoffProperties(long backoffMs) {
      this.backoffMs = backoffMs;
    }

    public long getBackoffMs() {
      return backoffMs;
    }

    public void setBackoffMs(long backoffMs) {
      this.backoffMs = backoffMs;
    }
  }

  public static class QueueProperties {

    private int maxDepthPerSymbol = 100;
    private int maxDepthGlobal = 2000;

    public int getMaxDepthPerSymbol() {
      return maxDepthPerSymbol;
    }

    public void setMaxDepthPerSymbol(int maxDepthPerSymbol) {
      this.maxDepthPerSymbol = maxDepthPerSymbol;
    }

    public int getMaxDepthGlobal() {
      return maxDepthGlobal;
    }

    public void setMaxDepthGlobal(int maxDepthGlobal) {
      this.maxDepthGlobal = maxDepthGlobal;
    }
  }
}
