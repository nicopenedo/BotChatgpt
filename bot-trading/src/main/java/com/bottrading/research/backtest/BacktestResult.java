package com.bottrading.research.backtest;

import java.util.List;

public record BacktestResult(
    BacktestRequest request,
    MetricsSummary metrics,
    List<TradeRecord> trades,
    List<EquityPoint> equityCurve) {}
