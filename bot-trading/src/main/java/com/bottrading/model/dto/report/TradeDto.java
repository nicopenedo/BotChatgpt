package com.bottrading.model.dto.report;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeDto(
    Long id,
    String symbol,
    String side,
    Instant executedAt,
    BigDecimal price,
    BigDecimal quantity,
    BigDecimal fee,
    BigDecimal pnl,
    BigDecimal pnlR,
    BigDecimal slippageBps,
    String clientOrderId,
    String decisionKey,
    String decisionNote) {}
