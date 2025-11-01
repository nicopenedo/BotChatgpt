package com.bottrading.strategy;

import java.util.Objects;

public record SignalResult(SignalSide side, double confidence, String note) {

  public SignalResult {
    Objects.requireNonNull(side, "side");
    if (confidence < 0 || confidence > 1) {
      throw new IllegalArgumentException("confidence must be within [0,1]");
    }
    note = note == null ? "" : note;
  }

  public static SignalResult flat(String note) {
    return new SignalResult(SignalSide.FLAT, 0.0, note);
  }

  public static SignalResult buy(double confidence, String note) {
    return new SignalResult(SignalSide.BUY, confidence, note);
  }

  public static SignalResult sell(double confidence, String note) {
    return new SignalResult(SignalSide.SELL, confidence, note);
  }
}
