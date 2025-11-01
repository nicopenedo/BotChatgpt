package com.bottrading.strategy.router;

import com.bottrading.config.TradingProps;
import com.bottrading.research.regime.Regime;
import com.bottrading.strategy.CompositeStrategy;
import com.bottrading.strategy.StrategyFactory;
import com.bottrading.strategy.StrategyFactory.RouterRule;
import com.bottrading.strategy.StrategyFactory.StrategyCatalog;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class StrategyRouter {

  private final TradingProps tradingProps;
  private final StrategyFactory strategyFactory;
  private final MeterRegistry meterRegistry;

  public StrategyRouter(
      TradingProps tradingProps, StrategyFactory strategyFactory, MeterRegistry meterRegistry) {
    this.tradingProps = tradingProps;
    this.strategyFactory = strategyFactory;
    this.meterRegistry = meterRegistry;
  }

  public Selection select(String symbol, Regime regime) {
    StrategyCatalog catalog = strategyFactory.getCatalog();
    String preset = resolvePreset(catalog, regime);
    CompositeStrategy strategy = strategyFactory.getStrategy(preset);
    meterRegistry.counter("router.selections", Tags.of("symbol", symbol, "preset", preset)).increment();
    return new Selection(preset, strategy, regime);
  }

  private String resolvePreset(StrategyCatalog catalog, Regime regime) {
    if (!tradingProps.getRouter().isEnabled() || regime == null) {
      return catalog.defaultPreset();
    }
    List<RouterRule> rules = strategyFactory.getRouterRules();
    for (RouterRule rule : rules) {
      if (matches(rule, regime)) {
        return rule.preset();
      }
    }
    return catalog.defaultPreset();
  }

  private boolean matches(RouterRule rule, Regime regime) {
    if (rule == null) {
      return false;
    }
    boolean trendMatches =
        rule.trend() == null || Objects.equals(rule.trend(), regime.trend());
    boolean volMatches =
        rule.volatility() == null || Objects.equals(rule.volatility(), regime.volatility());
    return trendMatches && volMatches;
  }

  public record Selection(String preset, CompositeStrategy strategy, Regime regime) {}
}
