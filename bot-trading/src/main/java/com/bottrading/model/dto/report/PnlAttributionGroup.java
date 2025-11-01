package com.bottrading.model.dto.report;

import java.math.BigDecimal;

public record PnlAttributionGroup(
    String preset,
    String regime,
    BigDecimal pnlGross,
    BigDecimal signalEdge,
    BigDecimal timingCost,
    BigDecimal slippageCost,
    BigDecimal feesCost,
    BigDecimal pnlNet,
    long trades) {}
