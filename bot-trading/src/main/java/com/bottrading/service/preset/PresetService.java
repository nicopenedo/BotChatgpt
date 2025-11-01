package com.bottrading.service.preset;

import com.bottrading.config.PresetsProperties;
import com.bottrading.model.entity.BacktestRun;
import com.bottrading.model.entity.EvaluationSnapshot;
import com.bottrading.model.entity.LiveTracking;
import com.bottrading.model.entity.PresetVersion;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PresetActivationMode;
import com.bottrading.model.enums.PresetStatus;
import com.bottrading.repository.BacktestRunRepository;
import com.bottrading.repository.EvaluationSnapshotRepository;
import com.bottrading.repository.LiveTrackingRepository;
import com.bottrading.repository.PresetVersionRepository;
import com.bottrading.research.regime.RegimeTrend;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PresetService {

  private static final Logger log = LoggerFactory.getLogger(PresetService.class);

  private final PresetVersionRepository presetRepository;
  private final BacktestRunRepository backtestRunRepository;
  private final EvaluationSnapshotRepository snapshotRepository;
  private final LiveTrackingRepository liveTrackingRepository;
  private final PresetsProperties presetsProperties;
  private final Clock clock;

  public PresetService(
      PresetVersionRepository presetRepository,
      BacktestRunRepository backtestRunRepository,
      EvaluationSnapshotRepository snapshotRepository,
      LiveTrackingRepository liveTrackingRepository,
      PresetsProperties presetsProperties,
      Clock clock) {
    this.presetRepository = presetRepository;
    this.backtestRunRepository = backtestRunRepository;
    this.snapshotRepository = snapshotRepository;
    this.liveTrackingRepository = liveTrackingRepository;
    this.presetsProperties = presetsProperties;
    this.clock = clock;
  }

  @Transactional
  public PresetVersion importPreset(PresetImportRequest request) {
    PresetVersion preset = new PresetVersion();
    preset.setRegime(request.regime());
    preset.setSide(request.side());
    preset.setParamsJson(request.paramsJson());
    preset.setSignalsJson(request.signalsJson());
    preset.setCodeSha(request.codeSha());
    preset.setDataHash(request.dataHash());
    preset.setLabelsHash(request.labelsHash());
    if (request.backtest() != null) {
      persistBacktest(request.backtest(), request.oosMetrics());
      preset.setSourceRunId(request.backtest().runId());
    }
    PresetVersion saved = presetRepository.save(preset);
    log.info(
        "Imported preset {} for regime {} {} sourced from run {}",
        saved.getId(),
        saved.getRegime(),
        saved.getSide(),
        saved.getSourceRunId());
    return saved;
  }

  @Transactional
  public PresetVersion activatePreset(UUID presetId, PresetActivationMode mode, String actor) {
    PresetVersion preset =
        presetRepository
            .findById(presetId)
            .orElseThrow(() -> new IllegalArgumentException("Preset not found: " + presetId));
    PromotionDecision decision = evaluateForPromotion(preset);
    if (!decision.approved()) {
      throw new IllegalStateException("Preset does not satisfy promotion policy: " + decision.reason());
    }

    Instant now = Instant.now(clock);
    presetRepository
        .findFirstByRegimeAndSideAndStatusOrderByActivatedAtDesc(
            preset.getRegime(), preset.getSide(), PresetStatus.ACTIVE)
        .ifPresent(
            active -> {
              active.setStatus(PresetStatus.RETIRED);
              active.setRetiredAt(now);
              presetRepository.save(active);
              log.info(
                  "Retired preset {} for regime {} {} in favour of {}",
                  active.getId(),
                  preset.getRegime(),
                  preset.getSide(),
                  presetId);
            });
    preset.setStatus(PresetStatus.ACTIVE);
    preset.setActivatedAt(now);
    preset.setRetiredAt(null);
    PresetVersion saved = presetRepository.save(preset);
    log.info(
        "Activated preset {} (mode={}, actor={}) for regime {} {}",
        saved.getId(),
        mode,
        actor,
        saved.getRegime(),
        saved.getSide());
    return saved;
  }

  @Transactional
  public PresetVersion retirePreset(UUID presetId, String actor) {
    PresetVersion preset =
        presetRepository
            .findById(presetId)
            .orElseThrow(() -> new IllegalArgumentException("Preset not found: " + presetId));
    preset.setStatus(PresetStatus.RETIRED);
    preset.setRetiredAt(Instant.now(clock));
    PresetVersion saved = presetRepository.save(preset);
    log.info("Preset {} retired by {}", presetId, actor);
    return saved;
  }

  @Transactional
  public PresetVersion rollback(RegimeTrend regime, OrderSide side, String actor) {
    Instant now = Instant.now(clock);
    AtomicReference<UUID> justRetired = new AtomicReference<>();
    presetRepository
        .findFirstByRegimeAndSideAndStatusOrderByActivatedAtDesc(regime, side, PresetStatus.ACTIVE)
        .ifPresent(
            active -> {
              active.setStatus(PresetStatus.RETIRED);
              active.setRetiredAt(now);
              presetRepository.save(active);
              justRetired.set(active.getId());
            });
    List<PresetVersion> retired =
        presetRepository.findByRegimeAndSideAndStatusOrderByRetiredAtDesc(
            regime, side, PresetStatus.RETIRED);
    PresetVersion target =
        retired.stream()
            .filter(p -> {
              UUID retiredId = justRetired.get();
              return retiredId == null || !retiredId.equals(p.getId());
            })
            .findFirst()
            .orElse(null);
    if (target == null) {
      throw new IllegalStateException("No retired presets available for rollback");
    }
    target.setStatus(PresetStatus.ACTIVE);
    target.setActivatedAt(now);
    target.setRetiredAt(null);
    PresetVersion saved = presetRepository.save(target);
    log.info("Rolled back to preset {} for regime {} {} by {}", saved.getId(), regime, side, actor);
    return saved;
  }

  public Optional<PresetVersion> getActivePreset(RegimeTrend regime, OrderSide side) {
    return presetRepository.findFirstByRegimeAndSideAndStatusOrderByActivatedAtDesc(
        regime, side, PresetStatus.ACTIVE);
  }

  public List<PresetVersion> listPresets(RegimeTrend regime, OrderSide side, PresetStatus status) {
    if (regime != null && side != null && status != null) {
      return presetRepository.findByRegimeAndSideAndStatusOrderByActivatedAtDesc(regime, side, status);
    }
    if (regime != null && side != null) {
      return presetRepository.findByRegimeAndSideOrderByCreatedAtDesc(regime, side);
    }
    if (status != null) {
      return presetRepository.findByStatus(status);
    }
    return presetRepository.findAll();
  }

  public PresetVersion getPreset(UUID id) {
    return presetRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Preset not found: " + id));
  }

  public List<EvaluationSnapshot> snapshots(UUID presetId) {
    return snapshotRepository.findByPresetIdOrderByCreatedAtDesc(presetId);
  }

  public List<LiveTracking> liveMetrics(UUID presetId) {
    return liveTrackingRepository.findByPresetIdOrderByCreatedAtDesc(presetId);
  }

  private PromotionDecision evaluateForPromotion(PresetVersion preset) {
    if (preset.getSourceRunId() == null) {
      return new PromotionDecision(false, "Preset missing backtest run");
    }
    BacktestRun run =
        backtestRunRepository
            .findByRunId(preset.getSourceRunId())
            .orElse(null);
    if (run == null) {
      return new PromotionDecision(false, "Backtest run not found");
    }
    Map<String, Object> oos = run.getOosMetricsJson();
    double pf = metricAsDouble(oos, "PF");
    double maxdd = metricAsDouble(oos, "MaxDD");
    double trades = metricAsDouble(oos, "Trades");
    PresetsProperties.Promotion policy = presetsProperties.getPromotion();
    double pfThreshold = policy.getPfBaseline() * (1.0 + policy.getEpsilonPf());
    if (pf < pfThreshold) {
      return new PromotionDecision(false, "PF below threshold");
    }
    if (maxdd > policy.getMaxddCapPct()) {
      return new PromotionDecision(false, "MaxDD above cap");
    }
    if (trades < policy.getMinTradesOos()) {
      return new PromotionDecision(false, "Not enough trades");
    }
    Optional<EvaluationSnapshot> latestShadow =
        snapshotRepository.findByPresetIdOrderByCreatedAtDesc(preset.getId()).stream()
            .filter(s -> s.getShadowMetricsJson() != null && !s.getShadowMetricsJson().isEmpty())
            .max(Comparator.comparing(EvaluationSnapshot::getCreatedAt));
    if (latestShadow.isPresent()) {
      Map<String, Object> shadow = latestShadow.get().getShadowMetricsJson();
      double shadowTrades = metricAsDouble(shadow, "Trades");
      if (shadowTrades < policy.getShadowMinTrades()) {
        return new PromotionDecision(false, "Shadow trades below minimum");
      }
      double pfShadow = metricAsDouble(shadow, "PF");
      double tolerance = policy.getShadowPfDropTolerance();
      if (pfShadow < pf * (1.0 - tolerance)) {
        return new PromotionDecision(false, "Shadow PF below tolerance");
      }
    }
    return new PromotionDecision(true, "ok");
  }

  private void persistBacktest(PresetImportRequest.BacktestMetadata metadata, Map<String, Object> oos) {
    BacktestRun run =
        backtestRunRepository.findByRunId(metadata.runId()).orElseGet(BacktestRun::new);
    run.setRunId(metadata.runId());
    run.setSymbol(metadata.symbol());
    run.setInterval(metadata.interval());
    run.setTsFrom(metadata.tsFrom());
    run.setTsTo(metadata.tsTo());
    run.setRegimeMask(metadata.regimeMask());
    run.setGaPopulation(metadata.gaPopulation());
    run.setGaGenerations(metadata.gaGenerations());
    run.setFitnessDefinition(metadata.fitnessDefinition());
    run.setSeed(metadata.seed());
    run.setCodeSha(metadata.codeSha());
    run.setDataHash(metadata.dataHash());
    run.setLabelsHash(metadata.labelsHash());
    if (oos != null) {
      run.setOosMetricsJson(oos);
    }
    run.setPerSplitMetricsJson(metadata.perSplitMetrics());
    backtestRunRepository.save(run);
  }

  private double metricAsDouble(Map<String, Object> metrics, String key) {
    if (metrics == null || !metrics.containsKey(key)) {
      return 0.0;
    }
    Object value = metrics.get(key);
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return Double.parseDouble(value.toString());
    } catch (NumberFormatException ex) {
      return 0.0;
    }
  }

  public record PresetImportRequest(
      RegimeTrend regime,
      OrderSide side,
      Map<String, Object> paramsJson,
      Map<String, Object> signalsJson,
      Map<String, Object> oosMetrics,
      BacktestMetadata backtest,
      String codeSha,
      String dataHash,
      String labelsHash) {}

  public record BacktestMetadata(
      String runId,
      String symbol,
      String interval,
      Instant tsFrom,
      Instant tsTo,
      String regimeMask,
      Integer gaPopulation,
      Integer gaGenerations,
      String fitnessDefinition,
      Long seed,
      Map<String, Object> perSplitMetrics,
      String codeSha,
      String dataHash,
      String labelsHash) {}

  private record PromotionDecision(boolean approved, String reason) {}
}
