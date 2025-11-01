package com.bottrading.strategy;

import com.bottrading.strategy.signals.MacdSignal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MacdSignalTest extends SignalTestSupport {

  @Test
  void macdHistogramTurnBullish() {
    MacdSignal signal = new MacdSignal(3, 6, 3, 0.8);
    SignalResult result = signal.evaluate(series(10, 9.5, 9.2, 9.0, 9.1, 9.4, 9.8, 10.3, 10.9, 11.4));
    Assertions.assertEquals(SignalSide.BUY, result.side());
  }

  @Test
  void macdWarmupFlat() {
    MacdSignal signal = new MacdSignal(12, 26, 9, 0.7);
    SignalResult result = signal.evaluate(series(10, 10.5, 11));
    Assertions.assertEquals(SignalSide.FLAT, result.side());
  }
}
