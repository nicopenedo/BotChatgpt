package com.bottrading.service;

import com.bottrading.bandit.BanditContext;
import com.bottrading.bandit.BanditContextFactory;
import com.bottrading.bandit.BanditSelector;
import com.bottrading.bandit.BanditSelection;
import com.bottrading.config.TradingProps;
import com.bottrading.model.dto.Kline;
import com.bottrading.model.entity.PresetVersion;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.preset.PresetService;
import com.bottrading.strategy.CompositeStrategy;
import com.bottrading.strategy.SignalResult;
import com.bottrading.strategy.StrategyContext;
import com.bottrading.strategy.StrategyFactory;
import com.bottrading.strategy.StrategyDecision;
import com.bottrading.research.regime.Regime;
import com.bottrading.research.regime.RegimeEngine;
import com.bottrading.service.risk.RiskGuard;
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
  private final RiskGuard riskGuard;
  private final BanditSelector banditSelector;
  private final BanditContextFactory banditContextFactory;
  private final PresetService presetService;

  public StrategyService(
      BinanceClient binanceClient,
      StrategyFactory strategyFactory,
      TradingProps tradingProps,
      RegimeEngine regimeEngine,
      StrategyRouter strategyRouter,
      RiskGuard riskGuard,
      BanditSelector banditSelector,
      BanditContextFactory banditContextFactory,
      PresetService presetService) {
    this.binanceClient = binanceClient;
    this.strategyFactory = strategyFactory;
    this.tradingProps = tradingProps;
    this.regimeEngine = regimeEngine;
    this.strategyRouter = strategyRouter;
    this.riskGuard = riskGuard;
    this.banditSelector = banditSelector;
    this.banditContextFactory = banditContextFactory;
    this.presetService = presetService;
  }

  public StrategyDecision decide(String symbol) {
    String effectiveSymbol = symbol != null ? symbol : tradingProps.getSymbol();
    if (!riskGuard.canOpen(effectiveSymbol)) {
      log.debug("Risk guard blocked strategy decision for {}", effectiveSymbol);
      StrategyContext context =
          StrategyContext.builder().symbol(effectiveSymbol).preset("default").build();
      return new StrategyDecision(
          SignalResult.flat("Risk guard pause in effect"), context, null, "default", null);
    }
    String interval = tradingProps.getInterval();
    List<Kline> klines = binanceClient.getKlines(effectiveSymbol, interval, DEFAULT_KLINE_LIMIT);
    if (klines == null || klines.isEmpty()) {
      log.warn("No klines available for symbol {}", effectiveSymbol);
      StrategyContext context =
          StrategyContext.builder().symbol(effectiveSymbol).preset("default").build();
      return new StrategyDecision(
          SignalResult.flat("No klines available"), context, null, "default", null);
    }
    BigDecimal volume24h = binanceClient.get24hQuoteVolume(effectiveSymbol);
    Kline last = klines.get(klines.size() - 1);
    Regime regime = regimeEngine.classify(effectiveSymbol, interval, klines);
    StrategyRouter.Selection selection = strategyRouter.select(effectiveSymbol, regime);
    StrategyContext.Builder contextBuilder =
        StrategyContext.builder()
            .symbol(effectiveSymbol)
            .lastPrice(last.close())
            .volume24h(volume24h)
            .asOf(last.closeTime())
            .regime(regime)
            .normalizedAtr(Double.isNaN(regime.normalizedAtr()) ? null : regime.normalizedAtr())
            .adx(Double.isNaN(regime.adx()) ? null : regime.adx())
            .rangeScore(Double.isNaN(regime.rangeScore()) ? null : regime.rangeScore());

    String presetKey = selection.preset();
    contextBuilder.preset(presetKey);
    StrategyContext contextForBandit = contextBuilder.build();

    BanditSelection banditSelection = null;
    CompositeStrategy strategy = selection.strategy();
    if (banditSelector != null) {
      BanditContext banditContext =
          banditContextFactory.build(regime, contextForBandit, null, null, null, null);
      BanditSelector.BanditSelectionResult banditResult =
          banditSelector.pickPresetOrFallback(effectiveSymbol, regime, OrderSide.BUY, banditContext);
      if (banditResult.eligible() && banditResult.selection() != null) {
        try {
          PresetVersion preset = presetService.getPreset(banditResult.selection().presetId());
          String derivedKey = deriveStrategyKey(preset.getParamsJson(), presetKey);
          if (derivedKey != null) {
            strategy = strategyFactory.getStrategy(derivedKey);
            presetKey = derivedKey;
            banditSelection =
                new BanditSelection(
                    banditResult.selection().armId(),
                    banditResult.selection().presetId(),
                    banditResult.selection().role(),
                    banditResult.selection().decisionId(),
                    banditResult.selection().context(),
                    derivedKey);
            contextBuilder.preset(presetKey);
          }
        } catch (IllegalArgumentException ex) {
          log.warn("Failed to hydrate preset {}: {}", banditResult.selection().presetId(), ex.getMessage());
        }
      }
    }

    StrategyContext context = contextBuilder.build();
    SignalResult result = strategy.evaluate(convert(klines), context);
    log.debug(
        "Strategy decision symbol={} preset={} side={} confidence={} note={}",
        effectiveSymbol,
        presetKey,
        result.side(),
        result.confidence(),
        result.note());
    return new StrategyDecision(result, context, regime, presetKey, banditSelection);
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

  private String deriveStrategyKey(java.util.Map<String, Object> params, String fallback) {
    if (params == null || params.isEmpty()) {
      return fallback;
    }
    Object explicit = params.getOrDefault("presetKey", params.get("strategy"));
    if (explicit != null) {
      return explicit.toString();
    }
    return fallback;
  }
}
