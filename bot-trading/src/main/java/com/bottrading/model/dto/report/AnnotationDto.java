package com.bottrading.model.dto.report;

import java.math.BigDecimal;
import java.time.Instant;

public record AnnotationDto(
    Instant ts,
    String type,
    BigDecimal price,
    BigDecimal qty,
    BigDecimal pnl,
    BigDecimal pnlR,
    BigDecimal fee,
    BigDecimal slippageBps,
    String text) {}
