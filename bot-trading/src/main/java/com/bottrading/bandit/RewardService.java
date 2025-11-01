package com.bottrading.bandit;

import org.springframework.stereotype.Component;

@Component
public class RewardService {

  private final BanditProperties properties;

  public RewardService(BanditProperties properties) {
    this.properties = properties;
  }

  public RewardResult compute(Double pnlR, Double slippageBps, Double feesBps) {
    double normalized = pnlR != null ? pnlR : 0.0;
    double slippage = slippageBps != null ? slippageBps : 0.0;
    double fees = feesBps != null ? feesBps : 0.0;
    double cap = properties.getReward().getCapR();
    double clipped = Math.max(-cap, Math.min(cap, normalized));
    double penalty =
        properties.getReward().getSlippagePenaltyBps() * slippage
            + properties.getReward().getFeesPenaltyBps() * fees;
    double reward = clipped - penalty;
    return new RewardResult(reward, normalized, slippage, fees);
  }

  public record RewardResult(double reward, double pnlR, double slippageBps, double feesBps) {}
}
