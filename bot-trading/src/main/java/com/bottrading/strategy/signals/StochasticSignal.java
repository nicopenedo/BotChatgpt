package com.bottrading.strategy.signals;

import com.bottrading.strategy.Series;
import com.bottrading.strategy.Signal;
import com.bottrading.strategy.SignalResult;
import java.util.List;

public class StochasticSignal implements Signal {

  private final int kPeriod;
  private final int dPeriod;
  private final double confidence;

  public StochasticSignal(int kPeriod, int dPeriod, double confidence) {
    if (kPeriod <= 0 || dPeriod <= 0) {
      throw new IllegalArgumentException("Stochastic periods must be positive");
    }
    this.kPeriod = kPeriod;
    this.dPeriod = dPeriod;
    this.confidence = confidence;
  }

  @Override
  public SignalResult evaluate(List<String[]> klines) {
    int min = Math.max(kPeriod, dPeriod) + 1;
    if (klines == null || klines.size() < min) {
      return SignalResult.flat("Stochastic warmup");
    }
    double[] closes = Series.closes(klines);
    double[] highs = Series.highs(klines);
    double[] lows = Series.lows(klines);
    double[] k = Series.stochasticK(closes, highs, lows, kPeriod);
    double[] d = Series.smooth(k, dPeriod);
    int last = closes.length - 1;
    if (Double.isNaN(k[last]) || Double.isNaN(d[last]) || Double.isNaN(k[last - 1]) || Double.isNaN(d[last - 1])) {
      return SignalResult.flat("Stochastic warmup");
    }
    String note =
        "%K=%s %D=%s"
            .formatted(format(k[last]), format(d[last]));
    if (k[last - 1] <= d[last - 1] && k[last] > d[last] && k[last] < 30) {
      return SignalResult.buy(confidence, "Stochastic bullish cross " + note);
    }
    if (k[last - 1] >= d[last - 1] && k[last] < d[last] && k[last] > 70) {
      return SignalResult.sell(confidence, "Stochastic bearish cross " + note);
    }
    return SignalResult.flat("Stochastic neutral " + note);
  }

  private String format(double value) {
    return String.format("%.2f", value);
  }
}
