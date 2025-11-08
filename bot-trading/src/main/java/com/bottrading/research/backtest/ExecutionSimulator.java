package com.bottrading.research.backtest;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

public class ExecutionSimulator {

  private final MathContext mc = new MathContext(12, RoundingMode.HALF_UP);
  private final BigDecimal slippageBps;
  private final BigDecimal takerFeeBps;
  private final BigDecimal makerFeeBps;

  public ExecutionSimulator(BigDecimal slippageBps, BigDecimal takerFeeBps, BigDecimal makerFeeBps) {
    this.slippageBps = slippageBps;
    this.takerFeeBps = takerFeeBps;
    this.makerFeeBps = makerFeeBps;
  }

  public ExecutionResult simulateBuy(BigDecimal price, BigDecimal quantity, boolean maker) {
    BigDecimal slipped = applySlippage(price, true);
    BigDecimal fee = fee(slipped.multiply(quantity, mc), maker);
    FillDetail fill =
        new FillDetail(
            null,
            slipped,
            quantity,
            0L,
            slippageValue(true),
            fee,
            maker);
    return new ExecutionResult(List.of(fill), quantity, false, ExecutionResult.ExecutionType.MARKET);
  }

  public ExecutionResult simulateSell(BigDecimal price, BigDecimal quantity, boolean maker) {
    BigDecimal slipped = applySlippage(price, false);
    BigDecimal fee = fee(slipped.multiply(quantity, mc), maker);
    FillDetail fill =
        new FillDetail(
            null,
            slipped,
            quantity,
            0L,
            slippageValue(false),
            fee,
            maker);
    return new ExecutionResult(List.of(fill), quantity, false, ExecutionResult.ExecutionType.MARKET);
  }

  private BigDecimal applySlippage(BigDecimal price, boolean buy) {
    if (slippageBps == null) {
      return price;
    }
    BigDecimal multiplier = slippageBps.divide(BigDecimal.valueOf(10000), mc);
    if (buy) {
      return price.multiply(BigDecimal.ONE.add(multiplier, mc), mc);
    }
    return price.multiply(BigDecimal.ONE.subtract(multiplier, mc), mc);
  }

  private BigDecimal fee(BigDecimal notional, boolean maker) {
    BigDecimal rate = maker ? makerFeeBps : takerFeeBps;
    if (rate == null) {
      return BigDecimal.ZERO;
    }
    return notional.multiply(rate, mc).divide(BigDecimal.valueOf(10000), mc);
  }

  private BigDecimal slippageValue(boolean buy) {
    if (slippageBps == null) {
      return BigDecimal.ZERO;
    }
    return buy ? slippageBps : slippageBps.negate();
  }
}
