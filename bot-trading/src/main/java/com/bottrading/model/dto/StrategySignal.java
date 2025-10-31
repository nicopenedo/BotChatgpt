package com.bottrading.model.dto;

import com.bottrading.model.enums.OrderSide;
import java.math.BigDecimal;
import java.time.Instant;

public record StrategySignal(
    Instant generatedAt,
    OrderSide side,
    BigDecimal price,
    BigDecimal quantity,
    String reason) {}
