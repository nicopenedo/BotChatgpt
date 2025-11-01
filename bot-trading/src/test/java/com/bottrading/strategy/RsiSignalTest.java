package com.bottrading.strategy;

import com.bottrading.strategy.signals.RsiSignal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RsiSignalTest extends SignalTestSupport {

  @Test
  void rsiOversoldInUptrendBuys() {
    RsiSignal signal = new RsiSignal(3, 40, 60, 3, 0.7);
    SignalResult result = signal.evaluate(series(10, 10.4, 10.8, 11.2, 11.4, 11.3, 11.1, 11.0));
    Assertions.assertEquals(SignalSide.BUY, result.side());
  }

  @Test
  void rsiWarmupFlat() {
    RsiSignal signal = new RsiSignal(14, 30, 70, 50, 0.6);
    SignalResult result = signal.evaluate(series(10, 10.5, 11));
    Assertions.assertEquals(SignalSide.FLAT, result.side());
  }
}
