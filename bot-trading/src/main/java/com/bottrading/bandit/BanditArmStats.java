package com.bottrading.bandit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Duration;
import java.time.Instant;

public class BanditArmStats {

  private double totalWeight;
  private double sumRewards;
  private double sumSquares;
  private long pulls;
  private long rewardObservations;
  private Instant lastUpdated;

  public double getTotalWeight() {
    return totalWeight;
  }

  public void setTotalWeight(double totalWeight) {
    this.totalWeight = totalWeight;
  }

  public double getSumRewards() {
    return sumRewards;
  }

  public void setSumRewards(double sumRewards) {
    this.sumRewards = sumRewards;
  }

  public double getSumSquares() {
    return sumSquares;
  }

  public void setSumSquares(double sumSquares) {
    this.sumSquares = sumSquares;
  }

  public long getPulls() {
    return pulls;
  }

  public void setPulls(long pulls) {
    this.pulls = pulls;
  }

  public long getRewardObservations() {
    return rewardObservations;
  }

  public void setRewardObservations(long rewardObservations) {
    this.rewardObservations = rewardObservations;
  }

  public Instant getLastUpdated() {
    return lastUpdated;
  }

  public void setLastUpdated(Instant lastUpdated) {
    this.lastUpdated = lastUpdated;
  }

  public void registerPull(Duration halfLife, Instant now) {
    applyDecay(halfLife, now);
    pulls += 1;
    lastUpdated = now;
  }

  public void recordReward(double reward, Duration halfLife, Instant now) {
    applyDecay(halfLife, now);
    totalWeight += 1.0d;
    sumRewards += reward;
    sumSquares += reward * reward;
    rewardObservations += 1;
    lastUpdated = now;
  }

  public void reset() {
    totalWeight = 0;
    sumRewards = 0;
    sumSquares = 0;
    pulls = 0;
    rewardObservations = 0;
    lastUpdated = null;
  }

  public void applyDecay(Duration halfLife, Instant now) {
    if (lastUpdated == null || halfLife == null) {
      return;
    }
    long seconds = Math.max(0, Duration.between(lastUpdated, now).getSeconds());
    if (seconds == 0) {
      return;
    }
    double halfLifeSeconds = Math.max(1, halfLife.getSeconds());
    double decay = Math.pow(0.5d, seconds / halfLifeSeconds);
    totalWeight *= decay;
    sumRewards *= decay;
    sumSquares *= decay;
    if (totalWeight < 1e-9) {
      totalWeight = 0;
      sumRewards = 0;
      sumSquares = 0;
    }
  }

  @JsonIgnore
  public double getMean() {
    if (totalWeight <= 0) {
      return 0;
    }
    return sumRewards / totalWeight;
  }

  @JsonIgnore
  public double getVariance() {
    if (totalWeight <= 0) {
      return 0;
    }
    double mean = getMean();
    double variance = sumSquares / totalWeight - mean * mean;
    if (variance < 0 && variance > -1e-9) {
      return 0;
    }
    return Math.max(0, variance);
  }

  @JsonIgnore
  public double getStdError() {
    if (rewardObservations <= 1) {
      return Double.POSITIVE_INFINITY;
    }
    return Math.sqrt(getVariance() / Math.max(1, totalWeight));
  }

  @JsonIgnore
  public double getEffectiveCount() {
    return totalWeight;
  }
}
