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
              base.slippageBps(),
              base.takerFeeBps(),
              base.makerFeeBps(),
              base.useCache()));
    }
    return segments;
  }

  public interface GaRunnerFactory {
    GaRunner create(BacktestRequest request);
  }
}
