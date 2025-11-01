package com.bottrading.research.regime;

import java.time.Instant;

public record RegimeLabel(Instant timestamp, RegimeTrend trend, RegimeVolatility volatility) {}
