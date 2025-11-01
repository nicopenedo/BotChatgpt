package com.bottrading.research.ga;

import com.bottrading.research.backtest.BacktestEngine;
import com.bottrading.research.backtest.BacktestRequest;
import com.bottrading.research.backtest.BacktestResult;
import com.bottrading.research.backtest.MetricsSummary;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Evaluator {

  private final BacktestEngine engine;
  private final BacktestRequest request;
  private final int maxWorkers;
  private final Path reportsDir;
  private final double complexityPenalty;
  private final int minTrades;

  public Evaluator(
      BacktestEngine engine,
      BacktestRequest request,
      int maxWorkers,
      Path reportsDir,
      double complexityPenalty,
      int minTrades) {
    this.engine = engine;
    this.request = request;
    this.maxWorkers = Math.max(1, maxWorkers);
    this.reportsDir = reportsDir;
    this.complexityPenalty = Math.max(0, complexityPenalty);
    this.minTrades = Math.max(0, minTrades);
  }

  public void evaluate(List<Genome> genomes) throws InterruptedException {
    ExecutorService executor = Executors.newFixedThreadPool(maxWorkers);
    List<Future<?>> futures = new ArrayList<>();
    for (Genome genome : genomes) {
      futures.add(
          executor.submit(
              () -> {
                try {
                  BacktestResult result = engine.run(request, null, genome.toStrategy());
                  MetricsSummary metrics = result.metrics();
                  genome.metrics(metrics);
                  genome.fitness(applyPenalties(genome, metrics));
                } catch (IOException ex) {
                  genome.fitness(Double.NEGATIVE_INFINITY);
                }
              }));
    }
    for (Future<?> future : futures) {
      try {
        future.get();
      } catch (ExecutionException ex) {
        // ignore individual failures
      }
    }
    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.HOURS);
  }

  private double applyPenalties(Genome genome, MetricsSummary metrics) {
    if (metrics == null) {
      return Double.NEGATIVE_INFINITY;
    }
    if (metrics.trades() < minTrades) {
      return Double.NEGATIVE_INFINITY;
    }
    double base = score(metrics);
    double penalty = complexityPenalty * genome.activeSignals();
    return base - penalty;
  }

  private double score(MetricsSummary metrics) {
    double pf = metrics.profitFactor().doubleValue();
    double cagr = metrics.cagr().doubleValue();
    double sharpe = metrics.sharpe().doubleValue();
    double dd = metrics.maxDrawdown().doubleValue();
    return (pf * 2.0) + sharpe + cagr - dd;
  }
}
