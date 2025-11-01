package com.bottrading.research.backtest;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Simple execution model that applies a fixed slippage and fee schedule. Used as fallback when
 * the realistic model is not requested.
 */
public class SimpleExecutionSimulator {

  private final MathContext mc = new MathContext(12, RoundingMode.HALF_UP);
  private final BigDecimal slippageBps;
  private final BigDecimal takerFeeBps;
  private final BigDecimal makerFeeBps;

  public SimpleExecutionSimulator(
      BigDecimal slippageBps, BigDecimal takerFeeBps, BigDecimal makerFeeBps) {
    this.slippageBps = slippageBps == null ? BigDecimal.ZERO : slippageBps;
    this.takerFeeBps = takerFeeBps == null ? BigDecimal.ZERO : takerFeeBps;
    this.makerFeeBps = makerFeeBps == null ? BigDecimal.ZERO : makerFeeBps;
  }

  public ExecutionResult simulateBuy(BigDecimal price, BigDecimal quantity, boolean maker) {
    BigDecimal slipped = applySlippage(price, true);
    BigDecimal fee = fee(slipped.multiply(quantity, mc), maker);
    return ExecutionResult.simpleFill(Instant.now(), slipped, quantity, fee, slippageBps, maker,
        maker ? ExecutionResult.ExecutionType.LIMIT : ExecutionResult.ExecutionType.MARKET);
  }

  public ExecutionResult simulateSell(BigDecimal price, BigDecimal quantity, boolean maker) {
    BigDecimal slipped = applySlippage(price, false);
    BigDecimal fee = fee(slipped.multiply(quantity, mc), maker);
    return ExecutionResult.simpleFill(Instant.now(), slipped, quantity, fee, slippageBps, maker,
        maker ? ExecutionResult.ExecutionType.LIMIT : ExecutionResult.ExecutionType.MARKET);
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
}
