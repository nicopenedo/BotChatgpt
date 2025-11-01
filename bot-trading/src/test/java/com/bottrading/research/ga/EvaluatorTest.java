package com.bottrading.research.ga;

import com.bottrading.research.backtest.BacktestEngine;
import com.bottrading.research.backtest.BacktestRequest;
import com.bottrading.research.backtest.BacktestResult;
import com.bottrading.research.backtest.ExecutionStatistics;
import com.bottrading.research.backtest.MetricsSummary;
import com.bottrading.research.regime.RegimeFilter;
import com.bottrading.research.regime.RegimeLabel;
import com.bottrading.research.regime.RegimeLabelSet;
import com.bottrading.research.regime.RegimeTrend;
import com.bottrading.research.regime.RegimeVolatility;
import com.bottrading.strategy.CompositeStrategy;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EvaluatorTest {

  @Test
  void appliesRegimeMaskToMetrics() throws InterruptedException {
    Instant start = Instant.parse("2024-01-01T00:00:00Z");
    List<RegimeLabel> labels =
        List.of(
            new RegimeLabel(start.plusSeconds(60), RegimeTrend.UP, RegimeVolatility.LO),
            new RegimeLabel(start.plusSeconds(120), RegimeTrend.UP, RegimeVolatility.LO),
            new RegimeLabel(start.plusSeconds(180), RegimeTrend.DOWN, RegimeVolatility.LO),
            new RegimeLabel(start.plusSeconds(240), RegimeTrend.DOWN, RegimeVolatility.LO));
    RegimeLabelSet labelSet = new RegimeLabelSet(labels);

    BacktestEngine engine =
        new BacktestEngine(null, null, null, BigDecimal.ONE) {
          @Override
          public BacktestResult run(BacktestRequest request, Path reportDirectory, CompositeStrategy override) {
            long allowed =
                request.regimeFilter() != null && request.regimeFilter().isActive()
                    ? request.regimeFilter().count(request.from(), request.to())
                    : labels.size();
            BigDecimal value = BigDecimal.valueOf(allowed);
            MetricsSummary metrics =
                new MetricsSummary(
                    value,
                    value,
                    value,
                    value,
                    value,
                    value,
                    value,
                    value,
                    value,
                    (int) allowed,
                    value,
                    value,
                    value);
            return new BacktestResult(
                request, metrics, List.of(), List.of(), List.of(), "hash", new ExecutionStatistics());
          }
        };

    BacktestRequest requestAll =
        new BacktestRequest(
            "TEST",
            "1m",
            start,
            start.plusSeconds(300),
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            false,
            null,
            "run",
            false,
            null,
            null);

    Genome baseGenome = new Genome(new Random(1));
    Evaluator evaluatorAll = new Evaluator(engine, requestAll, 1, Path.of("reports"), 0.0, 0);
    evaluatorAll.evaluate(List.of(baseGenome));
    Assertions.assertEquals(4, baseGenome.metrics().profitFactor().intValue());

    RegimeFilter upFilter = new RegimeFilter(RegimeTrend.UP, labelSet);
    BacktestRequest requestUp =
        new BacktestRequest(
            "TEST",
            "1m",
            start,
            start.plusSeconds(300),
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            false,
            null,
            "run",
            false,
            upFilter,
            null);
    Genome upGenome = new Genome(new Random(2));
    Evaluator evaluatorUp = new Evaluator(engine, requestUp, 1, Path.of("reports"), 0.0, 0);
    evaluatorUp.evaluate(List.of(upGenome));
    Assertions.assertEquals(2, upGenome.metrics().profitFactor().intValue());
  }
}
