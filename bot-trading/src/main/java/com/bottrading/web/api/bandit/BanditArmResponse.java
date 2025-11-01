package com.bottrading.web.api.bandit;

import com.bottrading.bandit.BanditArmEntity;
import com.bottrading.bandit.BanditArmStats;
import java.time.Instant;
import java.util.UUID;

public record BanditArmResponse(
    UUID id,
    String symbol,
    String regime,
    String side,
    UUID presetId,
    String status,
    String role,
    Stats stats,
    Instant updatedAt) {

  public static BanditArmResponse from(BanditArmEntity entity) {
    BanditArmStats stats = entity.getStats();
    Stats statsDto =
        new Stats(
            stats.getPulls(),
            stats.getRewardObservations(),
            stats.getMean(),
            stats.getVariance(),
            stats.getEffectiveCount());
    return new BanditArmResponse(
        entity.getId(),
        entity.getSymbol(),
        entity.getRegime(),
        entity.getSide().name(),
        entity.getPresetId(),
        entity.getStatus().name(),
        entity.getRole().name(),
        statsDto,
        entity.getUpdatedAt());
  }

  public record Stats(long pulls, long observations, double mean, double variance, double effectiveCount) {}
}
