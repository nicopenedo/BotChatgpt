package com.bottrading.research.backtest;

import com.bottrading.model.dto.Kline;
import java.util.List;

public record BacktestResult(
    BacktestRequest request,
    MetricsSummary metrics,
    List<TradeRecord> trades,
    List<EquityPoint> equityCurve,
    List<Kline> klines,
    String dataHash,
    ExecutionStatistics executionStatistics) {}
