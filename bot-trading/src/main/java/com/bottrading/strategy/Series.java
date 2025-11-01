package com.bottrading.strategy;

import java.util.ArrayList;
import java.util.List;

public final class Series {

  private Series() {}

  public static double[] closes(List<String[]> klines) {
    return extract(klines, 4);
  }

  public static double[] opens(List<String[]> klines) {
    return extract(klines, 1);
  }

  public static double[] highs(List<String[]> klines) {
    return extract(klines, 2);
  }

  public static double[] lows(List<String[]> klines) {
    return extract(klines, 3);
  }

  public static double[] volumes(List<String[]> klines) {
    return extract(klines, 5);
  }

  private static double[] extract(List<String[]> klines, int index) {
    double[] values = new double[klines.size()];
    for (int i = 0; i < klines.size(); i++) {
      values[i] = Double.parseDouble(klines.get(i)[index]);
    }
    return values;
  }

  public static double[] sma(double[] values, int period) {
    double[] result = new double[values.length];
    double sum = 0;
    for (int i = 0; i < values.length; i++) {
      sum += values[i];
      if (i >= period) {
        sum -= values[i - period];
      }
      if (i >= period - 1) {
        result[i] = sum / period;
      } else {
        result[i] = Double.NaN;
      }
    }
    return result;
  }

  public static double[] ema(double[] values, int period) {
    double[] result = new double[values.length];
    double multiplier = 2.0 / (period + 1);
    double ema = 0;
    for (int i = 0; i < values.length; i++) {
      double price = values[i];
      if (i == 0) {
        ema = price;
      } else if (i < period) {
        ema = ema + (price - ema) / (i + 1);
      } else {
        ema = (price - ema) * multiplier + ema;
      }
      result[i] = ema;
    }
    return result;
  }

  public static double[] macd(double[] values, int fastPeriod, int slowPeriod) {
    double[] fast = ema(values, fastPeriod);
    double[] slow = ema(values, slowPeriod);
    double[] macd = new double[values.length];
    for (int i = 0; i < values.length; i++) {
      macd[i] = fast[i] - slow[i];
    }
    return macd;
  }

  public static double[] rsi(double[] values, int period) {
    double[] rsi = new double[values.length];
    double gain = 0;
    double loss = 0;
    for (int i = 1; i < values.length; i++) {
      double change = values[i] - values[i - 1];
      double currentGain = Math.max(change, 0);
      double currentLoss = Math.max(-change, 0);
      if (i <= period) {
        gain += currentGain;
        loss += currentLoss;
        if (i == period) {
          gain /= period;
          loss /= period;
        }
      } else {
        gain = (gain * (period - 1) + currentGain) / period;
        loss = (loss * (period - 1) + currentLoss) / period;
      }
      if (i >= period) {
        double rs = loss == 0 ? 100 : gain / loss;
        rsi[i] = 100 - (100 / (1 + rs));
      } else {
        rsi[i] = Double.NaN;
      }
    }
    return rsi;
  }

  public static double[] atr(double[] highs, double[] lows, double[] closes, int period) {
    double[] atr = new double[closes.length];
    double sumTr = 0;
    for (int i = 1; i < closes.length; i++) {
      double tr = trueRange(highs[i], lows[i], closes[i - 1]);
      if (i <= period) {
        sumTr += tr;
        if (i == period) {
          atr[i] = sumTr / period;
        }
      } else {
        atr[i] = ((atr[i - 1] * (period - 1)) + tr) / period;
      }
    }
    return atr;
  }

  private static double trueRange(double high, double low, double prevClose) {
    double range1 = high - low;
    double range2 = Math.abs(high - prevClose);
    double range3 = Math.abs(low - prevClose);
    return Math.max(range1, Math.max(range2, range3));
  }

  public static double[] standardDeviation(double[] values, int period) {
    double[] std = new double[values.length];
    double sum = 0;
    double sumSq = 0;
    for (int i = 0; i < values.length; i++) {
      double v = values[i];
      sum += v;
      sumSq += v * v;
      if (i >= period) {
        double old = values[i - period];
        sum -= old;
        sumSq -= old * old;
      }
      if (i >= period - 1) {
        double mean = sum / period;
        double variance = Math.max((sumSq / period) - (mean * mean), 0);
        std[i] = Math.sqrt(variance);
      } else {
        std[i] = Double.NaN;
      }
    }
    return std;
  }

  public static double[] plusDirectionalMovement(double[] highs, double[] lows) {
    double[] result = new double[highs.length];
    for (int i = 1; i < highs.length; i++) {
      double upMove = highs[i] - highs[i - 1];
      double downMove = lows[i - 1] - lows[i];
      result[i] = (upMove > downMove && upMove > 0) ? upMove : 0;
    }
    return result;
  }

  public static double[] minusDirectionalMovement(double[] highs, double[] lows) {
    double[] result = new double[highs.length];
    for (int i = 1; i < highs.length; i++) {
      double upMove = highs[i - 1] - highs[i];
      double downMove = lows[i] - lows[i - 1];
      result[i] = (downMove > upMove && downMove > 0) ? downMove : 0;
    }
    return result;
  }

  public static double[] dx(double[] plusDI, double[] minusDI) {
    double[] result = new double[plusDI.length];
    for (int i = 0; i < plusDI.length; i++) {
      double denominator = plusDI[i] + minusDI[i];
      if (denominator == 0) {
        result[i] = 0;
      } else {
        result[i] = 100 * Math.abs(plusDI[i] - minusDI[i]) / denominator;
      }
    }
    return result;
  }

  public static double[] stochasticK(double[] closes, double[] highs, double[] lows, int period) {
    double[] k = new double[closes.length];
    for (int i = 0; i < closes.length; i++) {
      if (i < period - 1) {
        k[i] = Double.NaN;
        continue;
      }
      double highest = Double.NEGATIVE_INFINITY;
      double lowest = Double.POSITIVE_INFINITY;
      for (int j = i - period + 1; j <= i; j++) {
        highest = Math.max(highest, highs[j]);
        lowest = Math.min(lowest, lows[j]);
      }
      double range = highest - lowest;
      k[i] = range == 0 ? 0 : ((closes[i] - lowest) / range) * 100;
    }
    return k;
  }

  public static double[] smooth(double[] values, int period) {
    double[] result = new double[values.length];
    double sum = 0;
    int count = 0;
    for (int i = 0; i < values.length; i++) {
      double v = values[i];
      if (Double.isNaN(v)) {
        result[i] = Double.NaN;
        continue;
      }
      sum += v;
      count++;
      if (count > period) {
        sum -= values[i - period];
        count--;
      }
      if (count == period) {
        result[i] = sum / period;
      } else {
        result[i] = Double.NaN;
      }
    }
    return result;
  }

  public static double vwap(double[] closes, double[] volumes) {
    double pv = 0;
    double vol = 0;
    for (int i = 0; i < closes.length; i++) {
      pv += closes[i] * volumes[i];
      vol += volumes[i];
    }
    return vol == 0 ? Double.NaN : pv / vol;
  }

  public static List<String> toNotes(List<SignalResult> results) {
    List<String> notes = new ArrayList<>();
    for (SignalResult result : results) {
      if (result != null && result.note() != null && !result.note().isBlank()) {
        notes.add(result.note());
      }
    }
    return notes;
  }
}
