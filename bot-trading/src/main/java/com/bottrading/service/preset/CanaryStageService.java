package com.bottrading.service.preset;

import com.bottrading.config.PresetsProperties;
import com.bottrading.model.entity.PresetCanaryState;
import com.bottrading.model.entity.PresetVersion;
import com.bottrading.model.entity.ShadowPositionEntity;
import com.bottrading.model.enums.CanaryStatus;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PositionStatus;
import com.bottrading.model.enums.PresetActivationMode;
import com.bottrading.repository.PresetCanaryStateRepository;
import com.bottrading.repository.ShadowPositionRepository;
import com.bottrading.research.nightly.PromotionGate;
import com.bottrading.research.nightly.PromotionGate.GateDecision;
import com.bottrading.research.nightly.ResearchProperties;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CanaryStageService {

  private static final Logger log = LoggerFactory.getLogger(CanaryStageService.class);

  private final PresetCanaryStateRepository stateRepository;
  private final ShadowPositionRepository shadowRepository;
  private final PresetService presetService;
  private final PresetsProperties presetsProperties;
  private final Clock clock;

  public CanaryStageService(
      PresetCanaryStateRepository stateRepository,
      ShadowPositionRepository shadowRepository,
      PresetService presetService,
      PresetsProperties presetsProperties,
      Optional<Clock> clock) {
    this.stateRepository = stateRepository;
    this.shadowRepository = shadowRepository;
    this.presetService = presetService;
    this.presetsProperties = presetsProperties;
    this.clock = clock.orElse(Clock.systemUTC());
  }

  public double multiplier(UUID presetId) {
    if (presetId == null) {
      return 1.0;
    }
    return stateRepository
        .findById(presetId)
        .map(PresetCanaryState::getCurrentMultiplier)
        .orElse(1.0);
  }

  @Transactional
  public PresetCanaryState initializeState(
      PresetVersion preset, String symbol, String runId, double oosPf, int oosTrades) {
    List<Double> stages = presetsProperties.getCanary().getStages();
    double firstStage = stages.isEmpty() ? 1.0 : stages.get(0);
    PresetCanaryState state =
        stateRepository
            .findById(preset.getId())
            .orElseGet(
                () -> {
                  PresetCanaryState created = new PresetCanaryState();
                  created.setPresetId(preset.getId());
                  return created;
                });
    state.setSymbol(symbol);
    state.setRegime(preset.getRegime());
    state.setStatus(CanaryStatus.ELIGIBLE);
    state.setStageIndex(0);
    state.setCurrentMultiplier(firstStage);
    state.setOosPf(oosPf);
    state.setOosTrades(oosTrades);
    state.setRunId(runId);
    state.setNotes(null);
    state.setShadowTradesBaseline(
        state.getShadowTradesBaseline() == null ? 0 : state.getShadowTradesBaseline());
    PresetCanaryState saved = stateRepository.save(state);
    log.info(
        "Initialized canary state for preset {} regime={} stageMultiplier={}",
        preset.getId(),
        preset.getRegime(),
        firstStage);
    return saved;
  }

  public List<StageUpdate> evaluatePending(ResearchProperties.Nightly nightly) {
    if (nightly == null) {
      return List.of();
    }
    ResearchProperties.Nightly.Gate gate = nightly.getGate();
    if (gate == null) {
      return List.of();
    }
    List<PresetCanaryState> candidates =
        stateRepository.findByStatusIn(
            EnumSet.of(CanaryStatus.ELIGIBLE, CanaryStatus.SHADOW_PENDING));
    if (candidates.isEmpty()) {
      return List.of();
    }
    List<Double> stages = presetsProperties.getCanary().getStages();
    List<StageUpdate> updates = new ArrayList<>();
    for (PresetCanaryState state : candidates) {
      StageUpdate update = evaluateState(state, gate, stages);
      if (update != null) {
        updates.add(update);
      }
    }
    return updates;
  }

  @Transactional
  StageUpdate evaluateState(
      PresetCanaryState state, ResearchProperties.Nightly.Gate gate, List<Double> stages) {
    UUID presetId = state.getPresetId();
    double oosPf = Optional.ofNullable(state.getOosPf()).orElse(0.0d);
    int baseline = Optional.ofNullable(state.getShadowTradesBaseline()).orElse(0);
    List<ShadowPositionEntity> closed =
        shadowRepository.findByPresetIdAndStatusOrderByClosedAtAsc(
            presetId, PositionStatus.CLOSED);
    int totalClosed = closed.size();
    int requiredTrades = gate.getShadowMinTrades();
    if (requiredTrades <= 0) {
      requiredTrades = 1;
    }
    int newTrades = totalClosed - baseline;
    if (newTrades < requiredTrades) {
      state.setStatus(CanaryStatus.SHADOW_PENDING);
      stateRepository.save(state);
      return new StageUpdate(
          presetId,
          state.getStatus(),
          state.getStageIndex(),
          state.getCurrentMultiplier(),
          "Waiting for shadow trades: %d/%d".formatted(newTrades, requiredTrades));
    }
    List<ShadowPositionEntity> window =
        closed.subList(Math.max(0, totalClosed - requiredTrades), totalClosed);
    ShadowMetrics metrics = computeShadowMetrics(window);
    state.setShadowPf(metrics.profitFactor());
    state.setLastShadowEvaluation(Instant.now(clock));
    state.setShadowTradesBaseline(totalClosed);
    GateDecision gateDecision =
        PromotionGate.evaluateShadow(oosPf, metrics.profitFactor(), metrics.trades(), gate);
    if (!gateDecision.approved()) {
      state.setStatus(CanaryStatus.REJECTED);
      state.setCurrentMultiplier(0.0);
      state.setNotes(gateDecision.reason());
      stateRepository.save(state);
      log.info("Shadow gate rejected preset {}: {}", presetId, gateDecision.reason());
      return new StageUpdate(
          presetId,
          CanaryStatus.REJECTED,
          state.getStageIndex(),
          state.getCurrentMultiplier(),
          gateDecision.reason());
    }

    int nextStageIndex = state.getStageIndex() + 1;
    if (nextStageIndex < stages.size()) {
      double nextMultiplier = stages.get(nextStageIndex);
      state.setStageIndex(nextStageIndex);
      state.setCurrentMultiplier(nextMultiplier);
      state.setStatus(CanaryStatus.ELIGIBLE);
      state.setNotes("Stage advanced to index " + nextStageIndex);
      stateRepository.save(state);
      log.info(
          "Advanced canary preset {} to stage {} multiplier {}",
          presetId,
          nextStageIndex,
          nextMultiplier);
      return new StageUpdate(
          presetId,
          CanaryStatus.ELIGIBLE,
          state.getStageIndex(),
          nextMultiplier,
          "Stage advanced");
    }

    state.setStageIndex(nextStageIndex - 1);
    state.setCurrentMultiplier(stages.isEmpty() ? 1.0 : stages.get(stages.size() - 1));
    state.setStatus(CanaryStatus.PROMOTED);
    state.setNotes("Promoted to full risk");
    stateRepository.save(state);
    try {
      presetService.activatePreset(presetId, PresetActivationMode.FULL, "research-nightly");
    } catch (Exception ex) {
      log.warn("Unable to activate preset {} after canary promotion: {}", presetId, ex.getMessage());
    }
    return new StageUpdate(
        presetId,
        CanaryStatus.PROMOTED,
        state.getStageIndex(),
        state.getCurrentMultiplier(),
        "Promoted to full");
  }

  private ShadowMetrics computeShadowMetrics(List<ShadowPositionEntity> trades) {
    double grossProfit = 0.0d;
    double grossLoss = 0.0d;
    double equity = 0.0d;
    double peak = 0.0d;
    double maxDrawdown = 0.0d;
    for (ShadowPositionEntity position : trades) {
      double pnl = 0.0d;
      if (position.getRealizedPnl() != null) {
        pnl = position.getRealizedPnl().doubleValue();
      } else if (position.getExitPrice() != null
          && position.getEntryPrice() != null
          && position.getQuantity() != null) {
        BigDecimal diff = position.getExitPrice().subtract(position.getEntryPrice());
        OrderSide side = position.getSide();
        if (side == OrderSide.SELL) {
          diff = diff.negate();
        }
        pnl = diff.multiply(position.getQuantity()).doubleValue();
      }
      equity += pnl;
      if (pnl >= 0) {
        grossProfit += pnl;
      } else {
        grossLoss += Math.abs(pnl);
      }
      if (equity > peak) {
        peak = equity;
      }
      double drawdown = peak - equity;
      if (drawdown > maxDrawdown) {
        maxDrawdown = drawdown;
      }
    }
    double pf;
    if (grossLoss == 0) {
      pf = grossProfit > 0 ? Double.POSITIVE_INFINITY : 0.0;
    } else {
      pf = grossProfit / grossLoss;
    }
    double maxDdPct;
    if (peak <= 0) {
      maxDdPct = 0.0;
    } else {
      maxDdPct = BigDecimal.valueOf(maxDrawdown)
          .divide(BigDecimal.valueOf(Math.abs(peak)), 8, RoundingMode.HALF_UP)
          .multiply(BigDecimal.valueOf(100))
          .doubleValue();
    }
    return new ShadowMetrics(trades.size(), pf, maxDdPct);
  }

  public record StageUpdate(
      UUID presetId, CanaryStatus status, int stageIndex, double multiplier, String message) {}

  private record ShadowMetrics(int trades, double profitFactor, double maxDrawdownPct) {}
}
