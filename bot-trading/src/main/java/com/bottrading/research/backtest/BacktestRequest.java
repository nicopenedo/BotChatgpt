package com.bottrading.research.backtest;

import com.bottrading.research.backtest.realistic.RealisticBacktestConfig;
import com.bottrading.research.regime.RegimeFilter;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;

public record BacktestRequest(
    String symbol,
    String interval,
    Instant from,
    Instant to,
    Path strategyConfig,
    Path genomesConfig,
    BigDecimal slippageBps,
    BigDecimal takerFeeBps,
    BigDecimal makerFeeBps,
    boolean useDynamicFees,
    Long seed,
    String runId,
    boolean useCache,
    RegimeFilter regimeFilter,
    RealisticBacktestConfig realisticConfig) {}
