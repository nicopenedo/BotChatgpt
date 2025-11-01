package com.bottrading.research.backtest;

import com.bottrading.strategy.SignalSide;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Portfolio {

  private final MathContext mc = new MathContext(12, RoundingMode.HALF_UP);
  private final List<TradeRecord> trades = new ArrayList<>();
  private final List<EquityPoint> equityCurve = new ArrayList<>();

  private BigDecimal quoteBalance;
  private BigDecimal basePosition = BigDecimal.ZERO;
  private Instant openTime;
  private BigDecimal entryPrice;
  private TradeMetadata entryMetadata;
  private List<FillDetail> entryFills = List.of();
  private BigDecimal entryFees = BigDecimal.ZERO;
  private BigDecimal entrySlippageBps = BigDecimal.ZERO;
  private BigDecimal entryQueueTimeMs = BigDecimal.ZERO;
  private BigDecimal entryRequestedQuantity = BigDecimal.ZERO;
  private BigDecimal entryNotional = BigDecimal.ZERO;
  private ExecutionResult.ExecutionType entryExecutionType = ExecutionResult.ExecutionType.LIMIT;

  public Portfolio(BigDecimal startingCapital) {
    this.quoteBalance = startingCapital;
  }

  public boolean buy(ExecutionResult execution, TradeMetadata metadata) {
    if (basePosition.compareTo(BigDecimal.ZERO) > 0) {
      return false;
    }
    if (execution == null || !execution.hasFill()) {
      return false;
    }
    BigDecimal notional = execution.totalNotional();
    BigDecimal fee = execution.totalFee();
    quoteBalance = quoteBalance.subtract(notional.add(fee, mc), mc);
    basePosition = execution.quantity();
    entryPrice = execution.averagePrice();
    openTime = execution.firstFillTime();
    entryMetadata = metadata;
    entryFills = List.copyOf(execution.fills());
    entryFees = fee;
    entrySlippageBps = execution.slippageBps();
    entryQueueTimeMs = execution.averageQueueTimeMs();
    entryRequestedQuantity = execution.requestedQuantity();
    entryNotional = notional;
    entryExecutionType = execution.executionType();
    return true;
  }

  public void sell(ExecutionResult execution, TradeMetadata metadata) {
    if (basePosition.compareTo(BigDecimal.ZERO) <= 0) {
      return;
    }
    if (execution == null || !execution.hasFill()) {
      return;
    }
    BigDecimal proceeds = execution.totalNotional().subtract(execution.totalFee(), mc);
    quoteBalance = quoteBalance.add(proceeds, mc);
    BigDecimal pnl =
        proceeds.subtract(entryNotional.add(entryFees, mc), mc);
    boolean win = pnl.compareTo(BigDecimal.ZERO) > 0;
    TradeMetadata meta = entryMetadata != null ? entryMetadata : TradeMetadata.empty(SignalSide.BUY);
    TradeMetadata exitMeta = metadata != null ? metadata : TradeMetadata.empty(SignalSide.SELL);
    trades.add(
        new TradeRecord(
            openTime,
            entryPrice,
            execution.firstFillTime(),
            execution.averagePrice(),
            execution.quantity(),
            pnl,
            win,
            meta.side(),
            meta.reason(),
            exitMeta.reason(),
            meta.signals(),
            exitMeta.signals(),
            entryFills,
            List.copyOf(execution.fills()),
            entryFees.add(execution.totalFee(), mc),
            entrySlippageBps.add(execution.slippageBps(), mc),
            entryQueueTimeMs.add(execution.averageQueueTimeMs(), mc).divide(BigDecimal.valueOf(2), mc),
            riskMultiple(pnl, execution.quantity()),
            entryExecutionType,
            execution.executionType()));
    basePosition = BigDecimal.ZERO;
    entryPrice = null;
    openTime = null;
    entryMetadata = null;
    entryFills = List.of();
    entryFees = BigDecimal.ZERO;
    entrySlippageBps = BigDecimal.ZERO;
    entryQueueTimeMs = BigDecimal.ZERO;
    entryRequestedQuantity = BigDecimal.ZERO;
    entryNotional = BigDecimal.ZERO;
  }

  public void mark(Instant time, BigDecimal price) {
    BigDecimal equity = quoteBalance;
    if (basePosition.compareTo(BigDecimal.ZERO) > 0) {
      equity = equity.add(price.multiply(basePosition, mc), mc);
    }
    equityCurve.add(new EquityPoint(time, equity));
  }

  public BigDecimal equity() {
    if (equityCurve.isEmpty()) {
      return quoteBalance;
    }
    return equityCurve.get(equityCurve.size() - 1).equity();
  }

  public boolean hasPosition() {
    return basePosition.compareTo(BigDecimal.ZERO) > 0;
  }

  public BigDecimal positionSize() {
    return basePosition;
  }

  public List<TradeRecord> trades() {
    return trades;
  }

  public List<EquityPoint> equityCurve() {
    return equityCurve;
  }

  public BigDecimal entryRequestedQuantity() {
    return entryRequestedQuantity;
  }

  private BigDecimal riskMultiple(BigDecimal pnl, BigDecimal quantity) {
    if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal risk = entryPrice == null ? BigDecimal.ZERO : entryPrice.multiply(quantity, mc);
    if (risk.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return pnl.divide(risk, mc);
  }

  public record TradeMetadata(SignalSide side, String reason, List<String> signals) {
    public TradeMetadata {
      signals = signals == null ? List.of() : List.copyOf(signals);
    }

    public static TradeMetadata empty(SignalSide side) {
      return new TradeMetadata(side, "", List.of());
    }
  }
}
