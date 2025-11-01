package com.bottrading.research.backtest;

import com.bottrading.strategy.SignalSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TradeRecord(
    Instant entryTime,
    BigDecimal entryPrice,
    Instant exitTime,
    BigDecimal exitPrice,
    BigDecimal quantity,
    BigDecimal pnl,
    boolean win,
    SignalSide side,
    String entryReason,
    String exitReason,
    List<String> entrySignals,
    List<String> exitSignals,
    List<FillDetail> entryFills,
    List<FillDetail> exitFills,
    BigDecimal totalFees,
    BigDecimal slippageBps,
    BigDecimal averageQueueTimeMs,
    BigDecimal riskMultiple,
    ExecutionResult.ExecutionType entryExecutionType,
    ExecutionResult.ExecutionType exitExecutionType) {

  public TradeRecord {
    entrySignals = entrySignals == null ? List.of() : List.copyOf(entrySignals);
    exitSignals = exitSignals == null ? List.of() : List.copyOf(exitSignals);
    entryFills = entryFills == null ? List.of() : List.copyOf(entryFills);
    exitFills = exitFills == null ? List.of() : List.copyOf(exitFills);
  }
}
