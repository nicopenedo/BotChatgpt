package com.bottrading.research;

import com.bottrading.research.backtest.BacktestEngine;
import com.bottrading.research.backtest.BacktestRequest;
import com.bottrading.research.ga.Evaluator;
import com.bottrading.research.ga.GaRunner;
import com.bottrading.research.ga.Genome;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
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
    Instant from = option(args, "from").map(Instant::parse).orElse(null);
    Instant to = option(args, "to").map(Instant::parse).orElse(null);
    Path strategyPath = option(args, "strategy").map(Path::of).orElse(null);
    BigDecimal slippage = option(args, "slippageBps").map(BigDecimal::new).orElse(BigDecimal.ZERO);
    BigDecimal fees = option(args, "fees").map(BigDecimal::new).orElse(BigDecimal.ZERO);
    Path output = option(args, "out").map(Path::of).orElse(Path.of("research-output"));

    BacktestRequest request =
        new BacktestRequest(symbol, interval, from, to, strategyPath, slippage, fees, fees, true);
    try {
      backtestEngine.run(request, output);
      log.info("Backtest finished. Reports at {}", output.toAbsolutePath());
    } catch (Exception ex) {
      log.error("Backtest failed", ex);
    }
  }

  private void runGa(ApplicationArguments args) throws InterruptedException {
    String symbol = option(args, "symbol").orElse("BTCUSDT");
    String interval = option(args, "interval").orElse("1m");
    Instant from = option(args, "from").map(Instant::parse).orElse(null);
    Instant to = option(args, "to").map(Instant::parse).orElse(null);
    Path strategyPath = option(args, "strategy").map(Path::of).orElse(null);
    BigDecimal slippage = option(args, "slippageBps").map(BigDecimal::new).orElse(BigDecimal.ZERO);
    BigDecimal fees = option(args, "fees").map(BigDecimal::new).orElse(BigDecimal.ZERO);
    int pop = option(args, "pop").map(Integer::parseInt).orElse(20);
    int gens = option(args, "gens").map(Integer::parseInt).orElse(10);
    long seed = option(args, "seed").map(Long::parseLong).orElse(42L);
    int workers = option(args, "maxWorkers").map(Integer::parseInt).orElse(Runtime.getRuntime().availableProcessors());

    BacktestRequest baseRequest =
        new BacktestRequest(symbol, interval, from, to, strategyPath, slippage, fees, fees, true);
    Evaluator evaluator = new Evaluator(backtestEngine, baseRequest, workers, Path.of("ga-output"));
    GaRunner runner = new GaRunner(evaluator, pop, gens, 0.2, 3, 2, seed);
    Genome best = runner.run();
    log.info("GA best fitness={} thresholds=({}, {})", best.fitness(), best.buyThreshold(), best.sellThreshold());
    log.info("Best genome metrics: {}", best.metrics());
  }

  private Optional<String> option(ApplicationArguments args, String name) {
    List<String> values = args.getOptionValues(name);
    if (values == null || values.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(values.get(0));
  }
}
