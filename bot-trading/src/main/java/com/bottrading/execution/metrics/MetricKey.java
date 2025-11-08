package com.bottrading.execution.metrics;

// FIX: Avoid reassigning record components; rely on canonical constructor initialization.

import java.util.Objects;
import java.util.UUID;

public record MetricKey(String tenant, String symbol, String venue) {

  public MetricKey {
    tenant = tenant != null ? tenant : "global";
    symbol = Objects.requireNonNull(symbol, "symbol");
  }

  public static MetricKey of(UUID tenantId, String symbol) {
    return of(tenantId, symbol, null);
  }

  public static MetricKey of(UUID tenantId, String symbol, String venue) {
    return new MetricKey(tenantId != null ? tenantId.toString() : "global", symbol, venue);
  }
}
