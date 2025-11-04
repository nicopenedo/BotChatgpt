package com.bottrading.research.regime;

import com.bottrading.model.dto.Kline;
import com.bottrading.strategy.Series;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RegimeEngine {

  private static final Logger log = LoggerFactory.getLogger(RegimeEngine.class);
  private static final int DEFAULT_PERIOD = 14;
  private static final int EMA_FAST = 50;
  private static final int EMA_SLOW = 200;
  private static final int BB_PERIOD = 20;
  private static final double VOL_PERCENTILE = 0.65;

  private final MeterRegistry meterRegistry;
  private final ConcurrentMap<String, SymbolState> stateBySymbol = new ConcurrentHashMap<>();

  public RegimeEngine(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public Regime classify(String symbol, String interval, List<Kline> klines) {
    Objects.requireNonNull(symbol, "symbol");
    Objects.requireNonNull(interval, "interval");
    if (klines == null || klines.size() < EMA_SLOW + 5) {
      Regime fallback =
          new Regime(
              symbol,
              interval,
              RegimeTrend.RANGE,
              RegimeVolatility.LO,
              Double.NaN,
              Double.NaN,
              Double.NaN,
              Instant.now());
      updateState(symbol, fallback);
      return fallback;
    }

    double[] closes = toDoubleArray(klines, k -> k.close());
    double[] highs = toDoubleArray(klines, k -> k.high());
    double[] lows = toDoubleArray(klines, k -> k.low());

    int last = closes.length - 1;
    double[] emaFast = Series.ema(closes, EMA_FAST);
    double[] emaSlow = Series.ema(closes, EMA_SLOW);
    double[] atr = Series.atr(highs, lows, closes, DEFAULT_PERIOD);

    double normalizedAtr = Double.NaN;
    double lastClose = closes[last];
    if (lastClose > 0 && !Double.isNaN(atr[last]) && atr[last] > 0) {
      normalizedAtr = atr[last] / lastClose;
    }

    double adx = computeAdx(highs, lows, closes, DEFAULT_PERIOD)[last];
    double[] std = Series.standardDeviation(closes, BB_PERIOD);
    double rangeScore = Double.isNaN(std[last]) ? Double.NaN : (std[last] * 4) / lastClose;

    RegimeTrend trend = classifyTrend(emaFast[last], emaSlow[last], adx);
    RegimeVolatility volatility = classifyVolatility(symbol, normalizedAtr);

    Instant ts = Optional.ofNullable(klines.get(last).closeTime()).orElseGet(Instant::now);
    Regime regime =
        new Regime(symbol, interval, trend, volatility, normalizedAtr, adx, rangeScore, ts);
    updateState(symbol, regime);
    return regime;
  }

  public Optional<Regime> current(String symbol) {
    return Optional.ofNullable(stateBySymbol.get(symbol)).map(SymbolState::lastRegime);
  }

  public RegimeStatus status(String symbol) {
    SymbolState state = stateBySymbol.get(symbol);
    if (state == null) {
      return new RegimeStatus(symbol, null, 0, Map.of(), Map.of(), 0, List.of());
    }
    return new RegimeStatus(
        symbol,
        state.lastRegime(),
        state.changes(),
        state.trendShares(),
        state.volShares(),
        state.samples(),
        state.history());
  }

  private void updateState(String symbol, Regime regime) {
    SymbolState state =
        stateBySymbol.computeIfAbsent(symbol, key -> new SymbolState(symbol, meterRegistry));
    state.update(regime);
  }

  private RegimeTrend classifyTrend(double emaFast, double emaSlow, double adx) {
    if (Double.isNaN(adx) || adx < 18) {
      return RegimeTrend.RANGE;
    }
    if (Double.isNaN(emaFast) || Double.isNaN(emaSlow)) {
      return RegimeTrend.RANGE;
    }
    if (emaFast > emaSlow) {
      return RegimeTrend.UP;
    }
    if (emaFast < emaSlow) {
      return RegimeTrend.DOWN;
    }
    return RegimeTrend.RANGE;
  }

  private RegimeVolatility classifyVolatility(String symbol, double normalizedAtr) {
    SymbolState state =
        stateBySymbol.computeIfAbsent(symbol, key -> new SymbolState(symbol, meterRegistry));
    if (Double.isNaN(normalizedAtr) || normalizedAtr <= 0) {
      return RegimeVolatility.LO;
    }
    state.addAtr(normalizedAtr);
    if (state.atrHistorySize() < 10) {
      return RegimeVolatility.LO;
    }
    double threshold = state.percentile(VOL_PERCENTILE);
    return normalizedAtr >= threshold ? RegimeVolatility.HI : RegimeVolatility.LO;
  }

  private double[] toDoubleArray(List<Kline> klines, java.util.function.Function<Kline, BigDecimal> fn) {
    double[] values = new double[klines.size()];
    for (int i = 0; i < klines.size(); i++) {
      BigDecimal v = fn.apply(klines.get(i));
      values[i] = v == null ? Double.NaN : v.doubleValue();
    }
    return values;
  }

  private double[] computeAdx(double[] highs, double[] lows, double[] closes, int period) {
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

  public record RegimeStatus(
      String symbol,
      Regime regime,
      long changes,
      Map<String, Double> trendShare,
      Map<String, Double> volatilityShare,
      long samples,
      List<Regime> history) {}

  private static final class SymbolState {
    private final String symbol;
    private final Counter changesCounter;
    private final EnumMap<RegimeTrend, Long> trendSamples = new EnumMap<>(RegimeTrend.class);
    private final EnumMap<RegimeVolatility, Long> volSamples =
        new EnumMap<>(RegimeVolatility.class);
    private final ArrayDeque<Double> atrHistory = new ArrayDeque<>();
    private final ArrayDeque<Regime> history = new ArrayDeque<>();
    private long samples;
    private Regime lastRegime;

    private SymbolState(String symbol, MeterRegistry meterRegistry) {
      this.symbol = symbol;
      this.changesCounter = meterRegistry.counter("regime.changes", Tags.of("symbol", symbol));
      for (RegimeTrend trend : RegimeTrend.values()) {
        Gauge.builder("regime.time_share", this, state -> state.trendShare(trend))
            .tags("symbol", symbol, "type", "trend", "value", trend.name())
            .register(meterRegistry);
      }
      for (RegimeVolatility vol : RegimeVolatility.values()) {
        Gauge.builder("regime.time_share", this, state -> state.volShare(vol))
            .tags("symbol", symbol, "type", "vol", "value", vol.name())
            .register(meterRegistry);
      }
    }

    private synchronized void update(Regime regime) {
      this.samples++;
      trendSamples.merge(regime.trend(), 1L, Long::sum);
      volSamples.merge(regime.volatility(), 1L, Long::sum);
      if (lastRegime != null
          && (!Objects.equals(lastRegime.trend(), regime.trend())
              || !Objects.equals(lastRegime.volatility(), regime.volatility()))) {
        changesCounter.increment();
      }
      this.lastRegime = regime;
      history.addLast(regime);
      while (history.size() > 240) {
        history.removeFirst();
      }
    }

    private synchronized void addAtr(double value) {
      atrHistory.addLast(value);
      while (atrHistory.size() > 500) {
        atrHistory.removeFirst();
      }
    }

    private synchronized double percentile(double percentile) {
      if (atrHistory.isEmpty()) {
        return 0;
      }
      List<Double> snapshot = new ArrayList<>(atrHistory);
      Collections.sort(snapshot);
      int index = (int) Math.floor(Math.max(0, (snapshot.size() - 1) * percentile));
      return snapshot.get(Math.min(index, snapshot.size() - 1));
    }

    private synchronized long atrHistorySize() {
      return atrHistory.size();
    }

    private synchronized double trendShare(RegimeTrend trend) {
      if (samples == 0) {
        return 0;
      }
      return trendSamples.getOrDefault(trend, 0L) / (double) samples;
    }

    private synchronized double volShare(RegimeVolatility vol) {
      if (samples == 0) {
        return 0;
      }
      return volSamples.getOrDefault(vol, 0L) / (double) samples;
    }

    private synchronized Map<String, Double> trendShares() {
      Map<String, Double> map = new HashMap<>();
      for (RegimeTrend trend : RegimeTrend.values()) {
        map.put(trend.name(), trendShare(trend));
      }
      return Map.copyOf(map);
    }

    private synchronized Map<String, Double> volShares() {
      Map<String, Double> map = new HashMap<>();
      for (RegimeVolatility vol : RegimeVolatility.values()) {
        map.put(vol.name(), volShare(vol));
      }
      return Map.copyOf(map);
    }

    private synchronized Regime lastRegime() {
      return lastRegime;
    }

    private synchronized List<Regime> history() {
      return List.copyOf(history);
    }

    private synchronized long changes() {
      return Math.round(changesCounter.count());
    }

    private synchronized long samples() {
      return samples;
    }
  }
}
