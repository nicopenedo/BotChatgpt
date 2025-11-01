package com.bottrading.service.snapshot;

import com.bottrading.model.entity.EvaluationSnapshot;
import com.bottrading.model.entity.PresetVersion;
import com.bottrading.model.enums.SnapshotWindow;
import com.bottrading.repository.EvaluationSnapshotRepository;
import com.bottrading.repository.PresetVersionRepository;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SnapshotService {

  private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);

  private final PresetVersionRepository presetRepository;
  private final EvaluationSnapshotRepository snapshotRepository;

  public SnapshotService(
      PresetVersionRepository presetRepository,
      EvaluationSnapshotRepository snapshotRepository) {
    this.presetRepository = presetRepository;
    this.snapshotRepository = snapshotRepository;
  }

  @Transactional
  public EvaluationSnapshot createSnapshot(
      UUID presetId,
      SnapshotWindow window,
      Map<String, Object> oosMetrics,
      Map<String, Object> shadowMetrics,
      Map<String, Object> liveMetrics) {
    PresetVersion preset =
        presetRepository
            .findById(presetId)
            .orElseThrow(() -> new IllegalArgumentException("Preset not found: " + presetId));
    EvaluationSnapshot snapshot = new EvaluationSnapshot();
    snapshot.setPreset(preset);
    snapshot.setWindow(window.getLabel().toUpperCase());
    snapshot.setOosMetricsJson(oosMetrics);
    snapshot.setShadowMetricsJson(shadowMetrics);
    snapshot.setLiveMetricsJson(liveMetrics);
    EvaluationSnapshot saved = snapshotRepository.save(snapshot);
    log.info("Created snapshot {} for preset {} window {}", saved.getId(), presetId, window);
    return saved;
  }
}
