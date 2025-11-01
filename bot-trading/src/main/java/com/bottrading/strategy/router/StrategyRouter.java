package com.bottrading.strategy.router;

import com.bottrading.config.TradingProps;
import com.bottrading.research.regime.Regime;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.service.preset.PresetService;
import com.bottrading.strategy.CompositeStrategy;
import com.bottrading.strategy.StrategyFactory;
import com.bottrading.strategy.StrategyFactory.RouterRule;
import com.bottrading.strategy.StrategyFactory.StrategyCatalog;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class StrategyRouter {

  private final TradingProps tradingProps;
  private final StrategyFactory strategyFactory;
  private final MeterRegistry meterRegistry;
  private final PresetService presetService;
  private final Map<String, RouterState> states = new ConcurrentHashMap<>();

  public StrategyRouter(
      TradingProps tradingProps,
      StrategyFactory strategyFactory,
      MeterRegistry meterRegistry,
      PresetService presetService) {
    this.tradingProps = tradingProps;
    this.strategyFactory = strategyFactory;
    this.meterRegistry = meterRegistry;
    this.presetService = presetService;
  }

  public Selection select(String symbol, Regime regime) {
    StrategyCatalog catalog = strategyFactory.getCatalog();
    StrategyFactory.RouterConfig routerConfig = catalog.routerConfig();
    String fallback = routerConfig.fallback() != null ? routerConfig.fallback() : catalog.defaultPreset();
    int hysteresis = Math.max(1, routerConfig.hysteresis());
    String target = resolvePreset(catalog, routerConfig.rules(), fallback, regime);
    if (regime != null) {
      Optional<String> activePreset =
          presetService
              .getActivePreset(regime.trend(), OrderSide.BUY)
              .map(preset -> deriveStrategyKey(preset.getParamsJson(), target));
      if (activePreset.isPresent()) {
        target = activePreset.get();
      }
    }
    RouterState state = states.computeIfAbsent(symbol, key -> new RouterState(fallback));
    String preset = state.update(target, hysteresis);
    CompositeStrategy strategy = strategyFactory.getStrategy(preset);
    meterRegistry.counter("router.selections", Tags.of("symbol", symbol, "preset", preset)).increment();
    return new Selection(preset, strategy, regime);
  }

  private String resolvePreset(StrategyCatalog catalog, List<RouterRule> rules, String fallback, Regime regime) {
    if (!tradingProps.getRouter().isEnabled() || regime == null) {
      return fallback;
    }
    for (RouterRule rule : rules) {
      if (matches(rule, regime)) {
        return rule.preset();
      }
    }
    return fallback;
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

  private String deriveStrategyKey(Map<String, Object> params, String fallback) {
    if (params == null || params.isEmpty()) {
      return fallback;
    }
    Object explicit = params.getOrDefault("presetKey", params.get("strategy"));
    if (explicit != null) {
      return explicit.toString();
    }
    return fallback;
  }

  public record Selection(String preset, CompositeStrategy strategy, Regime regime) {}

  private static final class RouterState {
    private String currentPreset;
    private String pendingPreset;
    private int confirmations;

    private RouterState(String initialPreset) {
      this.currentPreset = initialPreset;
    }

    private synchronized String update(String target, int hysteresis) {
      if (target == null || target.equals(currentPreset)) {
        pendingPreset = null;
        confirmations = 0;
        if (target != null) {
          currentPreset = target;
        }
        return currentPreset;
      }
      if (hysteresis <= 1) {
        currentPreset = target;
        pendingPreset = null;
        confirmations = 0;
        return currentPreset;
      }
      if (target.equals(pendingPreset)) {
        confirmations++;
        if (confirmations >= hysteresis) {
          currentPreset = target;
          pendingPreset = null;
          confirmations = 0;
        }
      } else {
        pendingPreset = target;
        confirmations = 1;
      }
      return currentPreset;
    }
  }
}
