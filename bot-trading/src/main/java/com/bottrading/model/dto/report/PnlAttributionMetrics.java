package com.bottrading.model.dto.report;

import java.util.Map;

public record PnlAttributionMetrics(
    double timingAvgBps,
    double feesAvgBps,
    Map<String, Double> slippageAvgBpsBySymbol) {}
