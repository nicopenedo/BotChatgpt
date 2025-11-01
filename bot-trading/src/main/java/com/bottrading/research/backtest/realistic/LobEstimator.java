package com.bottrading.research.backtest.realistic;

import com.bottrading.model.dto.Kline;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Estimates spread and depth for the simplified top-of-book model. */
public class LobEstimator {

  private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

  private final RealisticBacktestConfig.LobConfig config;

  public LobEstimator(RealisticBacktestConfig.LobConfig config) {
    this.config = config == null ? new RealisticBacktestConfig.LobConfig() : config;
  }

  public TopOfBookSnapshot snapshot(String symbol, Kline kline) {
    BigDecimal spreadBps = computeSpreadBps(kline);
    RealisticBacktestConfig.DepthParameters params =
        config.depthPerSymbol().getOrDefault(symbol, new RealisticBacktestConfig.DepthParameters());
    BigDecimal depth = computeDepth(kline, params);
    return new TopOfBookSnapshot(spreadBps, depth, params.advQty());
  }

  private BigDecimal computeSpreadBps(Kline kline) {
    BigDecimal high = kline.high();
    BigDecimal low = kline.low();
    BigDecimal mid = kline.close();
    if (mid.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ONE;
    }
    BigDecimal range = high.subtract(low, MC);
    BigDecimal spread = range.divide(BigDecimal.valueOf(10), MC);
    return spread.divide(mid, MC).multiply(BigDecimal.valueOf(10000), MC).max(BigDecimal.ONE);
  }

  private BigDecimal computeDepth(Kline kline, RealisticBacktestConfig.DepthParameters params) {
    BigDecimal volume = kline.volume();
    BigDecimal baseDepth = params.baseDepth();
    if (volume == null) {
      return baseDepth;
    }
    BigDecimal adv = params.advQty();
    if (adv.compareTo(BigDecimal.ZERO) == 0) {
      return baseDepth;
    }
    BigDecimal liquidityFactor = volume.divide(adv, MC).add(BigDecimal.ONE, MC);
    if (config.depthModel() == RealisticBacktestConfig.DepthModel.CALIBRATED) {
      BigDecimal exponent = params.shape();
      double pow = Math.pow(liquidityFactor.doubleValue(), exponent.doubleValue());
      return baseDepth.multiply(BigDecimal.valueOf(pow), MC);
    }
    return baseDepth.multiply(liquidityFactor, MC);
  }

  public record TopOfBookSnapshot(BigDecimal spreadBps, BigDecimal depth, BigDecimal adv) {}
}
