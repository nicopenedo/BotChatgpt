package com.bottrading.cli;

import com.bottrading.research.backtest.BacktestEngine;
import com.bottrading.research.backtest.BacktestRequest;
import com.bottrading.research.backtest.BacktestResult;
import com.bottrading.research.backtest.realistic.RealisticBacktestConfig;
import com.bottrading.research.backtest.realistic.RealisticConfigLoader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(
    name = "bt",
    description = "Run backtests with realistic execution",
    mixinStandardHelpOptions = true)
public class BacktestCliCommand implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(BacktestCliCommand.class);

  private final BacktestEngine engine;

  @CommandLine.Option(names = "--symbol", required = true)
  private String symbol;

  @CommandLine.Option(names = "--interval", defaultValue = "1m")
  private String interval;

  @CommandLine.Option(names = "--from")
  private String from;

  @CommandLine.Option(names = "--to")
  private String to;

  @CommandLine.Option(names = "--strategy")
  private Path strategyPath;

  @CommandLine.Option(names = "--genomes")
  private Path genomesPath;

  @CommandLine.Option(names = "--preset")
  private Path presetPath;

  @CommandLine.Option(names = "--maker-fee-bps", defaultValue = "0")
  private BigDecimal makerFeeBps;

  @CommandLine.Option(names = "--taker-fee-bps", defaultValue = "0")
  private BigDecimal takerFeeBps;

  @CommandLine.Option(names = "--vip-discount-bps", defaultValue = "0")
  private BigDecimal vipDiscountBps;

  @CommandLine.Option(names = "--seed")
  private Long seed;

  @CommandLine.Option(names = "--run-id")
  private String runId;

  @CommandLine.Option(names = "--exec", defaultValue = "realistic")
  private String execMode;

  @CommandLine.Option(names = "--tca", defaultValue = "synthetic")
  private String tcaMode;

  @CommandLine.Option(names = "--out")
  private Path out;

  public BacktestCliCommand(BacktestEngine engine) {
    this.engine = engine;
  }

  @Override
  public void run() {
    try {
      RealisticBacktestConfig config = presetPath != null ? RealisticConfigLoader.load(presetPath) : new RealisticBacktestConfig();
      if ("calibrated".equalsIgnoreCase(tcaMode)) {
        tuneTca(config.tca());
      }
      BigDecimal maker = makerFeeBps.subtract(vipDiscountBps);
      BigDecimal taker = takerFeeBps.subtract(vipDiscountBps);
      Instant start = from != null ? Instant.parse(from) : null;
      Instant end = to != null ? Instant.parse(to) : null;
      String id = runId != null ? runId : "bt-" + System.currentTimeMillis();

      BacktestRequest request =
          new BacktestRequest(
              symbol,
              interval,
              start,
              end,
              strategyPath,
              genomesPath,
              BigDecimal.ZERO,
              taker.max(BigDecimal.ZERO),
              maker.max(BigDecimal.ZERO),
              false,
              seed,
              id,
              true,
              null,
              useRealistic() ? config : null);

      Path reportDir = prepareOutputDirectory(id);
      BacktestResult result = engine.run(request, reportDir);
      log.info(
          "Backtest completed: ProfitFactor={} WinRate={} FillRate={} TTLExpiredRate={}",
          result.metrics().profitFactor(),
          result.metrics().winRate(),
          result.metrics().fillRate(),
          result.metrics().ttlExpiredRate());
      if (out != null && out.toString().endsWith(".json")) {
        Path summary = reportDir.resolve("summary.json");
        Files.copy(summary, out, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (Exception ex) {
      log.error("Backtest CLI failure", ex);
    }
  }

  private boolean useRealistic() {
    return "realistic".equalsIgnoreCase(execMode);
  }

  private Path prepareOutputDirectory(String id) throws java.io.IOException {
    Path directory;
    if (out != null && !out.toString().endsWith(".json")) {
      directory = out;
    } else {
      directory = Path.of("reports").resolve("cli").resolve(symbol).resolve(id);
    }
    Files.createDirectories(directory);
    return directory;
  }

  private void tuneTca(RealisticBacktestConfig.TcaConfig config) {
    config.setSpreadWeight(BigDecimal.valueOf(0.8));
    config.setHourWeight(BigDecimal.valueOf(0.4));
    config.setQuantityWeight(BigDecimal.valueOf(0.7));
    config.setVolumeWeight(BigDecimal.valueOf(0.3));
  }
}
