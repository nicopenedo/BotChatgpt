package com.bottrading.research.backtest;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated result for an execution attempt. Supports partial fills, queues and
 * metadata needed for realistic simulation metrics.
 */
public class ExecutionResult {

  private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

  private final List<FillDetail> fills;
  private final BigDecimal requestedQuantity;
  private final boolean ttlExpired;
  private final ExecutionType executionType;

  public ExecutionResult(
      List<FillDetail> fills,
      BigDecimal requestedQuantity,
      boolean ttlExpired,
      ExecutionType executionType) {
    this.fills = fills == null ? List.of() : List.copyOf(fills);
    this.requestedQuantity = requestedQuantity == null ? BigDecimal.ZERO : requestedQuantity;
    this.ttlExpired = ttlExpired;
    this.executionType = executionType == null ? ExecutionType.MARKET : executionType;
  }

  public static ExecutionResult empty(BigDecimal requestedQuantity, ExecutionType type) {
    return new ExecutionResult(List.of(), requestedQuantity, true, type);
  }

  public static ExecutionResult simpleFill(
      Instant time,
      BigDecimal price,
      BigDecimal quantity,
      BigDecimal fee,
      BigDecimal slippageBps,
      boolean maker,
      ExecutionType type) {
    FillDetail fill =
        new FillDetail(
            time,
            price,
            quantity,
            0L,
            slippageBps == null ? BigDecimal.ZERO : slippageBps,
            fee == null ? BigDecimal.ZERO : fee,
            maker);
    return new ExecutionResult(List.of(fill), quantity, false, type);
  }

  public List<FillDetail> fills() {
    return fills;
  }

  public BigDecimal requestedQuantity() {
    return requestedQuantity;
  }

  public boolean ttlExpired() {
    return ttlExpired;
  }

  public ExecutionType executionType() {
    return executionType;
  }

  public boolean hasFill() {
    return quantity().compareTo(BigDecimal.ZERO) > 0;
  }

  public BigDecimal quantity() {
    BigDecimal sum = BigDecimal.ZERO;
    for (FillDetail fill : fills) {
      sum = sum.add(fill.quantity(), MC);
    }
    return sum;
  }

  public BigDecimal totalFee() {
    BigDecimal sum = BigDecimal.ZERO;
    for (FillDetail fill : fills) {
      sum = sum.add(fill.fee(), MC);
    }
    return sum;
  }

  public BigDecimal averagePrice() {
    BigDecimal qty = quantity();
    if (qty.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal notional = BigDecimal.ZERO;
    for (FillDetail fill : fills) {
      notional = notional.add(fill.price().multiply(fill.quantity(), MC), MC);
    }
    return notional.divide(qty, MC);
  }

  public BigDecimal totalNotional() {
    BigDecimal sum = BigDecimal.ZERO;
    for (FillDetail fill : fills) {
      sum = sum.add(fill.price().multiply(fill.quantity(), MC), MC);
    }
    return sum;
  }

  public BigDecimal slippageBps() {
    BigDecimal qty = quantity();
    if (qty.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal weighted = BigDecimal.ZERO;
    for (FillDetail fill : fills) {
      weighted = weighted.add(fill.slippageBps().multiply(fill.quantity(), MC), MC);
    }
    return weighted.divide(qty, MC);
  }

  public BigDecimal averageQueueTimeMs() {
    BigDecimal qty = quantity();
    if (qty.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal weighted = BigDecimal.ZERO;
    for (FillDetail fill : fills) {
      weighted =
          weighted.add(BigDecimal.valueOf(fill.queueTimeMs()).multiply(fill.quantity(), MC), MC);
    }
    return weighted.divide(qty, MC);
  }

  public Instant firstFillTime() {
    if (fills.isEmpty()) {
      return null;
    }
    Instant earliest = fills.get(0).time();
    for (FillDetail fill : fills) {
      if (fill.time() != null && (earliest == null || fill.time().isBefore(earliest))) {
        earliest = fill.time();
      }
    }
    return earliest;
  }

  public ExecutionResult merge(ExecutionResult other) {
    List<FillDetail> merged = new ArrayList<>(fills);
    merged.addAll(other.fills());
    BigDecimal requested = requestedQuantity.add(other.requestedQuantity(), MC);
    boolean expired = ttlExpired || other.ttlExpired();
    return new ExecutionResult(merged, requested, expired, executionType);
  }

  public enum ExecutionType {
    LIMIT,
    MARKET,
    TWAP,
    POV
  }
}
