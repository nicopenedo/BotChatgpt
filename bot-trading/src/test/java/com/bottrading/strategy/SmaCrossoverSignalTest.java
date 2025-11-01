package com.bottrading.strategy;

import com.bottrading.strategy.signals.SmaCrossoverSignal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SmaCrossoverSignalTest extends SignalTestSupport {

  @Test
  void bullishCrossProducesBuy() {
    SmaCrossoverSignal signal = new SmaCrossoverSignal(3, 5, 1.0);
    SignalResult result = signal.evaluate(series(10, 10.5, 11, 11.5, 12, 12.5, 13));
    Assertions.assertEquals(SignalSide.BUY, result.side());
  }

  @Test
  void insufficientDataReturnsFlat() {
    SmaCrossoverSignal signal = new SmaCrossoverSignal(3, 5, 1.0);
    SignalResult result = signal.evaluate(series(10, 10.5, 11));
    Assertions.assertEquals(SignalSide.FLAT, result.side());
  }
}
