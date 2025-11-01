package com.bottrading.strategy.router;

import com.bottrading.config.TradingProps;
import com.bottrading.research.regime.Regime;
import com.bottrading.research.regime.RegimeTrend;
import com.bottrading.research.regime.RegimeVolatility;
import com.bottrading.strategy.CompositeStrategy;
import com.bottrading.strategy.StrategyFactory;
import com.bottrading.strategy.StrategyFactory.RouterConfig;
import com.bottrading.strategy.StrategyFactory.RouterRule;
import com.bottrading.strategy.StrategyFactory.StrategyCatalog;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class StrategyRouterTest {

  @Test
  void appliesHysteresisAndFallback() {
    CompositeStrategy upStrategy = new CompositeStrategy();
    CompositeStrategy downStrategy = new CompositeStrategy();
    CompositeStrategy rangeStrategy = new CompositeStrategy();
    CompositeStrategy globalStrategy = new CompositeStrategy();

    Map<String, CompositeStrategy> presets =
        Map.of(
            "preset_up", upStrategy,
            "preset_down", downStrategy,
            "preset_range", rangeStrategy,
            "preset_global", globalStrategy);

    RouterConfig routerConfig =
        new RouterConfig(
            List.of(
                new RouterRule(RegimeTrend.UP, null, "preset_up"),
                new RouterRule(RegimeTrend.DOWN, null, "preset_down"),
                new RouterRule(RegimeTrend.RANGE, null, "preset_range")),
            "preset_global",
            3);

    StrategyCatalog catalog = new StrategyCatalog(presets, routerConfig, "preset_global");

    StrategyFactory factory =
        new StrategyFactory(new DefaultResourceLoader(), new TradingProps()) {
          @Override
          public StrategyCatalog getCatalog() {
            return catalog;
          }

          @Override
          public CompositeStrategy getStrategy(String preset) {
            return presets.get(preset);
          }

          @Override
          public List<RouterRule> getRouterRules() {
            return routerConfig.rules();
          }
        };

    TradingProps props = new TradingProps();
    props.getRouter().setEnabled(true);
    StrategyRouter router = new StrategyRouter(props, factory, new SimpleMeterRegistry());

    Regime upRegime = new Regime("TEST", "1m", RegimeTrend.UP, RegimeVolatility.LO, 0, 0, 0, Instant.now());
    Regime downRegime =
        new Regime("TEST", "1m", RegimeTrend.DOWN, RegimeVolatility.LO, 0, 0, 0, Instant.now());

    // Initial selection should use fallback until hysteresis satisfied
    Assertions.assertEquals("preset_global", router.select("BTC", upRegime).preset());
    Assertions.assertEquals("preset_global", router.select("BTC", upRegime).preset());
    Assertions.assertEquals("preset_up", router.select("BTC", upRegime).preset());

    // Switch to down after hysteresis
    Assertions.assertEquals("preset_up", router.select("BTC", downRegime).preset());
    Assertions.assertEquals("preset_up", router.select("BTC", downRegime).preset());
    Assertions.assertEquals("preset_down", router.select("BTC", downRegime).preset());

    // Null regime falls back to global
    Assertions.assertEquals("preset_global", router.select("BTC", null).preset());
  }
}
