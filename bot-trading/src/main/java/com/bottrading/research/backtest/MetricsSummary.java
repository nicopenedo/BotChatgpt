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
    int trades,
    BigDecimal exposure) {}
