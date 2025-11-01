package com.bottrading.bandit;

import java.util.List;

public class UcbTunedAlgorithm implements BanditAlgorithm {

  @Override
  public BanditArmEntity choose(List<BanditArmEntity> arms, BanditContext context) {
    double total =
        arms.stream().mapToDouble(arm -> Math.max(1.0, arm.getStats().getRewardObservations())).sum();
    double logTotal = Math.log(Math.max(Math.E, total));
    BanditArmEntity best = null;
    double bestScore = Double.NEGATIVE_INFINITY;
    for (BanditArmEntity arm : arms) {
      BanditArmStats stats = arm.getStats();
      double pulls = Math.max(1.0, stats.getRewardObservations());
      double mean = stats.getMean();
      double variance = stats.getVariance();
      double bonus =
          Math.sqrt(
              (logTotal / pulls)
                  * Math.min(0.25, variance + Math.sqrt(2 * logTotal / pulls)));
      double score = mean + bonus;
      if (stats.getRewardObservations() == 0) {
        score = Double.MAX_VALUE / 4;
      }
      if (score > bestScore) {
        bestScore = score;
        best = arm;
      }
    }
    return best;
  }

  @Override
  public String name() {
    return "UCB_TUNED";
  }
}
