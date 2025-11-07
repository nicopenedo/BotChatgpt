package com.bottrading.service.preset;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.config.ClockConfig;
import com.bottrading.config.PresetsProperties;
import com.bottrading.model.entity.PresetVersion;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PresetActivationMode;
import com.bottrading.repository.BacktestRunRepository;
import com.bottrading.repository.PresetVersionRepository;
import com.bottrading.research.regime.RegimeTrend;
import com.bottrading.service.preset.BacktestMetadata;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({PresetService.class, PresetsProperties.class, ClockConfig.class})
class PresetServiceTest {

  @Autowired private PresetService presetService;
  @Autowired private PresetVersionRepository presetVersionRepository;
  @Autowired private BacktestRunRepository backtestRunRepository;

  @Test
  void importAndActivatePreset() {
    BacktestMetadata metadata =
        new BacktestMetadata(
            "RUN_TEST",
            "BTCUSDT",
            "1h",
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-02-01T00:00:00Z"),
            "UP",
            50,
            20,
            "PF",
            42L,
            Map.of(),
            "abc",
            "data",
            "labels");
    PresetService.PresetImportRequest request =
        new PresetService.PresetImportRequest(
            RegimeTrend.UP,
            OrderSide.BUY,
            Map.of("presetKey", "alpha"),
            Map.of("signal", true),
            Map.of("PF", 2.0, "MaxDD", 5.0, "Trades", 200),
            metadata,
            "abc",
            "data",
            "labels");

    PresetVersion candidate = presetService.importPreset(request);

    assertThat(candidate.getStatus()).isNotNull();
    assertThat(candidate.getRegime()).isEqualTo(RegimeTrend.UP);
    assertThat(backtestRunRepository.findByRunId("RUN_TEST")).isPresent();

    PresetVersion active =
        presetService.activatePreset(candidate.getId(), PresetActivationMode.FULL, "test");

    assertThat(active.getStatus()).isEqualTo(com.bottrading.model.enums.PresetStatus.ACTIVE);
    assertThat(presetVersionRepository
            .findFirstByRegimeAndSideAndStatusOrderByActivatedAtDesc(
                RegimeTrend.UP, OrderSide.BUY, com.bottrading.model.enums.PresetStatus.ACTIVE))
        .isPresent();
  }
}
