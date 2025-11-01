package com.bottrading.service.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.model.entity.BacktestRun;
import com.bottrading.model.entity.PresetVersion;
import com.bottrading.model.enums.LeaderboardWindow;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PresetStatus;
import com.bottrading.repository.BacktestRunRepository;
import com.bottrading.repository.PresetVersionRepository;
import com.bottrading.research.regime.RegimeTrend;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(LeaderboardService.class)
class LeaderboardServiceTest {

  @Autowired private LeaderboardService leaderboardService;
  @Autowired private PresetVersionRepository presetVersionRepository;
  @Autowired private BacktestRunRepository backtestRunRepository;

  @BeforeEach
  void setup() {
    PresetVersion preset = new PresetVersion();
    preset.setRegime(RegimeTrend.UP);
    preset.setSide(OrderSide.BUY);
    preset.setParamsJson(Map.of("presetKey", "alpha"));
    preset.setStatus(PresetStatus.CANDIDATE);
    presetVersionRepository.save(preset);

    BacktestRun run = new BacktestRun();
    run.setRunId("RUN_A");
    run.setSymbol("BTCUSDT");
    run.setInterval("1h");
    run.setTsFrom(Instant.now());
    run.setTsTo(Instant.now());
    run.setOosMetricsJson(Map.of("PF", 2.0, "MaxDD", 4.0, "Trades", 150));
    backtestRunRepository.save(run);

    preset.setSourceRunId("RUN_A");
    presetVersionRepository.save(preset);
  }

  @Test
  void leaderboardFiltersByWindow() {
    List<LeaderboardService.LeaderboardEntry> entries =
        leaderboardService.leaderboard(
            RegimeTrend.UP, OrderSide.BUY, LeaderboardWindow.OOS_90D, 100, 5.0);
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).profitFactor()).isEqualTo(2.0);
  }

  @Test
  void leaderboardFiltersByMinTrades() {
    List<LeaderboardService.LeaderboardEntry> entries =
        leaderboardService.leaderboard(
            RegimeTrend.UP, OrderSide.BUY, LeaderboardWindow.OOS_90D, 200, null);
    assertThat(entries).isEmpty();
  }
}
