package com.bottrading.research.backtest;

import com.bottrading.model.dto.Kline;
import com.bottrading.research.io.DataLoader;
import com.bottrading.strategy.CompositeStrategy;
import com.bottrading.strategy.Signal;
import com.bottrading.strategy.SignalResult;
import com.bottrading.strategy.StrategyFactory;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class BacktestEngineTest {

  @Test
  void executesSimpleBacktest() throws IOException {
    List<Kline> klines = new ArrayList<>();
    klines.add(kline(0, 100));
    klines.add(kline(60_000, 101));
    klines.add(kline(120_000, 102));
    klines.add(kline(180_000, 103));
    klines.add(kline(240_000, 104));

    DataLoader loader =
        new DataLoader(null, null) {
          @Override
          public List<Kline> load(
              String symbol, String interval, Instant from, Instant to, boolean useCache) {
            return klines;
          }
        };
    StrategyFactory factory = new StrategyFactory(new DefaultResourceLoader(), new com.bottrading.config.TradingProps());
    ReportWriter reportWriter =
        new ReportWriter(
            new com.bottrading.research.io.CsvWriter(),
            new com.bottrading.research.io.JsonWriter(),
            new com.bottrading.research.io.ChartExporter(new com.bottrading.research.io.CsvWriter()));
    BacktestEngine engine = new BacktestEngine(loader, factory, reportWriter, BigDecimal.valueOf(1000));

    CompositeStrategy strategy = new CompositeStrategy().thresholds(0.5, 0.5);
    strategy.addSignal(new TestSignal(), 1.0);

    BacktestRequest request =
        new BacktestRequest(
            "TEST",
            "1m",
            null,
            null,
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            false,
            null,
            "test",
            false,
            null,
            null);
    BacktestResult result = engine.run(request, null, strategy);

    Assertions.assertEquals(1, result.trades().size());
    Assertions.assertTrue(result.metrics().trades() >= 1);
    Assertions.assertTrue(result.metrics().profitFactor().doubleValue() >= 0);
  }

  private Kline kline(long offset, double close) {
    BigDecimal price = BigDecimal.valueOf(close);
    Instant open = Instant.ofEpochMilli(offset);
    return new Kline(open, open.plusSeconds(60), price, price, price, price, BigDecimal.ONE);
  }

  private static class TestSignal implements Signal {
    @Override
    public SignalResult evaluate(List<String[]> klines) {
      if (klines.size() == 3) {
        return SignalResult.buy(1.0, "buy");
      }
      if (klines.size() == 5) {
        return SignalResult.sell(1.0, "sell");
      }
      return SignalResult.flat("wait");
    }
  }
}
