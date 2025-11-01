package com.bottrading.strategy.signals;

import com.bottrading.strategy.Series;
import com.bottrading.strategy.Signal;
import com.bottrading.strategy.SignalResult;
import java.util.List;

public class MacdSignal implements Signal {

  private final int fastPeriod;
  private final int slowPeriod;
  private final int signalPeriod;
  private final double confidence;

  public MacdSignal(int fastPeriod, int slowPeriod, int signalPeriod, double confidence) {
    if (fastPeriod <= 0 || slowPeriod <= 0 || signalPeriod <= 0) {
      throw new IllegalArgumentException("Invalid MACD periods");
    }
    this.fastPeriod = fastPeriod;
    this.slowPeriod = slowPeriod;
    this.signalPeriod = signalPeriod;
    this.confidence = confidence;
  }

  @Override
  public SignalResult evaluate(List<String[]> klines) {
    int min = Math.max(slowPeriod, signalPeriod) + 2;
    if (klines == null || klines.size() < min) {
      return SignalResult.flat("MACD warmup");
    }
    double[] closes = Series.closes(klines);
    double[] macdLine = Series.macd(closes, fastPeriod, slowPeriod);
    double[] signalLine = Series.ema(macdLine, signalPeriod);
    int last = closes.length - 1;
    if (Double.isNaN(signalLine[last]) || Double.isNaN(signalLine[last - 1])) {
      return SignalResult.flat("MACD warmup");
    }
    double hist = macdLine[last] - signalLine[last];
    double prevHist = macdLine[last - 1] - signalLine[last - 1];
    String note =
        "hist=%s macd=%s signal=%s"
            .formatted(format(hist), format(macdLine[last]), format(signalLine[last]));
    if (prevHist <= 0 && hist > 0) {
      return SignalResult.buy(confidence, "MACD bullish turn " + note);
    }
    if (prevHist >= 0 && hist < 0) {
      return SignalResult.sell(confidence, "MACD bearish turn " + note);
    }
    return SignalResult.flat("MACD flat " + note);
  }

  private String format(double value) {
    return String.format("%.4f", value);
  }
}
