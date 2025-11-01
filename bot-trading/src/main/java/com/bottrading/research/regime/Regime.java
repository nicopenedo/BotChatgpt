package com.bottrading.research.regime;

import java.time.Instant;

public record Regime(
    String symbol,
    String interval,
    RegimeTrend trend,
    RegimeVolatility volatility,
    double normalizedAtr,
    double adx,
    double rangeScore,
    Instant timestamp) {}
