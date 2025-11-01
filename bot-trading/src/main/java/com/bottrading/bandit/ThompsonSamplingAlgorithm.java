package com.bottrading.bandit;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ThompsonSamplingAlgorithm implements BanditAlgorithm {

  @Override
  public BanditArmEntity choose(List<BanditArmEntity> arms, BanditContext context) {
    BanditArmEntity best = null;
    double bestSample = Double.NEGATIVE_INFINITY;
    ThreadLocalRandom random = ThreadLocalRandom.current();
    for (BanditArmEntity arm : arms) {
      BanditArmStats stats = arm.getStats();
      double mean = stats.getMean();
      double variance = stats.getVariance();
      double explorationScale = variance > 0 ? Math.sqrt(variance / Math.max(1.0, stats.getEffectiveCount())) : 1.0;
      if (!Double.isFinite(explorationScale) || explorationScale <= 0) {
        explorationScale = 1.0;
      }
      if (stats.getRewardObservations() == 0) {
        explorationScale = 2.0;
        mean = 0;
      }
      double sample = mean + random.nextGaussian() * explorationScale;
      if (sample > bestSample) {
        bestSample = sample;
        best = arm;
      }
    }
    return best;
  }

  @Override
  public String name() {
    return "THOMPSON";
  }
}
