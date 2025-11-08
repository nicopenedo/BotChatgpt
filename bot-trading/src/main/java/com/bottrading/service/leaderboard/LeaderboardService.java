package com.bottrading.service.leaderboard;

// FIX: Use record-generated accessors with Java 21 visibility requirements.

import com.bottrading.model.entity.BacktestRun;
import com.bottrading.model.entity.EvaluationSnapshot;
import com.bottrading.model.entity.PresetVersion;
import com.bottrading.model.enums.LeaderboardWindow;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PresetStatus;
import com.bottrading.repository.BacktestRunRepository;
import com.bottrading.repository.EvaluationSnapshotRepository;
import com.bottrading.repository.PresetVersionRepository;
import com.bottrading.research.regime.RegimeTrend;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class LeaderboardService {

  private final PresetVersionRepository presetRepository;
  private final BacktestRunRepository backtestRunRepository;
  private final EvaluationSnapshotRepository snapshotRepository;

  public LeaderboardService(
      PresetVersionRepository presetRepository,
      BacktestRunRepository backtestRunRepository,
      EvaluationSnapshotRepository snapshotRepository) {
    this.presetRepository = presetRepository;
    this.backtestRunRepository = backtestRunRepository;
    this.snapshotRepository = snapshotRepository;
  }

  public List<LeaderboardEntry> leaderboard(
      RegimeTrend regime,
      OrderSide side,
      LeaderboardWindow window,
      Integer minTrades,
      Double maxDrawdown) {
    List<PresetVersion> presets;
    if (regime != null && side != null) {
      presets = presetRepository.findByRegimeAndSideOrderByCreatedAtDesc(regime, side);
    } else {
      presets = presetRepository.findAll();
    }
    List<LeaderboardEntry> entries = new ArrayList<>();
    for (PresetVersion preset : presets) {
      MetricsSnapshot snapshot = metricsForWindow(preset, window);
      if (snapshot.metrics().isEmpty()) {
        continue;
      }
      double trades = metric(snapshot.metrics(), "Trades");
      double maxdd = metric(snapshot.metrics(), "MaxDD");
      if (minTrades != null && trades < minTrades) {
        continue;
      }
      if (maxDrawdown != null && maxdd > maxDrawdown) {
        continue;
      }
      entries.add(
          new LeaderboardEntry(
              preset,
              snapshot.metrics(),
              metric(snapshot.metrics(), "PF"),
              trades,
              maxdd,
              snapshot.window(),
              snapshot.timestamp()));
    }
    return entries.stream()
        .sorted(Comparator.comparingDouble(LeaderboardEntry::profitFactor).reversed())
        .collect(Collectors.toList());
  }

  private MetricsSnapshot metricsForWindow(PresetVersion preset, LeaderboardWindow window) {
    if (window == null) {
      window = LeaderboardWindow.OOS_90D;
    }
    return switch (window) {
      case OOS_7D, OOS_30D, OOS_90D -> metricsFromBacktest(preset.getSourceRunId());
      case SHADOW_30D, SHADOW_90D -> metricsFromSnapshots(preset.getId(), window.name());
      case LIVE_7D, LIVE_30D, LIVE_90D -> metricsFromSnapshots(preset.getId(), window.name());
    };
  }

  private MetricsSnapshot metricsFromBacktest(String runId) {
    if (runId == null) {
      return MetricsSnapshot.empty();
    }
    Optional<BacktestRun> run = backtestRunRepository.findByRunId(runId);
    if (run.isEmpty()) {
      return MetricsSnapshot.empty();
    }
    return new MetricsSnapshot(run.get().getOosMetricsJson(), "OOS", run.get().getCreatedAt());
  }

  private MetricsSnapshot metricsFromSnapshots(UUID presetId, String window) {
    List<EvaluationSnapshot> snapshots = snapshotRepository.findByPresetIdOrderByCreatedAtDesc(presetId);
    for (EvaluationSnapshot snapshot : snapshots) {
      String storedWindow = snapshot.getWindow();
      if (window.equalsIgnoreCase(storedWindow)
          || storedWindow.equalsIgnoreCase(windowSuffix(window))) {
        Map<String, Object> payload;
        if (window.startsWith("LIVE")) {
          payload = snapshot.getLiveMetricsJson();
        } else {
          payload = snapshot.getShadowMetricsJson();
        }
        if (payload != null && !payload.isEmpty()) {
          return new MetricsSnapshot(payload, snapshot.getWindow(), snapshot.getCreatedAt());
        }
      }
    }
    return MetricsSnapshot.empty();
  }

  private String windowSuffix(String window) {
    int idx = window.indexOf('_');
    if (idx > 0 && idx < window.length() - 1) {
      return window.substring(idx + 1);
    }
    return window;
  }

  private double metric(Map<String, Object> metrics, String key) {
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

  public record LeaderboardEntry(
      PresetVersion preset,
      Map<String, Object> metrics,
      double profitFactor,
      double trades,
      double maxDrawdown,
      String window,
      Instant timestamp) {

    public boolean isActive() {
      return preset.getStatus() == PresetStatus.ACTIVE;
    }
  }

  private record MetricsSnapshot(Map<String, Object> metrics, String window, Instant timestamp) {
    static MetricsSnapshot empty() {
      return new MetricsSnapshot(Map.of(), "", null);
    }
  }
}
