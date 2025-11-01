package com.bottrading.bandit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.model.enums.OrderSide;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class BanditAlgorithmTest {

  private static final BanditContext CONTEXT = BanditContext.builder().build();

  @Test
  void ucb1PrefersHigherMean() {
    BanditArmEntity strong = arm(1.2, 0.05, 50);
    BanditArmEntity weak = arm(0.2, 0.05, 50);
    BanditAlgorithm algo = new Ucb1Algorithm();
    BanditArmEntity chosen = algo.choose(List.of(strong, weak), CONTEXT);
    assertThat(chosen).isSameAs(strong);
  }

  @Test
  void ucbTunedPrefersHigherMean() {
    BanditArmEntity strong = arm(1.2, 0.02, 80);
    BanditArmEntity weak = arm(0.2, 0.02, 80);
    BanditAlgorithm algo = new UcbTunedAlgorithm();
    BanditArmEntity chosen = algo.choose(List.of(strong, weak), CONTEXT);
    assertThat(chosen).isSameAs(strong);
  }

  @RepeatedTest(10)
  void thompsonSamplingSkewsTowardsBestArm() {
    BanditArmEntity strong = arm(1.5, 0.01, 200);
    BanditArmEntity weak = arm(-0.5, 0.01, 200);
    BanditAlgorithm algo = new ThompsonSamplingAlgorithm();
    BanditArmEntity chosen = algo.choose(Arrays.asList(strong, weak), CONTEXT);
    assertThat(chosen).isSameAs(strong);
  }

  private BanditArmEntity arm(double mean, double variance, long observations) {
    BanditArmEntity arm = new BanditArmEntity();
    arm.setSymbol("BTCUSDT");
    arm.setRegime("UP");
    arm.setSide(OrderSide.BUY);
    arm.setPresetId(java.util.UUID.randomUUID());
    BanditArmStats stats = new BanditArmStats();
    double weight = observations;
    stats.setTotalWeight(weight);
    stats.setSumRewards(mean * weight);
    stats.setSumSquares((variance + mean * mean) * weight);
    stats.setRewardObservations(observations);
    stats.setPulls(observations);
    stats.setLastUpdated(Instant.now());
    arm.setStats(stats);
    return arm;
  }
}
