package com.bottrading.strategy.signals;

import com.bottrading.strategy.Series;
import com.bottrading.strategy.Signal;
import com.bottrading.strategy.SignalResult;
import java.util.List;

public class RsiSignal implements Signal {

  private final int period;
  private final double lowerThreshold;
  private final double upperThreshold;
  private final int trendPeriod;
  private final double confidence;

  public RsiSignal(
      int period, double lowerThreshold, double upperThreshold, int trendPeriod, double confidence) {
    if (period <= 0) {
      throw new IllegalArgumentException("RSI period must be positive");
    }
    this.period = period;
    this.lowerThreshold = lowerThreshold;
    this.upperThreshold = upperThreshold;
    this.trendPeriod = Math.max(trendPeriod, period);
    this.confidence = confidence;
  }

  @Override
  public SignalResult evaluate(List<String[]> klines) {
    int min = Math.max(period, trendPeriod) + 1;
    if (klines == null || klines.size() < min) {
      return SignalResult.flat("RSI warmup");
    }
    double[] closes = Series.closes(klines);
    double[] rsi = Series.rsi(closes, period);
    double[] trend = Series.sma(closes, trendPeriod);
    int last = closes.length - 1;
    if (Double.isNaN(rsi[last]) || Double.isNaN(trend[last])) {
      return SignalResult.flat("RSI warmup");
    }
    double price = closes[last];
    double ma = trend[last];
    String note =
        "rsi=%s price=%s trendSma=%s"
            .formatted(format(rsi[last]), format(price), format(ma));
    if (price > ma && rsi[last] < lowerThreshold) {
      return SignalResult.buy(confidence, "RSI oversold in uptrend " + note);
    }
    if (price < ma && rsi[last] > upperThreshold) {
      return SignalResult.sell(confidence, "RSI overbought in downtrend " + note);
    }
    return SignalResult.flat("RSI neutral " + note);
  }

  private String format(double value) {
    return String.format("%.2f", value);
  }
}
