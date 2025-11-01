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
    BigDecimal feesBps,
    BigDecimal pnl,
    BigDecimal pnlNet,
    BigDecimal signalEdge,
    BigDecimal pnlR,
    BigDecimal slippageBps,
    BigDecimal slippageCost,
    BigDecimal timingBps,
    BigDecimal timingCost,
    String clientOrderId,
    String decisionKey,
    String decisionNote) {}
