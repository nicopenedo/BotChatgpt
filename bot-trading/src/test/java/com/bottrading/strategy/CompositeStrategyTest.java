package com.bottrading.strategy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CompositeStrategyTest extends SignalTestSupport {

  @Test
  void filterReturningFlatBlocksDecision() {
    CompositeStrategy strategy = new CompositeStrategy();
    strategy.addFilter(klines -> SignalResult.flat("Filter block"));
    SignalResult result = strategy.evaluate(series(1, 1.1, 1.2, 1.3));
    Assertions.assertEquals(SignalSide.FLAT, result.side());
    Assertions.assertTrue(result.note().contains("Filter block"));
  }

  @Test
  void weightedSignalsReachThreshold() {
    CompositeStrategy strategy = new CompositeStrategy().thresholds(1.0, 1.0);
    strategy.addSignal(klines -> SignalResult.buy(1.0, "alpha"), 0.6);
    strategy.addSignal(klines -> SignalResult.buy(1.0, "beta"), 0.5);
    SignalResult result = strategy.evaluate(series(1, 1.1, 1.2, 1.3));
    Assertions.assertEquals(SignalSide.BUY, result.side());
  }
}
