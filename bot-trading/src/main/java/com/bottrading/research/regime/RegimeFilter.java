package com.bottrading.research.regime;

import java.time.Instant;

public record RegimeFilter(RegimeTrend trend, RegimeLabelSet labels) {

  public boolean isActive() {
    return trend != null && labels != null && !labels.isEmpty();
  }

  public boolean allows(Instant timestamp) {
    if (!isActive() || timestamp == null) {
      return true;
    }
    RegimeLabel label = labels.labelAt(timestamp);
    return label != null && trend == label.trend();
  }

  public long count(Instant from, Instant to) {
    if (!isActive()) {
      return 0;
    }
    return labels.count(trend, from, to);
  }
}
