package com.bottrading.strategy;

import com.bottrading.strategy.signals.SupertrendSignal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SupertrendSignalTest extends SignalTestSupport {

  @Test
  void supertrendFlipBullish() {
    SupertrendSignal signal = new SupertrendSignal(3, 2.0, 0.6);
    SignalResult result =
        signal.evaluate(series(12, 11.5, 11.0, 10.8, 10.6, 10.7, 11.0, 11.4, 11.9, 12.4));
    Assertions.assertEquals(SignalSide.BUY, result.side());
  }

  @Test
  void supertrendWarmupFlat() {
    SupertrendSignal signal = new SupertrendSignal(10, 3.0, 0.7);
    SignalResult result = signal.evaluate(series(10, 10.1, 10.2));
    Assertions.assertEquals(SignalSide.FLAT, result.side());
  }
}
