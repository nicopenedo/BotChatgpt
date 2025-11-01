package com.bottrading.strategy.signals;

import com.bottrading.strategy.Series;
import com.bottrading.strategy.Signal;
import com.bottrading.strategy.SignalResult;
import java.util.List;

public class AtrVolatilityFilter implements Signal {

  private final int period;
  private final double minAtr;

  public AtrVolatilityFilter(int period, double minAtr) {
    if (period <= 0) {
      throw new IllegalArgumentException("ATR period must be positive");
    }
    this.period = period;
    this.minAtr = minAtr;
  }

  @Override
  public SignalResult evaluate(List<String[]> klines) {
    if (klines == null || klines.size() < period + 2) {
      return SignalResult.flat("ATR filter warmup");
    }
    double[] highs = Series.highs(klines);
    double[] lows = Series.lows(klines);
    double[] closes = Series.closes(klines);
    double[] atr = Series.atr(highs, lows, closes, period);
    int last = closes.length - 1;
    if (Double.isNaN(atr[last]) || atr[last] == 0) {
      return SignalResult.flat("ATR filter warmup");
    }
    if (atr[last] < minAtr) {
      return SignalResult.flat("ATR %.4f below %.4f".formatted(atr[last], minAtr));
    }
    return SignalResult.buy(1.0, "ATR %.4f >= %.4f".formatted(atr[last], minAtr));
  }
}
