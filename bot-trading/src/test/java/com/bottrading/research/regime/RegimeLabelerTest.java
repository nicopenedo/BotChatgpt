package com.bottrading.research.regime;

import com.bottrading.model.dto.Kline;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RegimeLabelerTest {

  private final RegimeLabeler labeler = new RegimeLabeler();

  @Test
  void labelsUptrendDatasetAsUp() {
    List<Kline> klines = generateSeries(Instant.EPOCH, 300, 100, 0.5);
    List<RegimeLabel> labels = labeler.label("TEST", "1m", klines);
    Assertions.assertFalse(labels.isEmpty());
    Assertions.assertEquals(RegimeTrend.UP, labels.get(labels.size() - 1).trend());
  }

  @Test
  void labelsDowntrendDatasetAsDown() {
    List<Kline> klines = generateSeries(Instant.EPOCH, 300, 100, -0.5);
    List<RegimeLabel> labels = labeler.label("TEST", "1m", klines);
    Assertions.assertFalse(labels.isEmpty());
    Assertions.assertEquals(RegimeTrend.DOWN, labels.get(labels.size() - 1).trend());
  }

  @Test
  void labelsRangeDatasetAsRange() {
    List<Kline> klines = new ArrayList<>();
    double price = 100;
    Instant start = Instant.EPOCH;
    for (int i = 0; i < 300; i++) {
      double noise = (i % 2 == 0 ? 1 : -1) * 0.2;
      price += noise;
      klines.add(kline(start.plusSeconds(i * 60L), price));
    }
    List<RegimeLabel> labels = labeler.label("TEST", "1m", klines);
    Assertions.assertFalse(labels.isEmpty());
    Assertions.assertEquals(RegimeTrend.RANGE, labels.get(labels.size() - 1).trend());
  }

  private List<Kline> generateSeries(Instant start, int count, double base, double delta) {
    List<Kline> list = new ArrayList<>();
    double price = base;
    for (int i = 0; i < count; i++) {
      price += delta;
      list.add(kline(start.plusSeconds(i * 60L), price));
    }
    return list;
  }

  private Kline kline(Instant open, double price) {
    BigDecimal value = BigDecimal.valueOf(price);
    return new Kline(open, open.plusSeconds(60), value, value, value, value, BigDecimal.ONE);
  }
}
