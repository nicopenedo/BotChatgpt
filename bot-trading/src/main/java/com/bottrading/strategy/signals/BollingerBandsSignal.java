package com.bottrading.strategy.signals;

import com.bottrading.strategy.Series;
import com.bottrading.strategy.Signal;
import com.bottrading.strategy.SignalResult;
import java.util.List;

public class BollingerBandsSignal implements Signal {

  private final int period;
  private final double stdDevs;
  private final double confidence;

  public BollingerBandsSignal(int period, double stdDevs, double confidence) {
    if (period <= 0) {
      throw new IllegalArgumentException("Bollinger period must be positive");
    }
    this.period = period;
    this.stdDevs = stdDevs;
    this.confidence = confidence;
  }

  @Override
  public SignalResult evaluate(List<String[]> klines) {
    if (klines == null || klines.size() < period) {
      return SignalResult.flat("Bollinger warmup");
    }
    double[] closes = Series.closes(klines);
    double[] sma = Series.sma(closes, period);
    double[] std = Series.standardDeviation(closes, period);
    int last = closes.length - 1;
    if (Double.isNaN(sma[last]) || Double.isNaN(std[last])) {
      return SignalResult.flat("Bollinger warmup");
    }
    double upper = sma[last] + stdDevs * std[last];
    double lower = sma[last] - stdDevs * std[last];
    double close = closes[last];
    String note =
        "close=%s upper=%s lower=%s"
            .formatted(format(close), format(upper), format(lower));
    if (close <= lower) {
      return SignalResult.buy(confidence, "Bollinger lower touch " + note);
    }
    if (close >= upper) {
      return SignalResult.sell(confidence, "Bollinger upper touch " + note);
    }
    return SignalResult.flat("Bollinger neutral " + note);
  }

  private String format(double value) {
    return String.format("%.4f", value);
  }
}
