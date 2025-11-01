package com.bottrading.research;

import com.bottrading.research.backtest.BacktestEngine;
import com.bottrading.research.backtest.BacktestRequest;
import com.bottrading.research.backtest.BacktestRequest;
import com.bottrading.research.backtest.BacktestResult;
import com.bottrading.research.backtest.MetricsSummary;
import com.bottrading.research.ga.Evaluator;
import com.bottrading.research.ga.GaProgressReporter;
import com.bottrading.research.ga.GaRunner;
import com.bottrading.research.ga.Genome;
import com.bottrading.research.ga.io.GenomeFile;
import com.bottrading.research.ga.io.GenomeIO;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ResearchRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ResearchRunner.class);
  private final BacktestEngine backtestEngine;

  public ResearchRunner(BacktestEngine backtestEngine) {
    this.backtestEngine = backtestEngine;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    List<String> commands = args.getNonOptionArgs();
    if (commands.contains("backtest")) {
      runBacktest(args);
    }
    if (commands.contains("ga")) {
      runGa(args);
    }
  }

  private void runBacktest(ApplicationArguments args) {
    String symbol = option(args, "symbol").orElse("BTCUSDT");
    String interval = option(args, "interval").orElse("1m");
    Instant from = option(args, "from").map(this::parseInstant).orElse(null);
    Instant to = option(args, "to").map(this::parseInstant).orElse(null);
    Path strategyPath = option(args, "strategy").map(Path::of).orElse(null);
    Path genomesPath = option(args, "genomes").map(Path::of).orElse(null);
    BigDecimal slippage = option(args, "slippageBps").map(BigDecimal::new).orElse(BigDecimal.ZERO);
    FeeConfig feeConfig = parseFees(option(args, "fees"));
    Long seed = option(args, "seed").map(Long::parseLong).orElse(null);
    String runId = option(args, "run-id").orElse("run-" + System.currentTimeMillis());
    Path reportDir = Path.of("reports").resolve(symbol).resolve(runId);

    BacktestRequest request =
        new BacktestRequest(
            symbol,
            interval,
            from,
            to,
            strategyPath,
            genomesPath,
            slippage,
            feeConfig.takerFee(),
            feeConfig.makerFee(),
            feeConfig.dynamic,
            seed,
            runId,
            true);
    try {
      BacktestResult result = backtestEngine.run(request, reportDir);
      log.info(
          "Backtest finished. Reports at {} | WinRate={} ProfitFactor={} MaxDD={} Trades={}",
          reportDir.toAbsolutePath(),
          asDecimal(result.metrics().winRate()),
          asDecimal(result.metrics().profitFactor()),
          asDecimal(result.metrics().maxDrawdown()),
          result.metrics().trades());
    } catch (Exception ex) {
      log.error("Backtest failed", ex);
    }
  }

  private void runGa(ApplicationArguments args) throws InterruptedException {
    String symbol = option(args, "symbol").orElse("BTCUSDT");
    String interval = option(args, "interval").orElse("1m");
    Instant from = option(args, "from").map(this::parseInstant).orElse(null);
    Instant to = option(args, "to").map(this::parseInstant).orElse(null);
    Path strategyPath = option(args, "strategy").map(Path::of).orElse(null);
    Path genomesPath = option(args, "genomes").map(Path::of).orElse(null);
    BigDecimal slippage = option(args, "slippageBps").map(BigDecimal::new).orElse(BigDecimal.ZERO);
    FeeConfig feeConfig = parseFees(option(args, "fees"));
    int pop = option(args, "pop").map(Integer::parseInt).orElse(20);
    int gens = option(args, "gens").map(Integer::parseInt).orElse(10);
    long seed = option(args, "seed").map(Long::parseLong).orElse(42L);
    int workers = option(args, "maxWorkers").map(Integer::parseInt).orElse(Runtime.getRuntime().availableProcessors());
    String runId = option(args, "run-id").orElse("ga-" + System.currentTimeMillis());
    boolean plotEnabled = option(args, "ga.plot").map(Boolean::parseBoolean).orElse(true);
    Path reportsDir = Path.of("reports").resolve("ga");

    BacktestRequest baseRequest =
        new BacktestRequest(
            symbol,
            interval,
            from,
            to,
            strategyPath,
            genomesPath,
            slippage,
            feeConfig.takerFee(),
            feeConfig.makerFee(),
            feeConfig.dynamic,
            seed,
            runId,
            true);
    Evaluator evaluator = new Evaluator(backtestEngine, baseRequest, workers, reportsDir);
    GaRunner runner = new GaRunner(evaluator, pop, gens, 0.2, 3, 2, seed);
    GaProgressReporter reporter = new GaProgressReporter(reportsDir, runId, plotEnabled);
    runner.addListener(reporter);
    Genome best = runner.run();

    Path outPath = option(args, "out").map(Path::of).orElse(Path.of("presets", "best_genomes.yaml"));
    GenomeFile genomeFile = new GenomeFile(best.toBuySection(), best.toSellSection());
    try {
      GenomeIO.write(outPath, genomeFile);
    } catch (Exception ex) {
      log.error("Unable to write genomes to {}", outPath, ex);
    }
    log.info(
        "GA completed. bestFitness={} thresholds=({}, {}) saved={}",
        best.fitness(),
        best.buyThreshold(),
        best.sellThreshold(),
        outPath.toAbsolutePath());
    MetricsSummary metrics = best.metrics();
    if (metrics != null) {
      log.info(
          "Best genome metrics -> WinRate={} ProfitFactor={} MaxDD={} Trades={}",
          asDecimal(metrics.winRate()),
          asDecimal(metrics.profitFactor()),
          asDecimal(metrics.maxDrawdown()),
          metrics.trades());
    }
  }

  private Optional<String> option(ApplicationArguments args, String name) {
    List<String> values = args.getOptionValues(name);
    if (values == null || values.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(values.get(0));
  }

  private Instant parseInstant(String raw) {
    try {
      return Instant.parse(raw);
    } catch (Exception ignore) {
      return LocalDate.parse(raw).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
  }

  private FeeConfig parseFees(Optional<String> value) {
    if (value.isEmpty()) {
      return new FeeConfig(false, null, null);
    }
    String raw = value.get();
    if ("dynamic".equalsIgnoreCase(raw)) {
      return new FeeConfig(true, null, null);
    }
    if (raw.contains(",")) {
      BigDecimal maker = null;
      BigDecimal taker = null;
      for (String part : raw.split(",")) {
        String[] kv = part.split("=");
        if (kv.length == 2) {
          if (kv[0].equalsIgnoreCase("maker")) {
            maker = new BigDecimal(kv[1]);
          } else if (kv[0].equalsIgnoreCase("taker")) {
            taker = new BigDecimal(kv[1]);
          }
        }
      }
      return new FeeConfig(false, taker, maker);
    }
    BigDecimal flat = new BigDecimal(raw);
    return new FeeConfig(false, flat, flat);
  }

  private String asDecimal(BigDecimal value) {
    return value == null ? "n/a" : value.toPlainString();
  }

  private record FeeConfig(boolean dynamic, BigDecimal takerFee, BigDecimal makerFee) {}
}
