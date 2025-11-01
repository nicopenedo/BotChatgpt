package com.bottrading.research.ga;

import com.bottrading.research.backtest.BacktestRequest;
import java.util.ArrayList;
import java.util.List;

public class WalkForwardOptimizer {

  private final List<BacktestRequest> windows;
  private final GaRunnerFactory factory;

  public WalkForwardOptimizer(List<BacktestRequest> windows, GaRunnerFactory factory) {
    this.windows = windows;
    this.factory = factory;
  }

  public Genome optimize() throws InterruptedException {
    Genome champion = null;
    for (BacktestRequest request : windows) {
      GaRunner runner = factory.create(request);
      Genome candidate = runner.run();
      if (champion == null || candidate.fitness() > champion.fitness()) {
        champion = candidate;
      }
    }
    return champion;
  }

  public static List<BacktestRequest> split(BacktestRequest base, int trainDays, int validationDays, int testDays) {
    List<BacktestRequest> segments = new ArrayList<>();
    if (base.from() == null || base.to() == null) {
      segments.add(base);
      return segments;
    }
    long totalDays = java.time.Duration.between(base.from(), base.to()).toDays();
    long windowSize = trainDays + validationDays + testDays;
    long steps = Math.max(1, totalDays / windowSize);
    for (int i = 0; i < steps; i++) {
      java.time.Instant start = base.from().plus(java.time.Duration.ofDays(i * windowSize));
      java.time.Instant trainEnd = start.plus(java.time.Duration.ofDays(trainDays));
      java.time.Instant validationEnd = trainEnd.plus(java.time.Duration.ofDays(validationDays));
      java.time.Instant end = validationEnd.plus(java.time.Duration.ofDays(testDays));
      if (end.isAfter(base.to())) {
        end = base.to();
      }
      segments.add(
          new BacktestRequest(
              base.symbol(),
              base.interval(),
              start,
              end,
              base.strategyConfig(),
              base.genomesConfig(),
              base.slippageBps(),
              base.takerFeeBps(),
              base.makerFeeBps(),
              base.useDynamicFees(),
              base.seed(),
              base.runId(),
              base.useCache(),
              base.regimeFilter(),
              base.realisticConfig()));
    }
    return segments;
  }

  public static List<BacktestRequest> splitByRegime(
      BacktestRequest base,
      int trainDays,
      int validationDays,
      int testDays,
      com.bottrading.research.regime.RegimeFilter regimeFilter,
      int minSamples) {
    if (regimeFilter == null || !regimeFilter.isActive()) {
      return split(base, trainDays, validationDays, testDays);
    }
    if (base.from() == null || base.to() == null) {
      return List.of(base);
    }
    List<BacktestRequest> segments = new ArrayList<>();
    List<BacktestRequest> windows = split(base, trainDays, validationDays, testDays);
    int index = 0;
    for (BacktestRequest request : windows) {
      java.time.Instant trainEnd = request.from().plus(java.time.Duration.ofDays(trainDays));
      if (trainEnd.isAfter(request.to())) {
        trainEnd = request.to();
      }
      java.time.Instant validationEnd = trainEnd.plus(java.time.Duration.ofDays(validationDays));
      if (validationEnd.isAfter(request.to())) {
        validationEnd = request.to();
      }
      long trainCount = regimeFilter.count(request.from(), trainEnd);
      long validationCount = regimeFilter.count(trainEnd, validationEnd);
      long testCount = regimeFilter.count(validationEnd, request.to());
      if ((minSamples > 0 && (trainCount < minSamples || validationCount < minSamples || testCount < minSamples))
          || trainCount == 0
          || validationCount == 0
          || testCount == 0) {
        continue;
      }
      segments.add(
          new BacktestRequest(
              request.symbol(),
              request.interval(),
              request.from(),
              request.to(),
              request.strategyConfig(),
              request.genomesConfig(),
              request.slippageBps(),
              request.takerFeeBps(),
              request.makerFeeBps(),
              request.useDynamicFees(),
              request.seed(),
              request.runId() + "-wf" + index++,
              request.useCache(),
              regimeFilter,
              request.realisticConfig()));
    }
    return segments;
  }

  public interface GaRunnerFactory {
    GaRunner create(BacktestRequest request);
  }
}
