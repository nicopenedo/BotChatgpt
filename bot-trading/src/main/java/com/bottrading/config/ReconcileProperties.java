package com.bottrading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reconcile")
public class ReconcileProperties {

  private boolean onStartup = true;
  private int scanMinutes = 1440;

  public boolean isOnStartup() {
    return onStartup;
  }

  public void setOnStartup(boolean onStartup) {
    this.onStartup = onStartup;
  }

  public int getScanMinutes() {
    return scanMinutes;
  }

  public void setScanMinutes(int scanMinutes) {
    this.scanMinutes = scanMinutes;
  }
}
