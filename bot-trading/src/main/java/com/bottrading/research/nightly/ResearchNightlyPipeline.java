package com.bottrading.research.nightly;

import com.bottrading.model.entity.PresetVersion;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.SnapshotWindow;
import com.bottrading.notify.TelegramNotifier;
import com.bottrading.research.backtest.BacktestEngine;
import com.bottrading.research.backtest.BacktestRequest;
import com.bottrading.research.backtest.BacktestResult;
import com.bottrading.research.backtest.MetricsSummary;
import com.bottrading.research.backtest.ReportWriter;
import com.bottrading.research.ga.Evaluator;
import com.bottrading.research.ga.GaRunner;
import com.bottrading.research.ga.Genome;
import com.bottrading.research.ga.WalkForwardOptimizer;
import com.bottrading.research.io.DataLoader;
import com.bottrading.research.nightly.NightlyReportGenerator.ReportData;
import com.bottrading.research.nightly.NightlyReportGenerator.WindowMetrics;
import com.bottrading.research.regime.RegimeFilter;
import com.bottrading.research.regime.RegimeLabel;
import com.bottrading.research.regime.RegimeLabelSet;
import com.bottrading.research.regime.RegimeLabeler;
import com.bottrading.research.regime.RegimeTrend;
import com.bottrading.service.preset.CanaryStageService;
import com.bottrading.service.preset.PresetService;
import com.bottrading.service.preset.PresetService.BacktestMetadata;
import com.bottrading.service.preset.PresetService.PresetImportRequest;
import com.bottrading.service.snapshot.SnapshotService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ResearchNightlyPipeline {

  private static final Logger log = LoggerFactory.getLogger(ResearchNightlyPipeline.class);

  private final ResearchProperties properties;
  private final BacktestEngine backtestEngine;
  private final DataLoader dataLoader;
  private final RegimeLabeler regimeLabeler;
  private final ReportWriter reportWriter;
  private final NightlyReportGenerator reportGenerator;
  private final PresetService presetService;
  private final SnapshotService snapshotService;
  private final CanaryStageService canaryStageService;
  private final TelegramNotifier notifier;
  private final Clock clock;

  public ResearchNightlyPipeline(
      ResearchProperties properties,
      BacktestEngine backtestEngine,
      DataLoader dataLoader,
      RegimeLabeler regimeLabeler,
      ReportWriter reportWriter,
      NightlyReportGenerator reportGenerator,
      PresetService presetService,
      SnapshotService snapshotService,
      CanaryStageService canaryStageService,
      TelegramNotifier notifier,
      Optional<Clock> clock) {
    this.properties = properties;
    this.backtestEngine = backtestEngine;
    this.dataLoader = dataLoader;
    this.regimeLabeler = regimeLabeler;
    this.reportWriter = reportWriter;
    this.reportGenerator = reportGenerator;
    this.presetService = presetService;
    this.snapshotService = snapshotService;
    this.canaryStageService = canaryStageService;
    this.notifier = notifier;
    this.clock = clock.orElse(Clock.systemUTC());
  }

  public void runNightly() {
    ResearchProperties.Nightly nightly = properties.getNightly();
    if (nightly == null || !nightly.isEnabled()) {
      log.debug("Nightly research disabled");
      return;
    }
    try {
      executeNightly(nightly);
    } catch (Exception ex) {
      log.error("Nightly research pipeline failed", ex);
      notify("Nightly research failed: " + ex.getMessage());
    }
  }

  private void executeNightly(ResearchProperties.Nightly nightly) throws IOException {
    Instant now = Instant.now(clock);
    ResearchProperties.Dataset dataset = nightly.getDataset();
    Instant to = now;
    Instant from = to.minus(Duration.ofDays(Math.max(1, dataset.getHistoryDays())));
    String symbol = dataset.getSymbol();
    String interval = dataset.getInterval();
    log.info("Nightly research start symbol={} interval={} from={} to={}", symbol, interval, from, to);

    List<com.bottrading.model.dto.Kline> klines =
        dataLoader.load(symbol, interval, from, to, dataset.isUseCache());
    List<RegimeLabel> labels = regimeLabeler.label(symbol, interval, klines);
    RegimeLabelSet labelSet = new RegimeLabelSet(labels);
    String labelsHash = hashLabels(labels);

    LocalDate date = LocalDate.now(clock);
    Path baseDir = Path.of(nightly.getReporting().getBaseDir(), date.toString());
    Files.createDirectories(baseDir);
    regimeLabeler.exportCsv(labels, baseDir.resolve("regime_labels.csv"));

    String codeSha = resolveCodeRevision();

    for (RegimeTrend trend : RegimeTrend.values()) {
      try {
        runForTrend(nightly, trend, symbol, interval, from, to, labelSet, labelsHash, codeSha, baseDir);
      } catch (Exception ex) {
        log.warn("Nightly research failed for regime {}: {}", trend, ex.getMessage());
        notify("Nightly regime " + trend + " failed: " + ex.getMessage());
      }
    }

    var updates = canaryStageService.evaluatePending(nightly);
    for (CanaryStageService.StageUpdate update : updates) {
      notify(
          "Canary update preset="
              + update.presetId()
              + " status="
              + update.status()
              + " stage="
              + update.stageIndex()
              + " multiplier="
              + update.multiplier()
              + " -> "
              + update.message());
    }
  }

  private void runForTrend(
      ResearchProperties.Nightly nightly,
      RegimeTrend trend,
      String symbol,
      String interval,
      Instant from,
      Instant to,
      RegimeLabelSet labelSet,
      String labelsHash,
      String codeSha,
      Path baseDir)
      throws IOException, InterruptedException {
    ResearchProperties.Ga ga = nightly.getGa();
    ResearchProperties.Gate gate = nightly.getGate();

    String runId =
        "nightly-" + trend.name().toLowerCase() + "-" + LocalDate.now(clock).toString();
    Path regimeDir = baseDir.resolve(trend.name().toLowerCase());
    Files.createDirectories(regimeDir);

    RegimeFilter filter = new RegimeFilter(trend, labelSet);
    BacktestRequest baseRequest =
        new BacktestRequest(
            symbol,
            interval,
            from,
            to,
            null,
            null,
            ga.getSlippageBps(),
            ga.getTakerFeeBps(),
            ga.getMakerFeeBps(),
            ga.isUseDynamicFees(),
            ga.getSeed(),
            runId,
            nightly.getDataset().isUseCache(),
            filter,
            null);

    List<BacktestRequest> windows =
        WalkForwardOptimizer.splitByRegime(
            baseRequest,
            ga.getTrainDays(),
            ga.getValidationDays(),
            ga.getTestDays(),
            filter,
            ga.getMinSamples());
    if (windows.isEmpty()) {
      log.info("No valid walk-forward windows for regime {}", trend);
      return;
    }

    Genome champion = null;
    List<WindowMetrics> windowMetrics = new ArrayList<>();
    Map<String, Object> perSplitMetrics = new LinkedHashMap<>();

    for (BacktestRequest window : windows) {
      Evaluator evaluator =
          new Evaluator(
              backtestEngine,
              window,
              Math.max(1, ga.getMaxWorkers()),
              regimeDir,
              ga.getComplexityPenalty(),
              ga.getMinTrades());
      GaRunner runner = new GaRunner(evaluator, ga.getPopulation(), ga.getGenerations(), 0.2, 3, 2, ga.getSeed());
      Genome candidate = runner.run();
      if (candidate.metrics() != null) {
        windowMetrics.add(new WindowMetrics(window.runId(), candidate.metrics()));
        perSplitMetrics.put(window.runId(), toMetricsMap(candidate.metrics()));
      }
      if (champion == null || candidate.fitness() > champion.fitness()) {
        champion = candidate;
      }
    }

    if (champion == null) {
      log.warn("GA returned no champion for regime {}", trend);
      return;
    }

    BacktestResult result = backtestEngine.run(baseRequest, regimeDir, champion.toStrategy());
    reportWriter.write(regimeDir, result);
    Map<String, Object> oosMetrics = toMetricsMap(result.metrics());

    PromotionGate.GateDecision gateDecision =
        PromotionGate.evaluateOos(result.metrics(), gate, gate.getPfBaseline());

    Map<String, Object> shadowMetrics = Map.of();
    String status = gateDecision.approved() ? "eligible" : "rejected";
    String note = gateDecision.approved() ? "" : gateDecision.reason();

    ReportData reportData =
        new ReportData(runId, symbol, trend, status, note, oosMetrics, shadowMetrics, windowMetrics, regimeDir);
    reportGenerator.generate(reportData);

    if (!gateDecision.approved()) {
      log.info("Regime {} rejected by gate: {}", trend, gateDecision.reason());
      notify("Nightly " + trend + " rejected: " + gateDecision.reason());
      return;
    }

    Map<String, Object> paramsJson = genomeToParams(champion, runId);
    Map<String, Object> signalsJson = genomeToSignals(champion);

    BacktestMetadata metadata =
        new BacktestMetadata(
            runId,
            symbol,
            interval,
            from,
            to,
            trend.name(),
            ga.getPopulation(),
            ga.getGenerations(),
            "nightly-ga",
            ga.getSeed(),
            perSplitMetrics,
            codeSha,
            result.dataHash(),
            labelsHash);

    PresetImportRequest request =
        new PresetImportRequest(
            trend,
            OrderSide.BUY,
            paramsJson,
            signalsJson,
            oosMetrics,
            metadata,
            codeSha,
            result.dataHash(),
            labelsHash);

    PresetVersion preset = presetService.importPreset(request);
    snapshotService.createSnapshot(
        preset.getId(), SnapshotWindow.CUSTOM, oosMetrics, Map.of(), Map.of());
    MetricsSummary metrics = result.metrics();
    double pf = metrics.profitFactor() != null ? metrics.profitFactor().doubleValue() : 0.0d;
    canaryStageService.initializeState(preset, symbol, runId, pf, metrics.trades());
    notify(
        "Nightly regime "
            + trend
            + " eligible preset="
            + preset.getId()
            + " PF="
            + pf);
  }

  private Map<String, Object> genomeToParams(Genome genome, String presetKey) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("presetKey", presetKey);
    map.put("buy", sectionToMap(genome.toBuySection()));
    map.put("sell", sectionToMap(genome.toSellSection()));
    return map;
  }

  private Map<String, Object> genomeToSignals(Genome genome) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("buy", sectionToMap(genome.toBuySection()));
    map.put("sell", sectionToMap(genome.toSellSection()));
    return map;
  }

  private Map<String, Object> sectionToMap(com.bottrading.research.ga.io.GenomeSection section) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("threshold", section.threshold());
    map.put("enabled", section.enabledSignals());
    map.put("weights", section.weights());
    map.put("confidences", section.confidences());
    map.put("params", section.params());
    return map;
  }

  private Map<String, Object> toMetricsMap(MetricsSummary metrics) {
    Map<String, Object> map = new LinkedHashMap<>();
    if (metrics == null) {
      return map;
    }
    map.put("PF", safeNumber(metrics.profitFactor()));
    map.put("MaxDD", safeNumber(metrics.maxDrawdown()));
    map.put("Trades", metrics.trades());
    map.put("WinRate", safeNumber(metrics.winRate()));
    map.put("CAGR", safeNumber(metrics.cagr()));
    map.put("Sharpe", safeNumber(metrics.sharpe()));
    map.put("Sortino", safeNumber(metrics.sortino()));
    map.put("Exposure", safeNumber(metrics.exposure()));
    return map;
  }

  private double safeNumber(BigDecimal value) {
    return value == null ? 0.0d : value.doubleValue();
  }

  private void notify(String message) {
    if (notifier != null) {
      notifier.notifyResearch(message);
    }
  }

  private String resolveCodeRevision() {
    try {
      Process process =
          new ProcessBuilder("git", "rev-parse", "HEAD")
              .directory(Path.of(".").toFile())
              .start();
      int exit = process.waitFor();
      if (exit == 0) {
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      }
    } catch (Exception ex) {
      log.debug("Unable to resolve git revision: {}", ex.getMessage());
    }
    return null;
  }

  private String hashLabels(List<RegimeLabel> labels) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (RegimeLabel label : labels) {
        if (label == null || label.timestamp() == null) {
          continue;
        }
        digest.update(label.timestamp().toString().getBytes(StandardCharsets.UTF_8));
        if (label.trend() != null) {
          digest.update(label.trend().name().getBytes(StandardCharsets.UTF_8));
        }
        if (label.volatility() != null) {
          digest.update(label.volatility().name().getBytes(StandardCharsets.UTF_8));
        }
      }
      return bytesToHex(digest.digest());
    } catch (Exception ex) {
      log.debug("Unable to hash labels: {}", ex.getMessage());
      return null;
    }
  }

  private String bytesToHex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      builder.append(String.format("%02x", b));
    }
    return builder.toString();
  }
}
