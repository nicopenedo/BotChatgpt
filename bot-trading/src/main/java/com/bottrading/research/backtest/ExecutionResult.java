package com.bottrading.research.backtest;

import java.math.BigDecimal;

public record ExecutionResult(BigDecimal price, BigDecimal quantity, BigDecimal fee) {}
