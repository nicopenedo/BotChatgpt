package com.bottrading.strategy;

import com.bottrading.config.TradingProps;
import com.bottrading.research.regime.RegimeTrend;
import com.bottrading.research.regime.RegimeVolatility;
import com.bottrading.strategy.signals.AdxFilter;
import com.bottrading.strategy.signals.AtrVolatilityFilter;
import com.bottrading.strategy.signals.BollingerBandsSignal;
import com.bottrading.strategy.signals.DonchianChannelSignal;
import com.bottrading.strategy.signals.EmaCrossoverSignal;
import com.bottrading.strategy.signals.MacdSignal;
import com.bottrading.strategy.signals.RsiSignal;
import com.bottrading.strategy.signals.SmaCrossoverSignal;
import com.bottrading.strategy.signals.StochasticSignal;
import com.bottrading.strategy.signals.SupertrendSignal;
import com.bottrading.strategy.signals.Volume24hFilter;
import com.bottrading.strategy.signals.VwapSignal;
import com.bottrading.strategy.Signal;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class StrategyFactory {

  private static final Logger log = LoggerFactory.getLogger(StrategyFactory.class);

  private final ResourceLoader resourceLoader;
  private final TradingProps tradingProps;
  private volatile StrategyCatalog catalog;

  public StrategyFactory(
      ResourceLoader resourceLoader, TradingProps tradingProps) {
    this.resourceLoader = resourceLoader;
    this.tradingProps = tradingProps;
    this.catalog = loadFromYaml().orElseGet(this::fromDefaults);
  }

  public CompositeStrategy getStrategy() {
    StrategyCatalog current = catalog;
    if (current == null) {
      current = fromDefaults();
      catalog = current;
    }
    return current.strategy(current.defaultPreset());
  }

  public CompositeStrategy getStrategy(String preset) {
    StrategyCatalog current = catalog;
    if (current == null) {
      current = fromDefaults();
      catalog = current;
    }
    return current.strategy(preset);
  }

  public List<RouterRule> getRouterRules() {
    StrategyCatalog current = catalog;
    if (current == null) {
      current = fromDefaults();
      catalog = current;
    }
    return current.routerConfig().rules();
  }

  public StrategyCatalog getCatalog() {
    StrategyCatalog current = catalog;
    if (current == null) {
      current = fromDefaults();
      catalog = current;
    }
    return current;
  }

  public synchronized void reload() {
    catalog = loadFromYaml().orElseGet(this::fromDefaults);
  }

  private Optional<StrategyCatalog> loadFromYaml() {
    Resource resource = resourceLoader.getResource("classpath:strategy.yml");
    if (!resource.exists()) {
      log.warn("strategy.yml not found on classpath; using defaults");
      return Optional.empty();
    }
    try (InputStream input = resource.getInputStream()) {
      Yaml yaml = new Yaml();
      Object loaded = yaml.load(input);
      return buildFromRoot(loaded);
    } catch (IOException ex) {
      log.error("Unable to read strategy.yml", ex);
      return Optional.empty();
    }
  }

  public Optional<StrategyCatalog> buildFromPath(Path path) {
    try (InputStream input = Files.newInputStream(path)) {
      Yaml yaml = new Yaml();
      Object loaded = yaml.load(input);
      return buildFromRoot(loaded);
    } catch (IOException ex) {
      log.error("Unable to load strategy from {}", path, ex);
      return Optional.empty();
    }
  }

  private Optional<StrategyCatalog> buildFromRoot(Object loaded) {
    if (!(loaded instanceof Map<?, ?> rootMap)) {
      log.warn("Strategy configuration empty or invalid, falling back to defaults");
      return Optional.empty();
    }
    Map<String, Object> baseSections = extractBaseSections(rootMap);
    CompositeStrategy defaultStrategy = buildComposite(baseSections);

    Map<String, CompositeStrategy> presets = new HashMap<>();
    presets.put("default", defaultStrategy);

    Map<String, Object> presetConfigs = castMap(rootMap.get("presets"));
    if (presetConfigs != null) {
      for (Map.Entry<String, Object> entry : presetConfigs.entrySet()) {
        Map<String, Object> presetMap = castMap(entry.getValue());
        Map<String, Object> merged = new HashMap<>(baseSections);
        if (presetMap != null) {
          if (presetMap.containsKey("thresholds")) {
            merged.put("thresholds", presetMap.get("thresholds"));
          }
          if (presetMap.containsKey("filters")) {
            merged.put("filters", presetMap.get("filters"));
          }
          if (presetMap.containsKey("signals")) {
            merged.put("signals", presetMap.get("signals"));
          }
        }
        presets.put(entry.getKey(), buildComposite(merged));
      }
    }

    RouterConfig routerConfig = parseRouterConfig(rootMap.get("router"));
    return Optional.of(new StrategyCatalog(presets, routerConfig, "default"));
  }

  private StrategyCatalog fromDefaults() {
    CompositeStrategy strategy = new CompositeStrategy();
    strategy.thresholds(1.5, 1.5);
    strategy.addFilter(new Volume24hFilter(tradingProps.getMinVolume24h().doubleValue()));
    strategy.addFilter(new AtrVolatilityFilter(14, 5));
    strategy.addSignal(new SmaCrossoverSignal(9, 21, 0.8), 1.0);
    strategy.addSignal(new MacdSignal(12, 26, 9, 0.7), 1.0);
    strategy.addSignal(new RsiSignal(14, 30, 70, 50, 0.6), 0.8);
    strategy.addSignal(new BollingerBandsSignal(20, 2.0, 0.5), 0.6);
    Map<String, CompositeStrategy> presets = Map.of("default", strategy);
    return new StrategyCatalog(presets, new RouterConfig(List.of(), null, 1), "default");
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> castList(Object value) {
    if (value instanceof List<?> list) {
      return (List<Map<String, Object>>) list;
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> castMap(Object value) {
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return null;
  }

  private double readDouble(Object value, double defaultValue) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value instanceof String str) {
      try {
        return Double.parseDouble(str);
      } catch (NumberFormatException ignore) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  private int readInt(Object value, int defaultValue) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String str) {
      try {
        return Integer.parseInt(str);
      } catch (NumberFormatException ignore) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  private boolean readBoolean(Object value, boolean defaultValue) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    if (value instanceof String str) {
      return Boolean.parseBoolean(str);
    }
    return defaultValue;
  }

  private Signal buildSignal(Map<String, Object> config) {
    if (config == null || !config.containsKey("type")) {
      return null;
    }
    String type = config.get("type").toString().toUpperCase();
    Map<String, Object> params = Optional.ofNullable(castMap(config.get("params"))).orElse(Map.of());
    double confidence = readDouble(config.getOrDefault("confidence", 1.0), 1.0);
    return switch (type) {
      case "SMA_CROSS" ->
          new SmaCrossoverSignal(
              readInt(params.get("fast"), 9), readInt(params.get("slow"), 21), confidence);
      case "EMA_CROSS" ->
          new EmaCrossoverSignal(
              readInt(params.get("fast"), 12), readInt(params.get("slow"), 26), confidence);
      case "MACD" ->
          new MacdSignal(
              readInt(params.get("fast"), 12),
              readInt(params.get("slow"), 26),
              readInt(params.get("signal"), 9),
              confidence);
      case "RSI" ->
          new RsiSignal(
              readInt(params.get("period"), 14),
              readDouble(params.get("lower"), 30),
              readDouble(params.get("upper"), 70),
              readInt(params.get("trendSma"), 50),
              confidence);
      case "BOLLINGER" ->
          new BollingerBandsSignal(
              readInt(params.get("period"), 20), readDouble(params.get("stdDevs"), 2), confidence);
      case "SUPERTREND" ->
          new SupertrendSignal(
              readInt(params.get("atrPeriod"), 10), readDouble(params.get("multiplier"), 3), confidence);
      case "STOCHASTIC" ->
          new StochasticSignal(
              readInt(params.get("k"), 14), readInt(params.get("d"), 3), confidence);
      case "DONCHIAN" ->
          new DonchianChannelSignal(readInt(params.get("period"), 20), confidence);
      case "VWAP" ->
          new VwapSignal(
              readBoolean(params.getOrDefault("confirmation", Boolean.FALSE), false),
              readInt(params.get("confirmationBars"), 3),
              confidence);
      case "ATR_FILTER" ->
          new AtrVolatilityFilter(readInt(params.get("period"), 14), readDouble(params.get("minAtr"), 5));
      case "ADX_FILTER" ->
          new AdxFilter(readInt(params.get("period"), 14), readDouble(params.get("minAdx"), 20));
      case "VOLUME24H_FILTER" ->
          new Volume24hFilter(
              params != null && params.containsKey("minVolume")
                  ? readDouble(params.get("minVolume"), tradingProps.getMinVolume24h().doubleValue())
                  : tradingProps.getMinVolume24h().doubleValue());
      default -> {
        log.warn("Unknown signal type {}", type);
        yield null;
      }
    };
  }

  private Map<String, Object> extractBaseSections(Map<String, Object> rootMap) {
    Map<String, Object> base = new HashMap<>();
    if (rootMap.containsKey("thresholds")) {
      base.put("thresholds", rootMap.get("thresholds"));
    }
    if (rootMap.containsKey("filters")) {
      base.put("filters", rootMap.get("filters"));
    }
    if (rootMap.containsKey("signals")) {
      base.put("signals", rootMap.get("signals"));
    }
    return base;
  }

  private CompositeStrategy buildComposite(Map<String, Object> sections) {
    CompositeStrategy strategy = new CompositeStrategy();
    Map<String, Object> thresholds = castMap(sections.get("thresholds"));
    double buyThreshold = thresholds != null ? readDouble(thresholds.get("buy"), 1.0) : 1.0;
    double sellThreshold = thresholds != null ? readDouble(thresholds.get("sell"), 1.0) : 1.0;
    strategy.thresholds(buyThreshold, sellThreshold);

    List<Map<String, Object>> filters = castList(sections.get("filters"));
    if (filters != null) {
      for (Map<String, Object> filterConfig : filters) {
        Signal filter = buildSignal(filterConfig);
        if (filter != null) {
          strategy.addFilter(filter);
        }
      }
    }

    List<Map<String, Object>> signals = castList(sections.get("signals"));
    if (signals != null) {
      for (Map<String, Object> signalConfig : signals) {
        Signal signal = buildSignal(signalConfig);
        if (signal != null) {
          double weight = readDouble(signalConfig.getOrDefault("weight", 1.0), 1.0);
          strategy.addSignal(signal, weight);
        }
      }
    }
    return strategy;
  }

  private RouterConfig parseRouterConfig(Object routerConfig) {
    Map<String, Object> router = castMap(routerConfig);
    if (router == null) {
      return new RouterConfig(List.of(), null, 1);
    }
    List<Map<String, Object>> rulesConfig = castList(router.get("rules"));
    List<RouterRule> rules = new ArrayList<>();
    if (rulesConfig != null) {
      for (Map<String, Object> ruleConfig : rulesConfig) {
        Map<String, Object> when = castMap(ruleConfig.get("when"));
        String preset = Optional.ofNullable(ruleConfig.get("use")).map(Object::toString).orElse("default");
        RegimeTrend trend = null;
        RegimeVolatility vol = null;
        if (when != null) {
          trend = parseTrend(when.get("trend"));
          vol = parseVolatility(when.get("vol"));
        }
        rules.add(new RouterRule(trend, vol, preset));
      }
    }
    String fallback = Optional.ofNullable(router.get("fallback")).map(Object::toString).orElse(null);
    int hysteresis = readInt(router.getOrDefault("hysteresis", 1), 1);
    return new RouterConfig(List.copyOf(rules), fallback, Math.max(1, hysteresis));
  }

  private List<RouterRule> parseRouterRules(Object routerConfig) {
    return parseRouterConfig(routerConfig).rules();
  }

  private RegimeTrend parseTrend(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return RegimeTrend.valueOf(value.toString().toUpperCase());
    } catch (IllegalArgumentException ex) {
      log.warn("Unknown trend regime {}", value);
      return null;
    }
  }

  private RegimeVolatility parseVolatility(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return RegimeVolatility.valueOf(value.toString().toUpperCase());
    } catch (IllegalArgumentException ex) {
      log.warn("Unknown volatility regime {}", value);
      return null;
    }
  }

  public record RouterRule(RegimeTrend trend, RegimeVolatility volatility, String preset) {}

  public record RouterConfig(List<RouterRule> rules, String fallback, int hysteresis) {}

  public static final class StrategyCatalog {
    private final Map<String, CompositeStrategy> presets;
    private final RouterConfig routerConfig;
    private final String defaultPreset;

    public StrategyCatalog(
        Map<String, CompositeStrategy> presets, RouterConfig routerConfig, String defaultPreset) {
      this.presets = Map.copyOf(presets);
      this.routerConfig = routerConfig;
      this.defaultPreset = defaultPreset;
    }

    public CompositeStrategy strategy(String preset) {
      return presets.getOrDefault(preset, presets.getOrDefault(defaultPreset, presets.values().stream().findFirst().orElse(null)));
    }

    public Map<String, CompositeStrategy> presets() {
      return presets;
    }

    public RouterConfig routerConfig() {
      return routerConfig;
    }

    public String defaultPreset() {
      return defaultPreset;
    }
  }
}
