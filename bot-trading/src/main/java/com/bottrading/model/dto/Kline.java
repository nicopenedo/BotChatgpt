package com.bottrading.model.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record Kline(
    Instant openTime,
    Instant closeTime,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume) {}
