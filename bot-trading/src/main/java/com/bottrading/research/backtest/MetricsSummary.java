package com.bottrading.research.backtest;

import java.math.BigDecimal;

public record MetricsSummary(
    BigDecimal cagr,
    BigDecimal sharpe,
    BigDecimal sortino,
    BigDecimal calmar,
    BigDecimal maxDrawdown,
    BigDecimal profitFactor,
    BigDecimal winRate,
    BigDecimal expectancy,
    BigDecimal averageR,
    int trades,
    BigDecimal exposure,
    BigDecimal fillRate,
    BigDecimal ttlExpiredRate) {}
