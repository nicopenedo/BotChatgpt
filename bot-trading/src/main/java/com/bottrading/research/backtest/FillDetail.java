package com.bottrading.research.backtest;

import java.math.BigDecimal;
import java.time.Instant;

/** Detail for a single order fill during the execution simulation. */
public record FillDetail(
    Instant time,
    BigDecimal price,
    BigDecimal quantity,
    long queueTimeMs,
    BigDecimal slippageBps,
    BigDecimal fee,
    boolean maker) {}
