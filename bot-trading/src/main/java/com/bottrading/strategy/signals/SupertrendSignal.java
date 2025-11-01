package com.bottrading.strategy.signals;

import com.bottrading.strategy.Series;
import com.bottrading.strategy.Signal;
import com.bottrading.strategy.SignalResult;
import java.util.List;

public class SupertrendSignal implements Signal {

  private final int atrPeriod;
  private final double multiplier;
  private final double confidence;

  public SupertrendSignal(int atrPeriod, double multiplier, double confidence) {
    if (atrPeriod <= 0) {
      throw new IllegalArgumentException("Supertrend ATR period must be positive");
    }
    this.atrPeriod = atrPeriod;
    this.multiplier = multiplier;
    this.confidence = confidence;
  }

  @Override
  public SignalResult evaluate(List<String[]> klines) {
    if (klines == null || klines.size() < atrPeriod + 2) {
      return SignalResult.flat("Supertrend warmup");
    }
    double[] highs = Series.highs(klines);
    double[] lows = Series.lows(klines);
    double[] closes = Series.closes(klines);
    double[] atr = Series.atr(highs, lows, closes, atrPeriod);
    int len = closes.length;
    double[] upperBand = new double[len];
    double[] lowerBand = new double[len];
    boolean[] supertrend = new boolean[len];

    for (int i = 0; i < len; i++) {
      double hl2 = (highs[i] + lows[i]) / 2.0;
      double basicUpper = hl2 + multiplier * atr[i];
      double basicLower = hl2 - multiplier * atr[i];
      if (i == 0) {
        upperBand[i] = basicUpper;
        lowerBand[i] = basicLower;
        supertrend[i] = true;
        continue;
      }
      upperBand[i] =
          (basicUpper < upperBand[i - 1] || closes[i - 1] > upperBand[i - 1])
              ? basicUpper
              : upperBand[i - 1];
      lowerBand[i] =
          (basicLower > lowerBand[i - 1] || closes[i - 1] < lowerBand[i - 1])
              ? basicLower
              : lowerBand[i - 1];
      if (supertrend[i - 1]) {
        supertrend[i] = closes[i] > lowerBand[i];
      } else {
        supertrend[i] = !(closes[i] > upperBand[i]);
      }
    }
    int last = len - 1;
    if (last < 1) {
      return SignalResult.flat("Supertrend warmup");
    }
    boolean currentUp = supertrend[last];
    boolean previousUp = supertrend[last - 1];
    String note =
        "supertrend=%s upper=%s lower=%s"
            .formatted(currentUp ? "UP" : "DOWN", format(upperBand[last]), format(lowerBand[last]));
    if (currentUp && !previousUp) {
      return SignalResult.buy(confidence, "Supertrend flip bullish " + note);
    }
    if (!currentUp && previousUp) {
      return SignalResult.sell(confidence, "Supertrend flip bearish " + note);
    }
    return SignalResult.flat("Supertrend steady " + note);
  }

  private String format(double value) {
    return String.format("%.4f", value);
  }
}
