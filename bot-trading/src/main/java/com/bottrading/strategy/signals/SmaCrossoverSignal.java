package com.bottrading.strategy.signals;

import com.bottrading.strategy.Series;
import com.bottrading.strategy.Signal;
import com.bottrading.strategy.SignalResult;
import java.util.List;

public class SmaCrossoverSignal implements Signal {

  private final int fastPeriod;
  private final int slowPeriod;
  private final double confidence;

  public SmaCrossoverSignal(int fastPeriod, int slowPeriod, double confidence) {
    if (fastPeriod <= 0 || slowPeriod <= 0 || fastPeriod >= slowPeriod) {
      throw new IllegalArgumentException("Invalid SMA crossover periods");
    }
    this.fastPeriod = fastPeriod;
    this.slowPeriod = slowPeriod;
    this.confidence = confidence;
  }

  @Override
  public SignalResult evaluate(List<String[]> klines) {
    if (klines == null || klines.size() < slowPeriod + 1) {
      return SignalResult.flat("SMA crossover warmup");
    }
    double[] closes = Series.closes(klines);
    double[] fast = Series.sma(closes, fastPeriod);
    double[] slow = Series.sma(closes, slowPeriod);
    int last = closes.length - 1;
    if (Double.isNaN(fast[last]) || Double.isNaN(slow[last])) {
      return SignalResult.flat("SMA crossover warmup");
    }
    double prevFast = fast[last - 1];
    double prevSlow = slow[last - 1];
    String note =
        "fast=%s slow=%s"
            .formatted(format(fast[last]), format(slow[last]));
    if (prevFast <= prevSlow && fast[last] > slow[last]) {
      return SignalResult.buy(confidence, "Bullish SMA cross " + note);
    }
    if (prevFast >= prevSlow && fast[last] < slow[last]) {
      return SignalResult.sell(confidence, "Bearish SMA cross " + note);
    }
    return SignalResult.flat("SMA neutral " + note);
  }

  private String format(double value) {
    return String.format("%.4f", value);
  }
}
