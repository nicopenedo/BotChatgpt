package com.bottrading.strategy;

import java.util.List;
import java.util.Objects;

public record SignalResult(SignalSide side, double confidence, String note, List<String> voters) {

  public SignalResult {
    Objects.requireNonNull(side, "side");
    if (confidence < 0 || confidence > 1) {
      throw new IllegalArgumentException("confidence must be within [0,1]");
    }
    note = note == null ? "" : note;
    voters = voters == null ? List.of() : List.copyOf(voters);
  }

  public static SignalResult flat(String note) {
    return flat(note, List.of());
  }

  public static SignalResult flat(String note, List<String> voters) {
    return new SignalResult(SignalSide.FLAT, 0.0, note, voters);
  }

  public static SignalResult buy(double confidence, String note) {
    return buy(confidence, note, List.of());
  }

  public static SignalResult buy(double confidence, String note, List<String> voters) {
    return new SignalResult(SignalSide.BUY, confidence, note, voters);
  }

  public static SignalResult sell(double confidence, String note) {
    return sell(confidence, note, List.of());
  }

  public static SignalResult sell(double confidence, String note, List<String> voters) {
    return new SignalResult(SignalSide.SELL, confidence, note, voters);
  }
}
