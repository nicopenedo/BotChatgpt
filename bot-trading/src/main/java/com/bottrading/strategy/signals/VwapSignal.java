package com.bottrading.strategy.signals;

import com.bottrading.strategy.Series;
import com.bottrading.strategy.Signal;
import com.bottrading.strategy.SignalResult;
import java.util.List;

public class VwapSignal implements Signal {

  private final boolean requireConfirmation;
  private final int confirmationBars;
  private final double confidence;

  public VwapSignal(boolean requireConfirmation, int confirmationBars, double confidence) {
    this.requireConfirmation = requireConfirmation;
    this.confirmationBars = Math.max(confirmationBars, 1);
    this.confidence = confidence;
  }

  @Override
  public SignalResult evaluate(List<String[]> klines) {
    if (klines == null || klines.size() < confirmationBars + 2) {
      return SignalResult.flat("VWAP warmup");
    }
    double[] closes = Series.closes(klines);
    double[] volumes = Series.volumes(klines);
    double vwap = Series.vwap(closes, volumes);
    int last = closes.length - 1;
    double prev = closes[last - 1];
    double close = closes[last];
    if (Double.isNaN(vwap)) {
      return SignalResult.flat("VWAP warmup");
    }
    String note =
        "close=%s prev=%s vwap=%s"
            .formatted(format(close), format(prev), format(vwap));
    boolean crossUp = prev < vwap && close > vwap;
    boolean crossDown = prev > vwap && close < vwap;
    if (crossUp && confirm(closes, true)) {
      return SignalResult.buy(confidence, "VWAP cross up " + note);
    }
    if (crossDown && confirm(closes, false)) {
      return SignalResult.sell(confidence, "VWAP cross down " + note);
    }
    return SignalResult.flat("VWAP neutral " + note);
  }

  private boolean confirm(double[] closes, boolean bullish) {
    if (!requireConfirmation) {
      return true;
    }
    int last = closes.length - 1;
    int start = Math.max(1, last - confirmationBars + 1);
    if (bullish) {
      for (int i = start; i <= last; i++) {
        if (closes[i] <= closes[i - 1]) {
          return false;
        }
      }
    } else {
      for (int i = start; i <= last; i++) {
        if (closes[i] >= closes[i - 1]) {
          return false;
        }
      }
    }
    return true;
  }

  private String format(double value) {
    return String.format("%.4f", value);
  }
}
