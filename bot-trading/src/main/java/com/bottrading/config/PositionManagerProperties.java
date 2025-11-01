package com.bottrading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "positions")
public class PositionManagerProperties {

  /** Lock timeout in milliseconds for position critical sections. */
  private long lockTimeoutMs = 3000;

  public long getLockTimeoutMs() {
    return lockTimeoutMs;
  }

  public void setLockTimeoutMs(long lockTimeoutMs) {
    this.lockTimeoutMs = lockTimeoutMs;
  }
}
