package com.bottrading.strategy;

import java.util.List;

public interface Signal {
  SignalResult evaluate(List<String[]> klines);

  default String name() {
    return getClass().getSimpleName();
  }

  default void applyContext(StrategyContext context) {
    // no-op by default
  }
}
