package com.bottrading.bandit;

import com.bottrading.model.entity.PresetVersion;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PresetStatus;
import com.bottrading.repository.PresetVersionRepository;
import com.bottrading.research.regime.RegimeTrend;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BanditStore {

  private static final Logger log = LoggerFactory.getLogger(BanditStore.class);

  private final BanditArmRepository armRepository;
  private final BanditPullRepository pullRepository;
  private final PresetVersionRepository presetRepository;
  private final Clock clock;
  private final BanditProperties properties;

  public BanditStore(
      BanditArmRepository armRepository,
      BanditPullRepository pullRepository,
      PresetVersionRepository presetRepository,
      Clock clock,
      BanditProperties properties) {
    this.armRepository = armRepository;
    this.pullRepository = pullRepository;
    this.presetRepository = presetRepository;
    this.clock = clock;
    this.properties = properties;
  }

  @Transactional
  public List<BanditArmEntity> ensureArms(
      String symbol, RegimeTrend regime, OrderSide side, List<PresetVersion> candidates) {
    String regimeKey = regimeKey(regime);
    List<BanditArmEntity> arms = armRepository.findBySymbolAndRegimeAndSide(symbol, regimeKey, side);
    Map<UUID, BanditArmEntity> byPreset = new HashMap<>();
    for (BanditArmEntity arm : arms) {
      byPreset.put(arm.getPresetId(), arm);
    }

    List<BanditArmEntity> result = new ArrayList<>(arms);
    for (PresetVersion preset : candidates) {
      UUID presetId = preset.getId();
      BanditArmEntity arm = byPreset.get(presetId);
      if (arm == null) {
        arm = new BanditArmEntity();
        arm.setSymbol(symbol);
        arm.setRegime(regimeKey);
        arm.setSide(side);
        arm.setPresetId(presetId);
        arm.setRole(preset.getStatus() == PresetStatus.ACTIVE ? BanditArmRole.ACTIVE : BanditArmRole.CANDIDATE);
        arm.setStatus(BanditArmStatus.ELIGIBLE);
        arm = armRepository.save(arm);
        log.info("Created bandit arm {} for {} {} {}", arm.getId(), symbol, regimeKey, side);
        result.add(arm);
      } else {
        BanditArmRole expectedRole =
            preset.getStatus() == PresetStatus.ACTIVE ? BanditArmRole.ACTIVE : BanditArmRole.CANDIDATE;
        if (arm.getRole() != expectedRole) {
          arm.setRole(expectedRole);
          armRepository.save(arm);
        }
      }
    }
    return result;
  }

  public List<BanditArmEntity> listArms(String symbol, String regime, OrderSide side) {
    if (symbol == null || regime == null || side == null) {
      return armRepository.findAll();
    }
    return armRepository.findBySymbolAndRegimeAndSide(symbol, regime, side);
  }

  public Optional<BanditArmEntity> getArm(UUID id) {
    return armRepository.findById(id);
  }

  @Transactional
  public void updateStatus(UUID armId, BanditArmStatus status) {
    BanditArmEntity arm =
        armRepository
            .findById(armId)
            .orElseThrow(() -> new IllegalArgumentException("Bandit arm not found: " + armId));
    arm.setStatus(status);
    armRepository.save(arm);
  }

  @Transactional
  public void resetStats(String symbol, String regime, OrderSide side) {
    List<BanditArmEntity> arms = listArms(symbol, regime, side);
    for (BanditArmEntity arm : arms) {
      arm.getStats().reset();
      armRepository.save(arm);
    }
  }

  @Transactional
  public BanditPullEntity logPull(
      BanditArmEntity arm, Map<String, Object> context, String decisionId, Instant now) {
    Duration halfLife = properties.getDecay().asDuration();
    arm.getStats().registerPull(halfLife, now);
    armRepository.save(arm);

    BanditPullEntity pull = new BanditPullEntity();
    pull.setArm(arm);
    pull.setTimestamp(now);
    pull.setDecisionId(decisionId);
    pull.setContext(context);
    pull.setSymbol(arm.getSymbol());
    pull.setRegime(arm.getRegime());
    pull.setSide(arm.getSide());
    pull.setRole(arm.getRole());
    return pullRepository.save(pull);
  }

  @Transactional
  public void applyReward(
      BanditPullEntity pull, double reward, Double pnlR, Double slippage, Double fees) {
    BanditArmEntity arm = pull.getArm();
    Duration halfLife = properties.getDecay().asDuration();
    Instant now = Instant.now(clock);
    arm.getStats().recordReward(reward, halfLife, now);
    armRepository.save(arm);

    pull.setReward(reward);
    pull.setPnlR(pnlR);
    pull.setSlippageBps(slippage);
    pull.setFeesBps(fees);
    pullRepository.save(pull);
  }

  public Optional<BanditPullEntity> findPull(String decisionId) {
    return pullRepository.findByDecisionId(decisionId);
  }

  public List<BanditPullEntity> recentPulls(
      String symbol, String regime, OrderSide side, int limit) {
    return pullRepository.findBySymbolAndRegimeAndSideOrderByTimestampDesc(
        symbol, regime, side, PageRequest.of(0, Math.max(1, limit)));
  }

  public CanaryBudgetSnapshot canarySnapshot(String symbol, Instant reference) {
    Instant from = reference.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant to = from.plus(Duration.ofDays(1));
    long total = pullRepository.countBySymbolAndTimestampBetween(symbol, from, to);
    long candidates =
        pullRepository.countBySymbolAndRoleAndTimestampBetween(
            symbol, BanditArmRole.CANDIDATE, from, to);
    return new CanaryBudgetSnapshot(total, candidates, from.atZone(ZoneOffset.UTC).toLocalDate());
  }

  public String regimeKey(RegimeTrend regime) {
    return regime != null ? regime.name() : "UNKNOWN";
  }

  public List<PresetVersion> loadEligiblePresets(RegimeTrend regime, OrderSide side) {
    List<PresetVersion> presets = new ArrayList<>();
    presetRepository
        .findFirstByRegimeAndSideAndStatusOrderByActivatedAtDesc(regime, side, PresetStatus.ACTIVE)
        .ifPresent(presets::add);
    presets.addAll(presetRepository.findByRegimeAndSideAndStatusOrderByActivatedAtDesc(regime, side, PresetStatus.CANDIDATE));
    return presets;
  }

  public record CanaryBudgetSnapshot(long totalPulls, long candidatePulls, LocalDate day) {}
}
