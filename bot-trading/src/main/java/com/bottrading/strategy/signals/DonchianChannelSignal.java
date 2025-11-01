package com.bottrading.strategy.signals;

import com.bottrading.strategy.Series;
import com.bottrading.strategy.Signal;
import com.bottrading.strategy.SignalResult;
import java.util.List;

public class DonchianChannelSignal implements Signal {

  private final int period;
  private final double confidence;

  public DonchianChannelSignal(int period, double confidence) {
    if (period <= 1) {
      throw new IllegalArgumentException("Donchian period must be > 1");
    }
    this.period = period;
    this.confidence = confidence;
  }

  @Override
  public SignalResult evaluate(List<String[]> klines) {
    if (klines == null || klines.size() < period + 1) {
      return SignalResult.flat("Donchian warmup");
    }
    double[] highs = Series.highs(klines);
    double[] lows = Series.lows(klines);
    double[] closes = Series.closes(klines);
    int last = closes.length - 1;
    double highest = Double.NEGATIVE_INFINITY;
    double lowest = Double.POSITIVE_INFINITY;
    for (int i = last - period; i < last; i++) {
      highest = Math.max(highest, highs[i]);
      lowest = Math.min(lowest, lows[i]);
    }
    double close = closes[last];
    String note =
        "close=%s breakoutHigh=%s breakoutLow=%s"
            .formatted(format(close), format(highest), format(lowest));
    if (close > highest) {
      return SignalResult.buy(confidence, "Donchian breakout high " + note);
    }
    if (close < lowest) {
      return SignalResult.sell(confidence, "Donchian breakout low " + note);
    }
    return SignalResult.flat("Donchian inside range " + note);
  }

  private String format(double value) {
    return String.format("%.4f", value);
  }
}
