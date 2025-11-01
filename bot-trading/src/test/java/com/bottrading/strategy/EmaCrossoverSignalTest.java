package com.bottrading.strategy;

import com.bottrading.strategy.signals.EmaCrossoverSignal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EmaCrossoverSignalTest extends SignalTestSupport {

  @Test
  void bearishCrossProducesSell() {
    EmaCrossoverSignal signal = new EmaCrossoverSignal(3, 6, 0.9);
    SignalResult result = signal.evaluate(series(15, 14.5, 14, 13.5, 13, 12.5, 12));
    Assertions.assertEquals(SignalSide.SELL, result.side());
  }

  @Test
  void emaWarmupFlat() {
    EmaCrossoverSignal signal = new EmaCrossoverSignal(3, 6, 0.9);
    SignalResult result = signal.evaluate(series(15, 14, 13));
    Assertions.assertEquals(SignalSide.FLAT, result.side());
  }
}
