package com.bottrading.execution.metrics;

import java.util.Objects;
import java.util.UUID;

public record MetricKey(String tenant, String symbol, String venue) {

  public MetricKey {
    this.tenant = tenant != null ? tenant : "global";
    this.symbol = Objects.requireNonNull(symbol, "symbol");
    this.venue = venue;
  }

  public static MetricKey of(UUID tenantId, String symbol) {
    return of(tenantId, symbol, null);
  }

  public static MetricKey of(UUID tenantId, String symbol, String venue) {
    return new MetricKey(tenantId != null ? tenantId.toString() : "global", symbol, venue);
  }
}
