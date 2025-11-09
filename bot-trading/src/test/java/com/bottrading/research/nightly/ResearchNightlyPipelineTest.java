package com.bottrading.research.nightly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bottrading.model.dto.Kline;
import com.bottrading.research.backtest.BacktestEngine;
import com.bottrading.research.backtest.ReportWriter;
import com.bottrading.research.io.DataLoader;
import com.bottrading.research.nightly.NightlyReportGenerator;
import com.bottrading.research.nightly.ResearchProperties;
import com.bottrading.research.nightly.ResearchProperties.Nightly;
import com.bottrading.research.regime.RegimeLabel;
import com.bottrading.research.regime.RegimeLabeler;
import com.bottrading.research.regime.RegimeTrend;
import com.bottrading.research.regime.RegimeVolatility;
import com.bottrading.service.preset.CanaryStageService;
import com.bottrading.service.preset.PresetService;
import com.bottrading.service.snapshot.SnapshotService;
import com.bottrading.notify.TelegramNotifier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResearchNightlyPipelineTest {

  @Mock private BacktestEngine backtestEngine;
  @Mock private DataLoader dataLoader;
  @Mock private RegimeLabeler regimeLabeler;
  @Mock private ReportWriter reportWriter;
  @Mock private NightlyReportGenerator reportGenerator;
  @Mock private PresetService presetService;
  @Mock private SnapshotService snapshotService;
  @Mock private CanaryStageService canaryStageService;
  @Mock private TelegramNotifier notifier;

  @TempDir Path tempDir;

  private ResearchProperties properties;
  private Clock clock;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    properties = new ResearchProperties();
    Nightly nightly = properties.getNightly();
    nightly.setEnabled(true);
    nightly.getDataset().setSymbol("BTCUSDT");
    nightly.getDataset().setInterval("1h");
    nightly.getDataset().setHistoryDays(1);
    nightly.getReporting().setBaseDir(tempDir.toString());
    nightly.getGa().setMinSamples(100);
    nightly.getGa().setTrainDays(1);
    nightly.getGa().setValidationDays(1);
    nightly.getGa().setTestDays(1);

    clock = Clock.fixed(Instant.parse("2024-03-01T00:00:00Z"), ZoneOffset.UTC);
    meterRegistry = new SimpleMeterRegistry();
  }

  @Test
  void runNightlyRecordsTimerUsingMeterRegistry() {
    List<Kline> klines =
        List.of(
            new Kline(
                Instant.parse("2024-02-29T23:58:00Z"),
                Instant.parse("2024-02-29T23:59:00Z"),
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE));
    when(dataLoader.load(anyString(), anyString(), any(Instant.class), any(Instant.class), anyBoolean()))
        .thenReturn(klines);
    List<RegimeLabel> labels =
        List.of(new RegimeLabel(Instant.parse("2024-02-29T23:58:00Z"), RegimeTrend.UP, RegimeVolatility.LO));
    when(regimeLabeler.label(anyString(), anyString(), any())).thenReturn(labels);
    doNothing().when(regimeLabeler).exportCsv(any(), any());
    when(canaryStageService.evaluatePending(any())).thenReturn(List.of());

    ResearchNightlyPipeline pipeline =
        new ResearchNightlyPipeline(
            properties,
            backtestEngine,
            dataLoader,
            regimeLabeler,
            reportWriter,
            reportGenerator,
            presetService,
            snapshotService,
            canaryStageService,
            notifier,
            Optional.of(clock),
            meterRegistry);

    pipeline.runNightly();

    assertThat(meterRegistry.find("research.nightly.stage.duration").tag("stage", "total").timer().count())
        .isGreaterThan(0);
    assertThat(
            meterRegistry
                .get("research.nightly.runs")
                .tag("symbol", "BTCUSDT")
                .tag("interval", "1h")
                .counter()
                .count())
        .isEqualTo(1.0);
  }
}
