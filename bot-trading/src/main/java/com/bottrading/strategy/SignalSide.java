package com.bottrading.strategy;

public enum SignalSide {
  BUY,
  SELL,
  FLAT;

  public boolean isActionable() {
    return this == BUY || this == SELL;
  }
}
