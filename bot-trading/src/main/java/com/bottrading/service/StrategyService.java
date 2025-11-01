package com.bottrading.service;

import com.bottrading.config.TradingProps;
import com.bottrading.model.dto.Kline;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.strategy.CompositeStrategy;
import com.bottrading.strategy.SignalResult;
import com.bottrading.strategy.StrategyContext;
import com.bottrading.strategy.StrategyFactory;
import com.bottrading.strategy.StrategyDecision;
import com.bottrading.research.regime.Regime;
import com.bottrading.research.regime.RegimeEngine;
import com.bottrading.strategy.router.StrategyRouter;
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
  private final TradingProps tradingProps;
  private final RegimeEngine regimeEngine;
  private final StrategyRouter strategyRouter;

  public StrategyService(
      BinanceClient binanceClient,
      StrategyFactory strategyFactory,
      TradingProps tradingProps,
      RegimeEngine regimeEngine,
      StrategyRouter strategyRouter) {
    this.binanceClient = binanceClient;
    this.strategyFactory = strategyFactory;
    this.tradingProps = tradingProps;
    this.regimeEngine = regimeEngine;
    this.strategyRouter = strategyRouter;
  }

  public StrategyDecision decide(String symbol) {
    String effectiveSymbol = symbol != null ? symbol : tradingProps.getSymbol();
    String interval = tradingProps.getInterval();
    List<Kline> klines = binanceClient.getKlines(effectiveSymbol, interval, DEFAULT_KLINE_LIMIT);
    if (klines == null || klines.isEmpty()) {
      log.warn("No klines available for symbol {}", effectiveSymbol);
      StrategyContext context =
          StrategyContext.builder().symbol(effectiveSymbol).preset("default").build();
      return new StrategyDecision(SignalResult.flat("No klines available"), context, null, "default");
    }
    BigDecimal volume24h = binanceClient.get24hQuoteVolume(effectiveSymbol);
    Kline last = klines.get(klines.size() - 1);
    Regime regime = regimeEngine.classify(effectiveSymbol, interval, klines);
    StrategyRouter.Selection selection = strategyRouter.select(effectiveSymbol, regime);
    StrategyContext context =
        StrategyContext.builder()
            .symbol(effectiveSymbol)
            .lastPrice(last.close())
            .volume24h(volume24h)
            .regime(regime)
            .preset(selection.preset())
            .normalizedAtr(Double.isNaN(regime.normalizedAtr()) ? null : regime.normalizedAtr())
            .adx(Double.isNaN(regime.adx()) ? null : regime.adx())
            .rangeScore(Double.isNaN(regime.rangeScore()) ? null : regime.rangeScore())
            .build();
    CompositeStrategy strategy = selection.strategy();
    SignalResult result = strategy.evaluate(convert(klines), context);
    log.debug(
        "Strategy decision symbol={} preset={} side={} confidence={} note={}",
        effectiveSymbol,
        selection.preset(),
        result.side(),
        result.confidence(),
        result.note());
    return new StrategyDecision(result, context, regime, selection.preset());
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
