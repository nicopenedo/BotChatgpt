package com.bottrading.service.risk;

public enum RiskMode {
  LIVE,
  SHADOW,
  PAUSED;

  public boolean isLive() {
    return this == LIVE;
  }

  public boolean isShadow() {
    return this == SHADOW;
  }

  public boolean isPaused() {
    return this == PAUSED;
  }

  public static RiskMode fromTradingState(TradingState.Mode mode) {
    return switch (mode) {
      case LIVE -> LIVE;
      case SHADOW -> SHADOW;
      case PAUSED -> PAUSED;
    };
  }

  public TradingState.Mode toTradingMode() {
    return switch (this) {
      case LIVE -> TradingState.Mode.LIVE;
      case SHADOW -> TradingState.Mode.SHADOW;
      case PAUSED -> TradingState.Mode.PAUSED;
    };
  }
}
