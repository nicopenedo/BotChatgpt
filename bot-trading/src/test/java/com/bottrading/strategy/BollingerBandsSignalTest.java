package com.bottrading.strategy;

import com.bottrading.strategy.signals.BollingerBandsSignal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BollingerBandsSignalTest extends SignalTestSupport {

  @Test
  void lowerBandTouchTriggersBuy() {
    BollingerBandsSignal signal = new BollingerBandsSignal(5, 2.0, 0.5);
    SignalResult result = signal.evaluate(series(10, 10.1, 10.2, 10.0, 9.8, 9.5, 9.3));
    Assertions.assertEquals(SignalSide.BUY, result.side());
  }

  @Test
  void bollingerWarmupFlat() {
    BollingerBandsSignal signal = new BollingerBandsSignal(20, 2.0, 0.5);
    SignalResult result = signal.evaluate(series(10, 10.1, 10.2));
    Assertions.assertEquals(SignalSide.FLAT, result.side());
  }
}
