package com.bottrading.execution;

import java.math.BigDecimal;
import java.util.Objects;

public record MarketSnapshot(
    BigDecimal midPrice,
    double spreadBps,
    double volatilityBps,
    double latencyMs,
    BigDecimal barVolume,
    BigDecimal quoteBarVolume) {

  public MarketSnapshot {
    Objects.requireNonNull(midPrice, "midPrice");
  }
}
