package com.bottrading.research.backtest;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;

public record BacktestRequest(
    String symbol,
    String interval,
    Instant from,
    Instant to,
    Path strategyConfig,
    BigDecimal slippageBps,
    BigDecimal takerFeeBps,
    BigDecimal makerFeeBps,
    boolean useCache) {}
