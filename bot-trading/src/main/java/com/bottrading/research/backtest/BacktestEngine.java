package com.bottrading.research.backtest;

import com.bottrading.model.dto.Kline;
import com.bottrading.research.ga.GenomeStrategyBuilder;
import com.bottrading.research.ga.io.GenomeIO;
import com.bottrading.research.ga.io.GenomeFile;
import com.bottrading.research.backtest.realistic.RealisticExecutionSimulator;
import com.bottrading.strategy.CompositeStrategy;
import com.bottrading.strategy.SignalResult;
import com.bottrading.strategy.SignalSide;
import com.bottrading.strategy.StrategyContext;
import com.bottrading.strategy.StrategyFactory;
import com.bottrading.research.io.DataLoader;
import com.bottrading.research.regime.RegimeFilter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class BacktestEngine {

  private final DataLoader dataLoader;
  private final StrategyFactory strategyFactory;
  private final ReportWriter reportWriter;
  private final BigDecimal startingCapital;

  public BacktestEngine(
      DataLoader dataLoader,
      StrategyFactory strategyFactory,
      ReportWriter reportWriter,
      BigDecimal startingCapital) {
    this.dataLoader = dataLoader;
    this.strategyFactory = strategyFactory;
    this.reportWriter = reportWriter;
    this.startingCapital = startingCapital;
  }

  public BacktestResult run(BacktestRequest request, Path reportDirectory) throws IOException {
    return run(request, reportDirectory, null);
  }

  public BacktestResult run(BacktestRequest request, Path reportDirectory, CompositeStrategy override)
      throws IOException {
    List<Kline> klines =
        dataLoader.load(request.symbol(), request.interval(), request.from(), request.to(), request.useCache());
    klines = applyRegimeFilter(klines, request.regimeFilter());
    if (klines == null || klines.isEmpty()) {
      throw new IllegalArgumentException("No klines available for backtest");
    }
    CompositeStrategy strategy = resolveStrategy(request, override);

    SimpleExecutionSimulator simulator =
        new SimpleExecutionSimulator(
            request.slippageBps() == null ? BigDecimal.ZERO : request.slippageBps(),
            request.takerFeeBps() == null ? BigDecimal.ZERO : request.takerFeeBps(),
            request.makerFeeBps() == null ? BigDecimal.ZERO : request.makerFeeBps());
    RealisticExecutionSimulator realisticSimulator = null;
    if (request.realisticConfig() != null) {
      long seed = request.seed() != null ? request.seed() : System.currentTimeMillis();
      realisticSimulator =
          new RealisticExecutionSimulator(
              request.symbol(),
              request.realisticConfig(),
              request.makerFeeBps(),
              request.takerFeeBps(),
              seed);
    }
    Portfolio portfolio = new Portfolio(startingCapital);
    ExecutionStatistics executionStats = new ExecutionStatistics();
    List<String[]> buffer = new ArrayList<>();
    for (int index = 0; index < klines.size(); index++) {
      Kline kline = klines.get(index);
      buffer.add(toArray(kline));
      BigDecimal volume24h = trailingVolume(buffer, 1440);
      StrategyContext context =
          StrategyContext.builder()
              .symbol(request.symbol())
              .lastPrice(kline.close())
              .volume24h(volume24h)
              .build();
      SignalResult decision = strategy.evaluate(List.copyOf(buffer), context);
      BigDecimal price = kline.close();
      BigDecimal riskFraction = BigDecimal.valueOf(0.1);
      Portfolio.TradeMetadata metadata =
          new Portfolio.TradeMetadata(decision.side(), decision.note(), decision.voters());
      if (decision.side() == SignalSide.BUY && !portfolio.hasPosition()) {
        BigDecimal allocation = portfolio.equity().multiply(riskFraction);
        BigDecimal quantity = allocation.divide(price, 8, RoundingMode.DOWN);
        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
          ExecutionResult fill;
          if (realisticSimulator != null) {
            fill = realisticSimulator.executeEntry(SignalSide.BUY, klines, index, quantity);
          } else {
            fill = simulator.simulateBuy(price, quantity, false);
          }
          executionStats.recordLimitAttempt(quantity, fill);
          portfolio.buy(fill, metadata);
        }
      } else if (decision.side() == SignalSide.SELL && portfolio.hasPosition()) {
        ExecutionResult fill;
        if (realisticSimulator != null) {
          fill = realisticSimulator.executeExit(SignalSide.SELL, klines, index, portfolio.positionSize());
        } else {
          fill = simulator.simulateSell(price, portfolio.positionSize(), false);
        }
        portfolio.sell(fill, metadata);
      }
      portfolio.mark(kline.openTime(), price);
    }
    if (portfolio.hasPosition()) {
      Kline last = klines.get(klines.size() - 1);
      ExecutionResult fill;
      if (realisticSimulator != null) {
        fill =
            realisticSimulator.executeExit(
                SignalSide.SELL, klines, klines.size() - 1, portfolio.positionSize());
      } else {
        fill = simulator.simulateSell(last.close(), portfolio.positionSize(), false);
      }
      portfolio.sell(
          fill,
          new Portfolio.TradeMetadata(
              SignalSide.SELL, "Force exit - end of data", List.of("FORCED_EXIT")));
    }
    MetricsSummary metrics =
        MetricsCalculator.compute(portfolio.trades(), portfolio.equityCurve(), executionStats);
    String dataHash = computeDataHash(request, klines);
    BacktestResult result =
        new BacktestResult(
            request,
            metrics,
            portfolio.trades(),
            portfolio.equityCurve(),
            klines,
            dataHash,
            executionStats);
    if (reportDirectory != null) {
      reportWriter.write(reportDirectory, result);
    }
    return result;
  }

  private List<Kline> applyRegimeFilter(List<Kline> klines, RegimeFilter filter) {
    if (filter == null || !filter.isActive() || klines == null || klines.isEmpty()) {
      return klines;
    }
    List<Kline> filtered = new ArrayList<>();
    for (Kline kline : klines) {
      Instant ts = kline.closeTime() != null ? kline.closeTime() : kline.openTime();
      if (ts != null && filter.allows(ts)) {
        filtered.add(kline);
      }
    }
    return filtered;
  }

  private CompositeStrategy resolveStrategy(BacktestRequest request, CompositeStrategy override) {
    if (override != null) {
      return override;
    }
    if (request.genomesConfig() != null) {
      try {
        GenomeFile genomeFile = GenomeIO.read(request.genomesConfig());
        return GenomeStrategyBuilder.build(genomeFile);
      } catch (IOException ex) {
        throw new IllegalArgumentException("Unable to load genomes configuration", ex);
      }
    }
    if (request.strategyConfig() != null) {
      return strategyFactory
          .buildFromPath(request.strategyConfig())
          .orElseGet(() -> strategyFactory.getStrategy());
    }
    return strategyFactory.getStrategy();
  }

  private String computeDataHash(BacktestRequest request, List<Kline> klines) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      update(digest, request.symbol());
      update(digest, request.interval());
      if (request.from() != null) {
        update(digest, request.from().toString());
      }
      if (request.to() != null) {
        update(digest, request.to().toString());
      }
      for (Kline kline : klines) {
        update(digest, String.valueOf(kline.openTime().toEpochMilli()));
        update(digest, kline.open().toPlainString());
        update(digest, kline.high().toPlainString());
        update(digest, kline.low().toPlainString());
        update(digest, kline.close().toPlainString());
        update(digest, kline.volume().toPlainString());
      }
      byte[] hash = digest.digest();
      StringBuilder builder = new StringBuilder();
      for (byte b : hash) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 algorithm not available", ex);
    }
  }

  private void update(MessageDigest digest, String value) {
    if (value == null) {
      return;
    }
    digest.update(value.getBytes(StandardCharsets.UTF_8));
  }

  private BigDecimal trailingVolume(List<String[]> buffer, int maxBars) {
    int count = Math.min(buffer.size(), maxBars);
    BigDecimal sum = BigDecimal.ZERO;
    for (int i = buffer.size() - count; i < buffer.size(); i++) {
      sum = sum.add(new BigDecimal(buffer.get(i)[5]));
    }
    return sum;
  }

  private String[] toArray(Kline kline) {
    return new String[] {
      String.valueOf(kline.openTime().toEpochMilli()),
      kline.open().toPlainString(),
      kline.high().toPlainString(),
      kline.low().toPlainString(),
      kline.close().toPlainString(),
      kline.volume().toPlainString()
    };
  }
}
