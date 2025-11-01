package com.bottrading.bandit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BanditArmStatsTest {

  @Test
  void updatesMeanAndVariance() {
    BanditArmStats stats = new BanditArmStats();
    Instant now = Instant.now();
    stats.recordReward(1.0, Duration.ofDays(21), now);
    stats.recordReward(2.0, Duration.ofDays(21), now.plusSeconds(1));

    assertThat(stats.getMean()).isCloseTo(1.5, within(1e-6));
    assertThat(stats.getVariance()).isCloseTo(0.25, within(1e-6));
    assertThat(stats.getRewardObservations()).isEqualTo(2);
  }

  @Test
  void appliesDecayWithHalfLife() {
    BanditArmStats stats = new BanditArmStats();
    Instant start = Instant.parse("2024-01-01T00:00:00Z");
    stats.recordReward(2.0, Duration.ofDays(10), start);
    double before = stats.getMean();
    stats.recordReward(2.0, Duration.ofDays(10), start.plus(Duration.ofDays(10)));
    double after = stats.getMean();
    assertThat(after).isCloseTo(before, within(1e-6));

    stats.recordReward(0.0, Duration.ofDays(10), start.plus(Duration.ofDays(20)));
    assertThat(stats.getMean()).isLessThan(before);
  }

  private static org.assertj.core.data.Offset<Double> within(double value) {
    return org.assertj.core.data.Offset.offset(value);
  }
}
