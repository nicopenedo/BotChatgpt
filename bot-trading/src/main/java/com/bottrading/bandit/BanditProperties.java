package com.bottrading.bandit;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bandit")
public class BanditProperties {

  private boolean enabled = false;
  private Algorithm algorithm = Algorithm.THOMPSON;
  private Reward reward = new Reward();
  private Canary canary = new Canary();
  private int minSamplesToCompete = 30;
  private Decay decay = new Decay();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Algorithm getAlgorithm() {
    return algorithm;
  }

  public void setAlgorithm(Algorithm algorithm) {
    this.algorithm = algorithm;
  }

  public Reward getReward() {
    return reward;
  }

  public void setReward(Reward reward) {
    this.reward = reward;
  }

  public Canary getCanary() {
    return canary;
  }

  public void setCanary(Canary canary) {
    this.canary = canary;
  }

  public int getMinSamplesToCompete() {
    return minSamplesToCompete;
  }

  public void setMinSamplesToCompete(int minSamplesToCompete) {
    this.minSamplesToCompete = minSamplesToCompete;
  }

  public Decay getDecay() {
    return decay;
  }

  public void setDecay(Decay decay) {
    this.decay = decay;
  }

  public enum Algorithm {
    THOMPSON,
    UCB1,
    UCB_TUNED
  }

  public static class Reward {

    private Metric metric = Metric.PNL_R;
    private double capR = 3.0;
    private double slippagePenaltyBps = 0.30;
    private double feesPenaltyBps = 0.20;

    public Metric getMetric() {
      return metric;
    }

    public void setMetric(Metric metric) {
      this.metric = metric;
    }

    public double getCapR() {
      return capR;
    }

    public void setCapR(double capR) {
      this.capR = capR;
    }

    public double getSlippagePenaltyBps() {
      return slippagePenaltyBps;
    }

    public void setSlippagePenaltyBps(double slippagePenaltyBps) {
      this.slippagePenaltyBps = slippagePenaltyBps;
    }

    public double getFeesPenaltyBps() {
      return feesPenaltyBps;
    }

    public void setFeesPenaltyBps(double feesPenaltyBps) {
      this.feesPenaltyBps = feesPenaltyBps;
    }

    public enum Metric {
      PNL_R,
      EXPECTANCY,
      SHARPE_LIKE
    }
  }

  public static class Canary {

    private int maxTradesPerDay = 20;
    private double maxSharePctPerDay = 0.25;

    public int getMaxTradesPerDay() {
      return maxTradesPerDay;
    }

    public void setMaxTradesPerDay(int maxTradesPerDay) {
      this.maxTradesPerDay = maxTradesPerDay;
    }

    public double getMaxSharePctPerDay() {
      return maxSharePctPerDay;
    }

    public void setMaxSharePctPerDay(double maxSharePctPerDay) {
      this.maxSharePctPerDay = maxSharePctPerDay;
    }
  }

  public static class Decay {

    private double halfLifeDays = 21.0;

    public double getHalfLifeDays() {
      return halfLifeDays;
    }

    public void setHalfLifeDays(double halfLifeDays) {
      this.halfLifeDays = halfLifeDays;
    }

    @NotNull
    public Duration asDuration() {
      return Duration.ofSeconds((long) Math.max(1, halfLifeDays * 24 * 3600));
    }
  }
}
