package com.bottrading.research.backtest;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Collects aggregate statistics about simulated executions. */
public class ExecutionStatistics {

  private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

  private BigDecimal requestedQty = BigDecimal.ZERO;
  private BigDecimal filledQty = BigDecimal.ZERO;
  private int totalLimitOrders;
  private int ttlExpired;

  public void recordLimitAttempt(BigDecimal requested, ExecutionResult result) {
    if (result == null || result.executionType() != ExecutionResult.ExecutionType.LIMIT) {
      return;
    }
    if (requested != null) {
      requestedQty = requestedQty.add(requested, MC);
    }
    filledQty = filledQty.add(result.quantity(), MC);
    totalLimitOrders++;
    if (result.ttlExpired()) {
      ttlExpired++;
    }
  }

  public BigDecimal fillRate() {
    if (requestedQty.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return filledQty.divide(requestedQty, MC);
  }

  public BigDecimal ttlExpiredRate() {
    if (totalLimitOrders == 0) {
      return BigDecimal.ZERO;
    }
    return BigDecimal.valueOf((double) ttlExpired / totalLimitOrders);
  }

  public BigDecimal requestedQty() {
    return requestedQty;
  }

  public BigDecimal filledQty() {
    return filledQty;
  }

  public int totalLimitOrders() {
    return totalLimitOrders;
  }

  public int ttlExpiredOrders() {
    return ttlExpired;
  }
}
