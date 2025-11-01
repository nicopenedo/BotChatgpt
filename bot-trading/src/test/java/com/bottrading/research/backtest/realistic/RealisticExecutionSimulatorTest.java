package com.bottrading.research.backtest.realistic;

import com.bottrading.model.dto.Kline;
import com.bottrading.research.backtest.ExecutionResult;
import com.bottrading.strategy.SignalSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RealisticExecutionSimulatorTest {

  @Test
  void limitFallsBackToMarketWhenTtlExpires() {
    RealisticBacktestConfig config = new RealisticBacktestConfig();
    config.execution().limit().setFallbackToMarket(true);
    config.execution().setMode(RealisticBacktestConfig.ExecutionMode.LIMIT);
    config.execution().limit().setLimitTtl(1000);
    config.execution().limit().setLimitBuffer(BigDecimal.valueOf(50));
    List<Kline> klines = candles(Instant.parse("2024-01-01T00:00:00Z"), 5, BigDecimal.valueOf(100));
    RealisticExecutionSimulator simulator =
        new RealisticExecutionSimulator("BTCUSDT", config, BigDecimal.ZERO, BigDecimal.valueOf(5), 42L);
    ExecutionResult result =
        simulator.executeEntry(SignalSide.BUY, klines, 0, BigDecimal.valueOf(1));

    Assertions.assertTrue(result.ttlExpired());
    Assertions.assertEquals(BigDecimal.valueOf(1), result.quantity());
    Assertions.assertEquals(ExecutionResult.ExecutionType.LIMIT, result.executionType());
    Assertions.assertFalse(result.fills().isEmpty());
    Assertions.assertFalse(result.fills().get(0).maker());
  }

  @Test
  void twapProducesExpectedSlices() {
    RealisticBacktestConfig config = new RealisticBacktestConfig();
    config.execution().twap().setTwapSlices(4);
    List<Kline> klines = candles(Instant.parse("2024-01-01T00:00:00Z"), 10, BigDecimal.valueOf(200));
    RealisticExecutionSimulator simulator =
        new RealisticExecutionSimulator("BTCUSDT", config, BigDecimal.ZERO, BigDecimal.ONE, 7L);
    ExecutionResult result =
        simulator.executeExit(SignalSide.SELL, klines, 0, BigDecimal.valueOf(4));

    Assertions.assertEquals(4, result.fills().size());
    Assertions.assertEquals(ExecutionResult.ExecutionType.TWAP, result.executionType());
    Assertions.assertEquals(BigDecimal.valueOf(4), result.quantity());
  }

  @Test
  void povAdjustsParticipationByVolume() {
    RealisticBacktestConfig config = new RealisticBacktestConfig();
    config.execution().pov().setPovTarget(BigDecimal.valueOf(0.2));
    List<Kline> klines = candles(Instant.parse("2024-01-01T00:00:00Z"), 6, BigDecimal.valueOf(150));
    RealisticExecutionSimulator simulator =
        new RealisticExecutionSimulator("BTCUSDT", config, BigDecimal.ZERO, BigDecimal.ONE, 11L);
    ExecutionResult result =
        simulator.executeExit(SignalSide.SELL, klines, 0, BigDecimal.valueOf(10));

    Assertions.assertTrue(result.fills().size() >= 2);
    Assertions.assertEquals(ExecutionResult.ExecutionType.POV, result.executionType());
    Assertions.assertEquals(BigDecimal.valueOf(10), result.quantity());
  }

  @Test
  void slippageIncreasesWithSpreadAndHour() {
    RealisticBacktestConfig config = new RealisticBacktestConfig();
    SyntheticTcaEstimator estimator = new SyntheticTcaEstimator(config.tca());
    BigDecimal base =
        estimator.estimate(
            BigDecimal.valueOf(5),
            BigDecimal.valueOf(1),
            BigDecimal.valueOf(1000),
            BigDecimal.valueOf(500),
            Instant.parse("2024-01-01T02:00:00Z"));
    BigDecimal higherSpread =
        estimator.estimate(
            BigDecimal.valueOf(10),
            BigDecimal.valueOf(1),
            BigDecimal.valueOf(1000),
            BigDecimal.valueOf(500),
            Instant.parse("2024-01-01T02:00:00Z"));
    BigDecimal peakHour =
        estimator.estimate(
            BigDecimal.valueOf(10),
            BigDecimal.valueOf(1),
            BigDecimal.valueOf(1000),
            BigDecimal.valueOf(500),
            Instant.parse("2024-01-01T12:00:00Z"));

    Assertions.assertTrue(higherSpread.compareTo(base) > 0);
    Assertions.assertTrue(peakHour.compareTo(higherSpread) >= 0);
  }

  private List<Kline> candles(Instant start, int count, BigDecimal price) {
    List<Kline> list = new ArrayList<>();
    Instant cursor = start;
    for (int i = 0; i < count; i++) {
      Kline kline =
          new Kline(
              cursor,
              cursor.plusSeconds(60),
              price,
              price.add(BigDecimal.ONE),
              price.subtract(BigDecimal.ONE),
              price,
              BigDecimal.valueOf(100 + i));
      list.add(kline);
      cursor = cursor.plusSeconds(60);
    }
    return list;
  }
}
