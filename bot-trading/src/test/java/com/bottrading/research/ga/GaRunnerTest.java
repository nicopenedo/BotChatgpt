package com.bottrading.research.ga;

import com.bottrading.research.backtest.BacktestEngine;
import com.bottrading.research.backtest.BacktestRequest;
import com.bottrading.research.backtest.BacktestResult;
import com.bottrading.research.backtest.EquityPoint;
import com.bottrading.research.backtest.ExecutionResult;
import com.bottrading.research.backtest.ExecutionStatistics;
import com.bottrading.research.backtest.MetricsSummary;
import com.bottrading.research.backtest.TradeRecord;
import com.bottrading.strategy.SignalSide;
import com.bottrading.strategy.CompositeStrategy;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GaRunnerTest {

  @Test
  void gaCompletesWithStubbedEvaluator() throws InterruptedException {
    BacktestEngine engine =
        new BacktestEngine(null, null, null, BigDecimal.valueOf(1000)) {
          @Override
          public BacktestResult run(
              BacktestRequest request, Path reportDirectory, CompositeStrategy override) {
            int signals = override.getSignals().size();
            BigDecimal score = BigDecimal.valueOf(signals + 1);
            MetricsSummary metrics =
                new MetricsSummary(
                    score,
                    score,
                    score,
                    score,
                    BigDecimal.ONE,
                    score,
                    BigDecimal.ONE,
                    score,
                    score,
                    1,
                    BigDecimal.ONE,
                    score,
                    score);
            return new BacktestResult(
                request,
                metrics,
                Collections.singletonList(
                    new TradeRecord(
                        Instant.now(),
                        BigDecimal.ONE,
                        Instant.now(),
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        true,
                        SignalSide.BUY,
                        "entry",
                        "exit",
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        ExecutionResult.ExecutionType.MARKET,
                        ExecutionResult.ExecutionType.MARKET)),
                Collections.singletonList(new EquityPoint(Instant.now(), score)),
                Collections.emptyList(),
                "hash",
                new ExecutionStatistics());
          }
        };

    BacktestRequest request =
        new BacktestRequest(
            "TEST",
            "1m",
            null,
            null,
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            false,
            null,
            "test",
            false,
            null,
            null);
    Evaluator evaluator = new Evaluator(engine, request, 1, Path.of("ga-test"), 0.0, 0);
    GaRunner runner = new GaRunner(evaluator, 4, 2, 0.1, 2, 1, 42L);
    Genome best = runner.run();
    Assertions.assertNotNull(best);
    Assertions.assertTrue(best.fitness() != Double.NEGATIVE_INFINITY);
  }
}
