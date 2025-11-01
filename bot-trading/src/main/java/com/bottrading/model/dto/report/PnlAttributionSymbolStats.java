package com.bottrading.model.dto.report;

public record PnlAttributionSymbolStats(
    String symbol,
    double avgSlippageBps,
    double worstSlippageBps,
    double bestSlippageBps,
    double avgTimingBps,
    double worstTimingBps,
    double bestTimingBps,
    double netPnl) {}
