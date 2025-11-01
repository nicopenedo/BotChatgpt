package com.bottrading.research.backtest;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeRecord(
    Instant entryTime,
    BigDecimal entryPrice,
    Instant exitTime,
    BigDecimal exitPrice,
    BigDecimal quantity,
    BigDecimal pnl,
    boolean win) {}
