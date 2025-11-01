package com.bottrading.research.regime;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.model.dto.Kline;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegimeEngineTest {

  private RegimeEngine engine;

  @BeforeEach
  void setUp() {
    engine = new RegimeEngine(new SimpleMeterRegistry());
  }

  @Test
  void shouldClassifyUpTrendWhenMomentumStrong() {
    List<Kline> data = trendingSeries(220, 100, 1.5);
    Regime regime = engine.classify("BTCUSDT", "1m", data);
    assertThat(regime.trend()).isEqualTo(RegimeTrend.UP);
    assertThat(regime.symbol()).isEqualTo("BTCUSDT");
  }

  @Test
  void shouldClassifyDownTrendWhenMomentumNegative() {
    List<Kline> data = trendingSeries(220, 150, -1.8);
    Regime regime = engine.classify("ETHUSDT", "1m", data);
    assertThat(regime.trend()).isEqualTo(RegimeTrend.DOWN);
  }

  @Test
  void shouldClassifyRangeWhenVolatilityLow() {
    List<Kline> data = rangingSeries(220, 50, 0.6);
    Regime regime = engine.classify("BNBUSDT", "1m", data);
    assertThat(regime.trend()).isEqualTo(RegimeTrend.RANGE);
  }

  @Test
  void shouldTrackVolatilityHistoryAndPercentiles() {
    for (int i = 0; i < 12; i++) {
      engine.classify("SOLUSDT", "1m", trendingSeries(220, 20, 0.2));
    }
    Regime low = engine.classify("SOLUSDT", "1m", trendingSeries(220, 20, 0.2));
    assertThat(low.volatility()).isEqualTo(RegimeVolatility.LO);

    for (int i = 0; i < 6; i++) {
      engine.classify("SOLUSDT", "1m", trendingSeries(220, 20, 3.0));
    }
    Regime high = engine.classify("SOLUSDT", "1m", trendingSeries(220, 20, 3.0));
    assertThat(high.volatility()).isEqualTo(RegimeVolatility.HI);

    RegimeEngine.RegimeStatus status = engine.status("SOLUSDT");
    assertThat(status.history()).isNotEmpty();
  }

  private List<Kline> trendingSeries(int length, double base, double step) {
    List<Kline> list = new ArrayList<>(length);
    Instant start = Instant.parse("2024-01-01T00:00:00Z");
    for (int i = 0; i < length; i++) {
      double close = base + (step * i);
      double open = close - step;
      double magnitude = Math.abs(step);
      double high = Math.max(open, close) + magnitude * 0.6;
      double low = Math.min(open, close) - magnitude * 0.6;
      list.add(
          new Kline(
              start.plusSeconds(i * 60L),
              start.plusSeconds((i + 1L) * 60L),
              BigDecimal.valueOf(open),
              BigDecimal.valueOf(high),
              BigDecimal.valueOf(low),
              BigDecimal.valueOf(close),
              BigDecimal.valueOf(1000 + magnitude)));
    }
    return list;
  }

  private List<Kline> rangingSeries(int length, double base, double amplitude) {
    List<Kline> list = new ArrayList<>(length);
    Instant start = Instant.parse("2024-01-01T00:00:00Z");
    for (int i = 0; i < length; i++) {
      double offset = Math.sin(i / 5.0) * amplitude;
      double close = base + offset;
      double open = base + Math.sin((i - 1) / 5.0) * amplitude;
      double high = Math.max(open, close) + amplitude * 0.3;
      double low = Math.min(open, close) - amplitude * 0.3;
      list.add(
          new Kline(
              start.plusSeconds(i * 60L),
              start.plusSeconds((i + 1L) * 60L),
              BigDecimal.valueOf(open),
              BigDecimal.valueOf(high),
              BigDecimal.valueOf(low),
              BigDecimal.valueOf(close),
              BigDecimal.valueOf(900 + amplitude)));
    }
    return list;
  }
}

