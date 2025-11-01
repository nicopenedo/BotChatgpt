package com.bottrading.research.backtest.realistic;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Simple synthetic transaction cost analysis model. Provides an estimated slippage in basis points
 * using candle spread, time of day, volume and participation rate.
 */
public class SyntheticTcaEstimator {

  private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
  private final RealisticBacktestConfig.TcaConfig config;

  public SyntheticTcaEstimator(RealisticBacktestConfig.TcaConfig config) {
    this.config = config == null ? new RealisticBacktestConfig.TcaConfig() : config;
  }

  public BigDecimal estimate(
      BigDecimal spreadBps, BigDecimal qty, BigDecimal adv, BigDecimal volume, Instant time) {
    BigDecimal normalizedSpread = spreadBps == null ? BigDecimal.ZERO : spreadBps;
    BigDecimal participation = ratio(qty, adv);
    BigDecimal liquidity = ratio(qty, volume);
    BigDecimal hourComponent = hourFactor(time);

    BigDecimal slippage =
        normalizedSpread.multiply(config.spreadWeight(), MC)
            .add(participation.multiply(config.quantityWeight(), MC), MC)
            .add(liquidity.multiply(config.volumeWeight(), MC), MC)
            .add(hourComponent.multiply(config.hourWeight(), MC), MC);
    return slippage.max(BigDecimal.ZERO);
  }

  private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
    if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return numerator.divide(denominator, MC);
  }

  private BigDecimal hourFactor(Instant time) {
    if (time == null) {
      return BigDecimal.ZERO;
    }
    int hour = time.atZone(ZoneOffset.UTC).getHour();
    double centered = Math.abs(hour - 12) / 12.0;
    return BigDecimal.valueOf(centered);
  }
}
