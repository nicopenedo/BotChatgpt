package com.bottrading.service.risk;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeEvent(
    String symbol,
    boolean opening,
    BigDecimal pnl,
    BigDecimal equityAfter,
    BigDecimal notional,
    Instant occurredAt) {

  public TradeEvent(
      String symbol,
      boolean opening,
      BigDecimal pnl,
      BigDecimal equityAfter,
      BigDecimal notional) {
    this(symbol, opening, pnl, equityAfter, notional, Instant.now());
  }
}
