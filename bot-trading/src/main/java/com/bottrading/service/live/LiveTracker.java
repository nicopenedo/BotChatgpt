package com.bottrading.service.live;

import com.bottrading.model.entity.LiveTracking;
import com.bottrading.model.entity.PresetVersion;
import com.bottrading.repository.LiveTrackingRepository;
import com.bottrading.repository.PresetVersionRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LiveTracker {

  private static final Logger log = LoggerFactory.getLogger(LiveTracker.class);

  private final PresetVersionRepository presetRepository;
  private final LiveTrackingRepository liveTrackingRepository;

  public LiveTracker(
      PresetVersionRepository presetRepository, LiveTrackingRepository liveTrackingRepository) {
    this.presetRepository = presetRepository;
    this.liveTrackingRepository = liveTrackingRepository;
  }

  @Transactional
  public LiveTracking record(
      UUID presetId,
      Instant from,
      Instant to,
      double capitalRisked,
      double pnl,
      double profitFactor,
      double maxDrawdown,
      int trades,
      double slippageBps,
      Map<String, Object> driftFlags) {
    PresetVersion preset =
        presetRepository
            .findById(presetId)
            .orElseThrow(() -> new IllegalArgumentException("Preset not found: " + presetId));
    LiveTracking tracking = new LiveTracking();
    tracking.setPreset(preset);
    tracking.setTsFrom(from);
    tracking.setTsTo(to);
    tracking.setCapitalRisked(capitalRisked);
    tracking.setPnl(pnl);
    tracking.setPf(profitFactor);
    tracking.setMaxDrawdown(maxDrawdown);
    tracking.setTrades(trades);
    tracking.setSlippageBps(slippageBps);
    tracking.setDriftFlags(driftFlags);
    LiveTracking saved = liveTrackingRepository.save(tracking);
    log.info("Recorded live metrics {} for preset {}", saved.getId(), presetId);
    return saved;
  }
}
