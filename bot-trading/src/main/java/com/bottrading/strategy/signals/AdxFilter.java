package com.bottrading.strategy.signals;

import com.bottrading.strategy.Series;
import com.bottrading.strategy.Signal;
import com.bottrading.strategy.SignalResult;
import java.util.List;

public class AdxFilter implements Signal {

  private final int period;
  private final double minAdx;

  public AdxFilter(int period, double minAdx) {
    if (period <= 0) {
      throw new IllegalArgumentException("ADX period must be positive");
    }
    this.period = period;
    this.minAdx = minAdx;
  }

  @Override
  public SignalResult evaluate(List<String[]> klines) {
    if (klines == null || klines.size() < period + 2) {
      return SignalResult.flat("ADX warmup");
    }
    double[] highs = Series.highs(klines);
    double[] lows = Series.lows(klines);
    double[] closes = Series.closes(klines);
    double[] atr = Series.atr(highs, lows, closes, period);
    double[] plusDm = Series.plusDirectionalMovement(highs, lows);
    double[] minusDm = Series.minusDirectionalMovement(highs, lows);
    double[] plusSmoothed = Series.ema(plusDm, period);
    double[] minusSmoothed = Series.ema(minusDm, period);
    int len = closes.length;
    double[] plusDi = new double[len];
    double[] minusDi = new double[len];
    for (int i = 0; i < len; i++) {
      double atrValue = atr[i];
      if (atrValue <= 0) {
        plusDi[i] = 0;
        minusDi[i] = 0;
      } else {
        plusDi[i] = 100 * (plusSmoothed[i] / atrValue);
        minusDi[i] = 100 * (minusSmoothed[i] / atrValue);
      }
    }
    double[] dx = Series.dx(plusDi, minusDi);
    double[] adx = Series.ema(dx, period);
    int last = len - 1;
    if (Double.isNaN(adx[last])) {
      return SignalResult.flat("ADX warmup");
    }
    if (adx[last] < minAdx) {
      return SignalResult.flat("ADX %.2f below %.2f".formatted(adx[last], minAdx));
    }
    return SignalResult.buy(1.0, "ADX %.2f >= %.2f".formatted(adx[last], minAdx));
  }
}
