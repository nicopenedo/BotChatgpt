package com.bottrading.service;

import com.bottrading.config.TradingProperties;
import com.bottrading.model.dto.Kline;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.strategy.CompositeStrategy;
import com.bottrading.strategy.SignalResult;
import com.bottrading.strategy.StrategyContext;
import com.bottrading.strategy.StrategyFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StrategyService {

  private static final Logger log = LoggerFactory.getLogger(StrategyService.class);
  private static final int DEFAULT_KLINE_LIMIT = 200;

  private final BinanceClient binanceClient;
  private final StrategyFactory strategyFactory;
  private final TradingProperties tradingProperties;

  public StrategyService(
      BinanceClient binanceClient, StrategyFactory strategyFactory, TradingProperties tradingProperties) {
    this.binanceClient = binanceClient;
    this.strategyFactory = strategyFactory;
    this.tradingProperties = tradingProperties;
  }

  public SignalResult decide(String symbol) {
    String effectiveSymbol = symbol != null ? symbol : tradingProperties.getSymbol();
    List<Kline> klines = binanceClient.getKlines(effectiveSymbol, "1m", DEFAULT_KLINE_LIMIT);
    if (klines == null || klines.isEmpty()) {
      log.warn("No klines available for symbol {}", effectiveSymbol);
      return SignalResult.flat("No klines available");
    }
    BigDecimal volume24h = binanceClient.get24hQuoteVolume(effectiveSymbol);
    Kline last = klines.get(klines.size() - 1);
    StrategyContext context =
        StrategyContext.builder()
            .symbol(effectiveSymbol)
            .lastPrice(last.close())
            .volume24h(volume24h)
            .build();
    CompositeStrategy strategy = strategyFactory.getStrategy();
    SignalResult result = strategy.evaluate(convert(klines), context);
    log.debug(
        "Strategy decision symbol={} side={} confidence={} note={}",
        effectiveSymbol,
        result.side(),
        result.confidence(),
        result.note());
    return result;
  }

  private List<String[]> convert(List<Kline> klines) {
    List<String[]> list = new ArrayList<>(klines.size());
    for (Kline kline : klines) {
      list.add(
          new String[] {
            String.valueOf(kline.openTime().toEpochMilli()),
            kline.open().toPlainString(),
            kline.high().toPlainString(),
            kline.low().toPlainString(),
            kline.close().toPlainString(),
            kline.volume().toPlainString()
          });
    }
    return list;
  }
}
