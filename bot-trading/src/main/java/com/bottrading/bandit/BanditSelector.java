package com.bottrading.bandit;

import com.bottrading.model.entity.PresetVersion;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.research.regime.Regime;
import com.bottrading.research.regime.RegimeTrend;
import com.bottrading.service.risk.RiskGuard;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BanditSelector {

  private static final Logger log = LoggerFactory.getLogger(BanditSelector.class);

  private final BanditProperties properties;
  private final BanditStore store;
  private final CanaryBudgetManager canaryBudgetManager;
  private final BanditMetrics metrics;
  private final RewardService rewardService;
  private final RiskGuard riskGuard;
  private final Clock clock;
  private final Map<BanditProperties.Algorithm, BanditAlgorithm> algorithms;

  public BanditSelector(
      BanditProperties properties,
      BanditStore store,
      CanaryBudgetManager canaryBudgetManager,
      BanditMetrics metrics,
      RewardService rewardService,
      RiskGuard riskGuard,
      Clock clock) {
    this.properties = properties;
    this.store = store;
    this.canaryBudgetManager = canaryBudgetManager;
    this.metrics = metrics;
    this.rewardService = rewardService;
    this.riskGuard = riskGuard;
    this.clock = clock;
    this.algorithms = new EnumMap<>(BanditProperties.Algorithm.class);
    algorithms.put(BanditProperties.Algorithm.THOMPSON, new ThompsonSamplingAlgorithm());
    algorithms.put(BanditProperties.Algorithm.UCB1, new Ucb1Algorithm());
    algorithms.put(BanditProperties.Algorithm.UCB_TUNED, new UcbTunedAlgorithm());
  }

  public BanditSelectionResult pickPresetOrFallback(
      String symbol, Regime regime, OrderSide side, BanditContext context) {
    if (!properties.isEnabled()) {
      return BanditSelectionResult.disabled();
    }
    if (!riskGuard.canOpen(symbol)) {
      metrics.incrementBlocked("risk_guard");
      return BanditSelectionResult.blocked();
    }
    RegimeTrend trend = regime != null ? regime.trend() : null;
    List<PresetVersion> presets = store.loadEligiblePresets(trend, side);
    if (presets.isEmpty()) {
      metrics.incrementBlocked("no_presets");
      return BanditSelectionResult.blocked();
    }
    List<BanditArmEntity> arms = store.ensureArms(symbol, trend, side, presets);
    List<BanditArmEntity> eligible = filterEligible(symbol, arms);
    if (eligible.isEmpty()) {
      metrics.incrementBlocked("no_arms");
      return BanditSelectionResult.blocked();
    }

    BanditAlgorithm algorithm = algorithms.getOrDefault(properties.getAlgorithm(), new ThompsonSamplingAlgorithm());
    metrics.registerAlgorithm(algorithm.name());
    BanditArmEntity chosen = algorithm.choose(eligible, context);
    if (chosen == null) {
      metrics.incrementBlocked("algo_no_choice");
      return BanditSelectionResult.blocked();
    }
    String decisionId = UUID.randomUUID().toString();
    Instant now = Instant.now(clock);
    store.logPull(chosen, context.features(), decisionId, now);
    metrics.incrementPull(chosen);

    BanditStore.CanaryBudgetSnapshot snapshot = store.canarySnapshot(symbol, now);
    double share = snapshot.totalPulls() == 0 ? 0.0 : (double) snapshot.candidatePulls() / snapshot.totalPulls();
    metrics.updateCanaryShare(symbol, share);
    canaryBudgetManager.registerPull(symbol, chosen.getRole() == BanditArmRole.CANDIDATE);

    return BanditSelectionResult.selected(
        new BanditSelection(
            chosen.getId(),
            chosen.getPresetId(),
            chosen.getRole(),
            decisionId,
            context.features(),
            null));
  }

  public void update(String decisionId, Double pnlR, Double slippageBps, Double feesBps) {
    if (!properties.isEnabled()) {
      return;
    }
    store
        .findPull(decisionId)
        .ifPresentOrElse(
            pull -> {
              RewardService.RewardResult reward = rewardService.compute(pnlR, slippageBps, feesBps);
              store.applyReward(pull, reward.reward(), reward.pnlR(), reward.slippageBps(), reward.feesBps());
              metrics.recordReward(pull.getArm(), reward.reward());
            },
            () -> log.warn("Bandit decision {} not found for reward update", decisionId));
  }

  private List<BanditArmEntity> filterEligible(String symbol, List<BanditArmEntity> arms) {
    List<BanditArmEntity> eligible = new ArrayList<>();
    boolean canSelectCandidate = canaryBudgetManager.canSelectCandidate(symbol);
    for (BanditArmEntity arm : arms) {
      if (arm.getStatus() != BanditArmStatus.ELIGIBLE) {
        continue;
      }
      if (arm.getRole() == BanditArmRole.CANDIDATE && !canSelectCandidate) {
        continue;
      }
      if (arm.getStats().getPulls() < properties.getMinSamplesToCompete()
          && arm.getRole() == BanditArmRole.CANDIDATE) {
        continue;
      }
      eligible.add(arm);
    }
    return eligible;
  }

  public record BanditSelectionResult(BanditSelection selection, boolean eligible) {
    private static BanditSelectionResult disabled() {
      return new BanditSelectionResult(null, false);
    }

    private static BanditSelectionResult blocked() {
      return new BanditSelectionResult(null, false);
    }

    private static BanditSelectionResult selected(BanditSelection selection) {
      return new BanditSelectionResult(selection, true);
    }
  }
}
