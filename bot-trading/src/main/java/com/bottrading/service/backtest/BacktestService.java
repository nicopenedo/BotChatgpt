package com.bottrading.service.backtest;

import com.bottrading.model.dto.Kline;
import com.bottrading.model.dto.StrategySignal;
import com.bottrading.service.strategy.ScalpingSmaStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BacktestService implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BacktestService.class);
  private final ScalpingSmaStrategy strategy;

  public BacktestService(ScalpingSmaStrategy strategy) {
    this.strategy = strategy;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    if (!args.containsOption("backtest.file")) {
      return;
    }
    Path file = Path.of(args.getOptionValues("backtest.file").get(0));
    BigDecimal startBalance =
        args.containsOption("backtest.start-balance")
            ? new BigDecimal(args.getOptionValues("backtest.start-balance").get(0))
            : BigDecimal.valueOf(1000);
    BigDecimal slippageBps =
        args.containsOption("backtest.slippage-bps")
            ? new BigDecimal(args.getOptionValues("backtest.slippage-bps").get(0))
            : BigDecimal.valueOf(5);
    log.info("Running backtest with file={} startBalance={} slippageBps={}bps", file, startBalance, slippageBps);

    List<Kline> klines = readKlines(file);
    BigDecimal cash = startBalance;
    BigDecimal position = BigDecimal.ZERO;
    BigDecimal entryPrice = BigDecimal.ZERO;
    BigDecimal realizedPnl = BigDecimal.ZERO;
    BigDecimal equityPeak = startBalance;
    BigDecimal maxDrawdown = BigDecimal.ZERO;
    int wins = 0;
    int losses = 0;

    for (int i = 30; i < klines.size(); i++) {
      List<Kline> window = klines.subList(0, i + 1);
      Kline current = klines.get(i);
      BigDecimal lastPrice = current.close();
      StrategySignal signal = strategy.evaluate(window, current.volume(), lastPrice);
      if (signal == null) {
        continue;
      }
      BigDecimal slippage = lastPrice.multiply(slippageBps).divide(BigDecimal.valueOf(10000), MathContext.DECIMAL64);
      if (signal.side().name().equals("BUY") && position.compareTo(BigDecimal.ZERO) == 0) {
        BigDecimal size = cash.multiply(BigDecimal.valueOf(0.1)).divide(lastPrice, 8, RoundingMode.DOWN);
        entryPrice = lastPrice.add(slippage);
        position = size;
        cash = cash.subtract(size.multiply(entryPrice));
        log.debug("Buy {} at {}", size, entryPrice);
      } else if (signal.side().name().equals("SELL") && position.compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal exitPrice = lastPrice.subtract(slippage);
        BigDecimal proceeds = position.multiply(exitPrice);
        cash = cash.add(proceeds);
        BigDecimal pnl = proceeds.subtract(position.multiply(entryPrice));
        realizedPnl = realizedPnl.add(pnl);
        if (pnl.compareTo(BigDecimal.ZERO) > 0) {
          wins++;
        } else {
          losses++;
        }
        position = BigDecimal.ZERO;
        entryPrice = BigDecimal.ZERO;
        log.debug("Sell {} at {} pnl={}", position, exitPrice, pnl);
      }
      BigDecimal equity = cash.add(position.multiply(lastPrice));
      equityPeak = equityPeak.max(equity);
      BigDecimal drawdown = equityPeak.subtract(equity);
      maxDrawdown = maxDrawdown.max(drawdown);
    }

    BigDecimal endEquity = cash.add(position.multiply(klines.get(klines.size() - 1).close()));
    BigDecimal pctReturn = endEquity.subtract(startBalance).divide(startBalance, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    BigDecimal sharpe =
        realizedPnl.divide(startBalance, 8, RoundingMode.HALF_UP)
            .divide(BigDecimal.valueOf(Math.sqrt(252)), 8, RoundingMode.HALF_UP);
    log.info(
        "Backtest result equity={} returnPct={} wins={} losses={} maxDrawdown={} sharpe={}",
        endEquity,
        pctReturn,
        wins,
        losses,
        maxDrawdown,
        sharpe);
  }

  private List<Kline> readKlines(Path file) throws Exception {
    String json = Files.readString(file);
    List<List<Object>> raw = new ObjectMapper().readValue(json, List.class);
    List<Kline> klines = new ArrayList<>();
    for (List<Object> entry : raw) {
      klines.add(
          new Kline(
              Instant.ofEpochMilli(Long.parseLong(entry.get(0).toString())),
              new BigDecimal(entry.get(1).toString()),
              new BigDecimal(entry.get(2).toString()),
              new BigDecimal(entry.get(3).toString()),
              new BigDecimal(entry.get(4).toString()),
              new BigDecimal(entry.get(5).toString())));
    }
    return klines;
  }
}
