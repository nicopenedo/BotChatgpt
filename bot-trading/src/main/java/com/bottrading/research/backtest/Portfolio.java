package com.bottrading.research.backtest;

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

  public Portfolio(BigDecimal startingCapital) {
    this.quoteBalance = startingCapital;
  }

  public void buy(Instant time, BigDecimal price, BigDecimal quantity, BigDecimal fee) {
    if (basePosition.compareTo(BigDecimal.ZERO) > 0) {
      return;
    }
    BigDecimal cost = price.multiply(quantity, mc).add(fee);
    quoteBalance = quoteBalance.subtract(cost, mc);
    basePosition = quantity;
    entryPrice = price;
    openTime = time;
  }

  public void sell(Instant time, BigDecimal price, BigDecimal quantity, BigDecimal fee) {
    if (basePosition.compareTo(BigDecimal.ZERO) <= 0) {
      return;
    }
    BigDecimal proceeds = price.multiply(quantity, mc).subtract(fee);
    quoteBalance = quoteBalance.add(proceeds, mc);
    BigDecimal pnl =
        price.subtract(entryPrice, mc).multiply(quantity, mc).subtract(fee, mc);
    trades.add(new TradeRecord(openTime, entryPrice, time, price, quantity, pnl, pnl.compareTo(BigDecimal.ZERO) > 0));
    basePosition = BigDecimal.ZERO;
    entryPrice = null;
    openTime = null;
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
}
