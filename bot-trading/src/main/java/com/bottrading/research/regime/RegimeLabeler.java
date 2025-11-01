package com.bottrading.research.regime;

import com.bottrading.model.dto.Kline;
import com.bottrading.strategy.Series;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RegimeLabeler {

  private static final int EMA_FAST = 50;
  private static final int EMA_SLOW = 200;
  private static final int ADX_PERIOD = 14;
  private static final int ATR_PERIOD = 14;
  private static final double THRESHOLD_PERCENTILE = 0.65;

  public List<RegimeLabel> label(String symbol, String interval, List<Kline> klines) {
    Objects.requireNonNull(symbol, "symbol");
    Objects.requireNonNull(interval, "interval");
    if (klines == null || klines.size() < EMA_SLOW + 5) {
      return List.of();
    }

    double[] closes = toArray(klines, k -> k.close().doubleValue());
    double[] highs = toArray(klines, k -> k.high().doubleValue());
    double[] lows = toArray(klines, k -> k.low().doubleValue());

    double[] emaFast = Series.ema(closes, EMA_FAST);
    double[] emaSlow = Series.ema(closes, EMA_SLOW);
    double[] atr = Series.atr(highs, lows, closes, ATR_PERIOD);
    double[] adx = computeAdx(highs, lows, closes, ADX_PERIOD);

    double[] normalizedAtr = new double[atr.length];
    for (int i = 0; i < atr.length; i++) {
      double close = closes[i];
      normalizedAtr[i] = close > 0 ? atr[i] / close : Double.NaN;
    }

    double adxThreshold = percentile(adx, THRESHOLD_PERCENTILE);
    double atrThreshold = percentile(normalizedAtr, THRESHOLD_PERCENTILE);

    int warmup = Math.max(EMA_SLOW, Math.max(ATR_PERIOD * 2, ADX_PERIOD * 2));
    List<RegimeLabel> labels = new ArrayList<>();
    for (int i = warmup; i < closes.length; i++) {
      double currentAdx = adx[i];
      double fast = emaFast[i];
      double slow = emaSlow[i];
      double currentAtr = normalizedAtr[i];

      if (Double.isNaN(currentAdx) || Double.isNaN(fast) || Double.isNaN(slow)) {
        continue;
      }

      RegimeTrend trend = classifyTrend(currentAdx, adxThreshold, fast, slow);
      RegimeVolatility vol = classifyVolatility(currentAtr, atrThreshold);
      Instant ts = Optional.ofNullable(klines.get(i).closeTime()).orElse(klines.get(i).openTime());
      if (ts != null) {
        labels.add(new RegimeLabel(ts, trend, vol));
      }
    }
    return labels;
  }

  public void exportCsv(List<RegimeLabel> labels, Path path) throws IOException {
    new RegimeLabelSet(labels).write(path);
  }

  private RegimeTrend classifyTrend(double adx, double adxThreshold, double emaFast, double emaSlow) {
    if (Double.isNaN(adx) || adx < adxThreshold || Double.isNaN(emaFast) || Double.isNaN(emaSlow)) {
      return RegimeTrend.RANGE;
    }
    double diff = emaFast - emaSlow;
    if (Math.abs(diff) < 1e-6) {
      return RegimeTrend.RANGE;
    }
    return diff > 0 ? RegimeTrend.UP : RegimeTrend.DOWN;
  }

  private RegimeVolatility classifyVolatility(double atrPct, double atrThreshold) {
    if (Double.isNaN(atrPct)) {
      return RegimeVolatility.LO;
    }
    return atrPct >= atrThreshold ? RegimeVolatility.HI : RegimeVolatility.LO;
  }

  private double percentile(double[] values, double percentile) {
    List<Double> filtered = new ArrayList<>();
    for (double v : values) {
      if (!Double.isFinite(v)) {
        continue;
      }
      filtered.add(v);
    }
    if (filtered.isEmpty()) {
      return Double.NaN;
    }
    filtered.sort(Double::compare);
    int index = (int) Math.floor(Math.max(0, (filtered.size() - 1) * percentile));
    return filtered.get(Math.min(index, filtered.size() - 1));
  }

  private double[] toArray(List<Kline> klines, java.util.function.Function<Kline, Double> mapper) {
    double[] values = new double[klines.size()];
    for (int i = 0; i < klines.size(); i++) {
      values[i] = mapper.apply(klines.get(i));
    }
    return values;
  }

  private double[] computeAdx(double[] highs, double[] lows, double[] closes, int period) {
    double[] atr = Series.atr(highs, lows, closes, period);
    double[] plusDm = Series.plusDirectionalMovement(highs, lows);
    double[] minusDm = Series.minusDirectionalMovement(highs, lows);
    double[] plusSmoothed = Series.ema(plusDm, period);
    double[] minusSmoothed = Series.ema(minusDm, period);
    double[] plusDi = new double[closes.length];
    double[] minusDi = new double[closes.length];
    for (int i = 0; i < closes.length; i++) {
      double atrValue = atr[i];
      if (atrValue <= 0 || Double.isNaN(atrValue)) {
        plusDi[i] = 0;
        minusDi[i] = 0;
      } else {
        plusDi[i] = 100 * (plusSmoothed[i] / atrValue);
        minusDi[i] = 100 * (minusSmoothed[i] / atrValue);
      }
    }
    double[] dx = Series.dx(plusDi, minusDi);
    return Series.ema(dx, period);
  }
}
