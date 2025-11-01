package com.bottrading.research.backtest;

import com.bottrading.model.dto.Kline;
import com.bottrading.strategy.CompositeStrategy;
import com.bottrading.strategy.SignalResult;
import com.bottrading.strategy.SignalSide;
import com.bottrading.strategy.StrategyContext;
import com.bottrading.strategy.StrategyFactory;
import com.bottrading.research.io.DataLoader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    if (klines == null || klines.isEmpty()) {
      throw new IllegalArgumentException("No klines available for backtest");
    }
    CompositeStrategy strategy = override;
    if (strategy == null) {
      strategy =
          request.strategyConfig() != null
              ? strategyFactory.buildFromPath(request.strategyConfig()).orElse(strategyFactory.getStrategy())
              : strategyFactory.getStrategy();
    }

    ExecutionSimulator simulator =
        new ExecutionSimulator(
            request.slippageBps() == null ? BigDecimal.ZERO : request.slippageBps(),
            request.takerFeeBps() == null ? BigDecimal.ZERO : request.takerFeeBps(),
            request.makerFeeBps() == null ? BigDecimal.ZERO : request.makerFeeBps());
    Portfolio portfolio = new Portfolio(startingCapital);
    List<String[]> buffer = new ArrayList<>();
    for (Kline kline : klines) {
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
      if (decision.side() == SignalSide.BUY && !portfolio.hasPosition()) {
        BigDecimal allocation = portfolio.equity().multiply(riskFraction);
        BigDecimal quantity = allocation.divide(price, 8, RoundingMode.DOWN);
        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
          ExecutionResult fill = simulator.simulateBuy(price, quantity, false);
          portfolio.buy(kline.openTime(), fill.price(), fill.quantity(), fill.fee());
        }
      } else if (decision.side() == SignalSide.SELL && portfolio.hasPosition()) {
        ExecutionResult fill = simulator.simulateSell(price, portfolio.positionSize(), false);
        portfolio.sell(kline.openTime(), fill.price(), fill.quantity(), fill.fee());
      }
      portfolio.mark(kline.openTime(), price);
    }
    MetricsSummary metrics = MetricsCalculator.compute(portfolio.trades(), portfolio.equityCurve());
    BacktestResult result = new BacktestResult(request, metrics, portfolio.trades(), portfolio.equityCurve());
    if (reportDirectory != null) {
      reportWriter.write(reportDirectory, result);
    }
    return result;
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
