package com.bottrading.service.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

  @Mock private PresetVersionRepository presetRepository;
  @Mock private BacktestRunRepository backtestRunRepository;
  @Mock private EvaluationSnapshotRepository snapshotRepository;

  private LeaderboardService service;

  @BeforeEach
  void setUp() {
    service = new LeaderboardService(presetRepository, backtestRunRepository, snapshotRepository);
  }

  @Test
  void aggregatesBacktestMetricsUsingRecordAccessors() {
    PresetVersion preset = new PresetVersion();
    preset.setId(UUID.randomUUID());
    preset.setRegime(RegimeTrend.UP);
    preset.setSide(OrderSide.BUY);
    preset.setStatus(PresetStatus.ACTIVE);
    preset.setSourceRunId("run-1");

    BacktestRun run = new BacktestRun();
    run.setRunId("run-1");
    run.setOosMetricsJson(Map.of("PF", 1.8, "Trades", 55, "MaxDD", 6));
    run.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));

    when(presetRepository.findByRegimeAndSideOrderByCreatedAtDesc(RegimeTrend.UP, OrderSide.BUY))
        .thenReturn(List.of(preset));
    when(backtestRunRepository.findByRunId("run-1")).thenReturn(Optional.of(run));

    List<LeaderboardService.LeaderboardEntry> entries =
        service.leaderboard(RegimeTrend.UP, OrderSide.BUY, LeaderboardWindow.OOS_90D, 40, 10.0);

    assertThat(entries).hasSize(1);
    LeaderboardService.LeaderboardEntry entry = entries.getFirst();
    assertThat(entry.metrics().get("PF")).isEqualTo(1.8);
    assertThat(entry.trades()).isEqualTo(55);
    assertThat(entry.maxDrawdown()).isEqualTo(6);
    assertThat(entry.window()).isEqualTo("OOS");
  }

  @Test
  void loadsLiveMetricsFromSnapshotsWhenPresent() {
    PresetVersion preset = new PresetVersion();
    UUID presetId = UUID.randomUUID();
    preset.setId(presetId);
    preset.setStatus(PresetStatus.ACTIVE);

    EvaluationSnapshot snapshot = new EvaluationSnapshot();
    snapshot.setPreset(preset);
    snapshot.setWindow("LIVE_30D");
    snapshot.setLiveMetricsJson(Map.of("PF", 1.2, "Trades", 15, "MaxDD", 4));
    snapshot.setCreatedAt(Instant.parse("2024-02-01T00:00:00Z"));

    when(presetRepository.findAll()).thenReturn(List.of(preset));
    when(snapshotRepository.findByPresetIdOrderByCreatedAtDesc(presetId)).thenReturn(List.of(snapshot));

    List<LeaderboardService.LeaderboardEntry> entries =
        service.leaderboard(null, null, LeaderboardWindow.LIVE_30D, null, null);

    assertThat(entries).hasSize(1);
    LeaderboardService.LeaderboardEntry entry = entries.getFirst();
    assertThat(entry.metrics().get("PF")).isEqualTo(1.2);
    assertThat(entry.trades()).isEqualTo(15);
    assertThat(entry.maxDrawdown()).isEqualTo(4);
    assertThat(entry.window()).isEqualTo("LIVE_30D");
  }
}
