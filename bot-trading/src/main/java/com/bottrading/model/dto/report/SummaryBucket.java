package com.bottrading.model.dto.report;

import java.math.BigDecimal;
import java.time.Instant;

public record SummaryBucket(
    Instant periodStart,
    Instant periodEnd,
    String label,
    long trades,
    long wins,
    long losses,
    BigDecimal grossPnL,
    BigDecimal netPnL,
    BigDecimal fees,
    BigDecimal winRate,
    BigDecimal profitFactor,
    BigDecimal maxDrawdown,
    BigDecimal sharpe,
    BigDecimal sortino) {}
