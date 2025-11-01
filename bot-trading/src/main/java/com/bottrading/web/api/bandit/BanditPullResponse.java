package com.bottrading.web.api.bandit;

import com.bottrading.bandit.BanditPullEntity;
import java.time.Instant;
import java.util.Map;

public record BanditPullResponse(
    long id,
    String armId,
    Instant timestamp,
    String decisionId,
    Map<String, Object> context,
    Double reward,
    Double pnlR,
    Double slippageBps,
    Double feesBps,
    String symbol,
    String regime,
    String side,
    String role) {

  public static BanditPullResponse from(BanditPullEntity entity) {
    return new BanditPullResponse(
        entity.getId(),
        entity.getArm() != null ? entity.getArm().getId().toString() : null,
        entity.getTimestamp(),
        entity.getDecisionId(),
        entity.getContext(),
        entity.getReward(),
        entity.getPnlR(),
        entity.getSlippageBps(),
        entity.getFeesBps(),
        entity.getSymbol(),
        entity.getRegime(),
        entity.getSide() != null ? entity.getSide().name() : null,
        entity.getRole() != null ? entity.getRole().name() : null);
  }
}
