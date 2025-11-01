package com.bottrading.service.anomaly;

public enum AnomalyAction {
  NONE,
  ALERT,
  SIZE_DOWN_25,
  SIZE_DOWN_50,
  SWITCH_TO_MARKET,
  SWITCH_TO_TWAP,
  PAUSE;

  public double sizingMultiplier() {
    return switch (this) {
      case SIZE_DOWN_25 -> 0.75;
      case SIZE_DOWN_50 -> 0.5;
      case PAUSE -> 0.0;
      default -> 1.0;
    };
  }
}
