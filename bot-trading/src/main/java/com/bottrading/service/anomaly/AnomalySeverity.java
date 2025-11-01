package com.bottrading.service.anomaly;

public enum AnomalySeverity {
  NONE(0),
  WARN(1),
  MEDIUM(2),
  HIGH(3),
  SEVERE(4);

  private final int gaugeLevel;

  AnomalySeverity(int gaugeLevel) {
    this.gaugeLevel = gaugeLevel;
  }

  public int gaugeLevel() {
    return gaugeLevel;
  }
}
