package com.bottrading.service.anomaly;

// FIX: Ensure large response maps use Map.ofEntries for Java 21 collection limits.

import com.bottrading.config.AnomalyProperties;
import com.bottrading.saas.service.TenantMetrics;
import com.bottrading.service.risk.RiskFlag;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AnomalyDetector {

  private static final Logger log = LoggerFactory.getLogger(AnomalyDetector.class);
  private static final int SPARKLINE_POINTS = 60;

  private final AnomalyProperties properties;
  private final MeterRegistry meterRegistry;
  private final AnomalyAlertPublisher alertPublisher;
  private final AnomalyRiskAdapter riskAdapter;
  private final Clock clock;
  private final ConcurrentMap<MetricKey, RollingWindow> windows = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, SymbolState> states = new ConcurrentHashMap<>();
  private final ConcurrentMap<MetricKey, AtomicReference<Double>> metricGauges = new ConcurrentHashMap<>();
  private final TenantMetrics tenantMetrics;

  public AnomalyDetector(
      AnomalyProperties properties,
      MeterRegistry meterRegistry,
      AnomalyAlertPublisher alertPublisher,
      AnomalyRiskAdapter riskAdapter,
      Clock clock,
      TenantMetrics tenantMetrics) {
    this.properties = properties;
    this.meterRegistry = meterRegistry;
    this.alertPublisher = alertPublisher;
    this.riskAdapter = riskAdapter;
    this.clock = clock;
    this.tenantMetrics = tenantMetrics;
  }

  public void recordSlippage(String symbol, double value) {
    record(symbol, AnomalyMetric.SLIPPAGE_BPS, value);
  }

  public void recordFillRate(String symbol, double value) {
    record(symbol, AnomalyMetric.FILL_RATE, value);
  }

  public void recordQueueTime(String symbol, double value) {
    record(symbol, AnomalyMetric.QUEUE_TIME_MS, value);
  }

  public void recordLatency(String symbol, double value) {
    record(symbol, AnomalyMetric.LATENCY_MS, value);
  }

  public void recordApiCall(String symbol, long latencyMs, boolean success) {
    record(symbol, AnomalyMetric.LATENCY_MS, latencyMs);
    record(symbol, AnomalyMetric.API_ERROR_RATE, success ? 0.0 : 1.0);
  }

  public void recordWsReconnects(String symbol, double count) {
    record(symbol, AnomalyMetric.WS_RECONNECTS, count);
  }

  public void recordSpread(String symbol, double bps) {
    record(symbol, AnomalyMetric.SPREAD_BPS, bps);
  }

  public double sizingMultiplier(String symbol) {
    SymbolState state = states.get(symbolKey(symbol));
    if (state == null) {
      return 1.0;
    }
    return state.multiplier();
  }

  public ExecutionOverride executionOverride(String symbol) {
    SymbolState state = states.get(symbolKey(symbol));
    if (state == null) {
      return ExecutionOverride.NONE;
    }
    return state.override();
  }

  public Optional<AnomalySnapshot> snapshot(String symbol) {
    SymbolState state = states.get(symbolKey(symbol));
    if (state == null) {
      return Optional.empty();
    }
    return state.snapshot();
  }

  private void record(String rawSymbol, AnomalyMetric metric, double value) {
    if (!properties.isEnabled() || rawSymbol == null || metric == null) {
      return;
    }
    if (!Double.isFinite(value)) {
      return;
    }
    String symbol = symbolKey(rawSymbol);
    MetricKey key = new MetricKey(symbol, metric);
    RollingWindow window =
        windows.computeIfAbsent(key, ignored -> new RollingWindow(properties.getWindow()));
    Instant now = Instant.now(clock);
    AtomicReference<Double> gauge =
        metricGauges.computeIfAbsent(
            key,
            ignored -> {
              AtomicReference<Double> ref = new AtomicReference<>(0.0);
              Gauge.builder("anomaly.metric", ref, AtomicReference::get)
                  .tags("symbol", symbol, "metric", metric.id())
                  .register(meterRegistry);
              return ref;
            });
    gauge.set(value);
    Stats stats = window.add(value);
    SymbolState state = states.computeIfAbsent(symbol, this::newSymbolState);
    state.append(metric, value);
    state.removeExpired(now);
    if (stats.count() < properties.getMinSamples()) {
      return;
    }
    double zScore = computeZScore(value, stats);
    if (Double.isNaN(zScore) || Double.isInfinite(zScore)) {
      return;
    }
    double abs = Math.abs(zScore);
    if (abs < properties.getZscore().getWarn()) {
      return;
    }
    if (properties.isEsdEnabled() && !isRobustOutlier(window.values(), value)) {
      return;
    }
    AnomalySeverity severity = classify(abs);
    if (severity == AnomalySeverity.NONE) {
      return;
    }
    applyAnomaly(state, symbol, metric, severity, zScore, value, stats, now);
  }

  private void applyAnomaly(
      SymbolState state,
      String symbol,
      AnomalyMetric metric,
      AnomalySeverity severity,
      double zScore,
      double value,
      Stats stats,
      Instant now) {
    AnomalyAction action = properties.actionFor(severity);
    ActiveAnomaly previous = state.active.get(metric);
    Instant expiresAt = now.plusSeconds(Math.max(1, properties.getCoolDownSec()));
    if (previous != null && previous.severity == severity) {
      previous.refresh(zScore, value, stats.mean(), expiresAt, detailFor(metric, value, stats, zScore));
      state.updateGauge();
      return;
    }

    ActiveAnomaly active =
        new ActiveAnomaly(
            metric,
            severity,
            action,
            zScore,
            value,
            stats.mean(),
            expiresAt,
            now,
            detailFor(metric, value, stats, zScore));
    state.active.put(metric, active);
    state.updateGauge();
    Counter counter =
        meterRegistry.counter(
            "anomaly.alerts",
            Tags.concat(
                tenantMetrics.tags(symbol),
                Tags.of("type", metric.id(), "severity", severity.name())));
    counter.increment();
    alertPublisher.publish(
        new AnomalyNotification(
            symbol,
            metric,
            severity,
            action,
            value,
            stats.mean(),
            zScore,
            now,
            expiresAt,
            active.detail));
    log.warn(
        "Anomaly detected {} {} severity={} action={} value={} z={}",
        symbol,
        metric.id(),
        severity,
        action,
        String.format(Locale.US, "%.4f", value),
        String.format(Locale.US, "%.2f", zScore));
    if (action == AnomalyAction.PAUSE) {
      RiskFlag flag = metric.riskFlag();
      riskAdapter.applyPause(flag, Duration.ofSeconds(properties.getCoolDownSec()), active.detail);
    }
  }

  private SymbolState newSymbolState(String symbol) {
    AtomicInteger gauge = new AtomicInteger(0);
    Gauge.builder("anomaly.state", gauge, AtomicInteger::get)
        .tags("symbol", symbol)
        .register(meterRegistry);
    return new SymbolState(symbol, gauge);
  }

  private AnomalySeverity classify(double zScore) {
    if (zScore >= properties.getZscore().getSevere()) {
      return AnomalySeverity.SEVERE;
    }
    if (zScore >= properties.getZscore().getHigh()) {
      return AnomalySeverity.HIGH;
    }
    if (zScore >= properties.getZscore().getMitigate()) {
      return AnomalySeverity.MEDIUM;
    }
    if (zScore >= properties.getZscore().getWarn()) {
      return AnomalySeverity.WARN;
    }
    return AnomalySeverity.NONE;
  }

  private double computeZScore(double value, Stats stats) {
    if (stats.stdDev() <= 0) {
      return Double.NaN;
    }
    return (value - stats.mean()) / stats.stdDev();
  }

  private boolean isRobustOutlier(List<Double> values, double sample) {
    if (values.size() < 5) {
      return true;
    }
    List<Double> copy = new ArrayList<>(values);
    Collections.sort(copy);
    double median = median(copy);
    List<Double> deviations = new ArrayList<>(copy.size());
    for (double v : copy) {
      deviations.add(Math.abs(v - median));
    }
    Collections.sort(deviations);
    double mad = median(deviations);
    if (mad == 0) {
      return true;
    }
    double modifiedZ = 0.6745 * Math.abs(sample - median) / mad;
    return modifiedZ >= properties.getZscore().getWarn();
  }

  private double median(List<Double> sortedValues) {
    int size = sortedValues.size();
    if (size == 0) {
      return 0;
    }
    if (size % 2 == 1) {
      return sortedValues.get(size / 2);
    }
    return (sortedValues.get(size / 2 - 1) + sortedValues.get(size / 2)) / 2.0;
  }

  private String detailFor(AnomalyMetric metric, double value, Stats stats, double zScore) {
    return switch (metric) {
      case FILL_RATE ->
          "fill="
              + String.format(Locale.US, "%.2f%%", value * 100)
              + " mean="
              + String.format(Locale.US, "%.2f%%", stats.mean() * 100)
              + " z="
              + String.format(Locale.US, "%.2f", zScore);
      case API_ERROR_RATE ->
          "error_rate="
              + String.format(Locale.US, "%.2f%%", value * 100)
              + " mean="
              + String.format(Locale.US, "%.2f%%", stats.mean() * 100)
              + " z="
              + String.format(Locale.US, "%.2f", zScore);
      case SLIPPAGE_BPS, SPREAD_BPS ->
          "bps="
              + String.format(Locale.US, "%.2f", value)
              + " mean="
              + String.format(Locale.US, "%.2f", stats.mean())
              + " z="
              + String.format(Locale.US, "%.2f", zScore);
      default ->
          metric.id()
              + "="
              + String.format(Locale.US, "%.2f", value)
              + " mean="
              + String.format(Locale.US, "%.2f", stats.mean())
              + " z="
              + String.format(Locale.US, "%.2f", zScore);
    };
  }

  private String symbolKey(String raw) {
    return raw == null ? "UNKNOWN" : raw.toUpperCase(Locale.ROOT);
  }

  private record MetricKey(String symbol, AnomalyMetric metric) {}

  private static class RollingWindow {
    private final int capacity;
    private final Deque<Double> values = new ArrayDeque<>();
    private double sum;
    private double sumSquares;

    private RollingWindow(int capacity) {
      this.capacity = Math.max(1, capacity);
    }

    synchronized Stats add(double value) {
      if (values.size() == capacity) {
        double removed = values.removeFirst();
        sum -= removed;
        sumSquares -= removed * removed;
      }
      values.addLast(value);
      sum += value;
      sumSquares += value * value;
      int count = values.size();
      double mean = sum / count;
      double variance = Math.max(0.0, (sumSquares / count) - (mean * mean));
      double stdDev = Math.sqrt(variance);
      return new Stats(count, mean, stdDev);
    }

    synchronized List<Double> values() {
      return new ArrayList<>(values);
    }
  }

  private record Stats(int count, double mean, double stdDev) {}

  public enum ExecutionOverride {
    NONE,
    FORCE_MARKET,
    FORCE_TWAP;

    boolean higherPriorityThan(ExecutionOverride other) {
      return this.ordinal() > other.ordinal();
    }
  }

  private static class ActiveAnomaly {
    private final AnomalyMetric metric;
    private final AnomalySeverity severity;
    private final AnomalyAction action;
    private final Instant triggeredAt;
    private Instant expiresAt;
    private double zScore;
    private double value;
    private double mean;
    private String detail;

    private ActiveAnomaly(
        AnomalyMetric metric,
        AnomalySeverity severity,
        AnomalyAction action,
        double zScore,
        double value,
        double mean,
        Instant expiresAt,
        Instant triggeredAt,
        String detail) {
      this.metric = metric;
      this.severity = severity;
      this.action = action;
      this.zScore = zScore;
      this.value = value;
      this.mean = mean;
      this.expiresAt = expiresAt;
      this.triggeredAt = triggeredAt;
      this.detail = detail;
    }

    void refresh(double zScore, double value, double mean, Instant expiresAt, String detail) {
      this.zScore = zScore;
      this.value = value;
      this.mean = mean;
      this.expiresAt = expiresAt;
      this.detail = detail;
    }

    double multiplier() {
      return action.sizingMultiplier();
    }

    ExecutionOverride override() {
      return switch (action) {
        case SWITCH_TO_TWAP -> ExecutionOverride.FORCE_TWAP;
        case SWITCH_TO_MARKET -> ExecutionOverride.FORCE_MARKET;
        default -> ExecutionOverride.NONE;
      };
    }
  }

  private class SymbolState {
    private final String symbol;
    private final AtomicInteger gauge;
    private final EnumMap<AnomalyMetric, ActiveAnomaly> active = new EnumMap<>(AnomalyMetric.class);
    private final EnumMap<AnomalyMetric, Deque<Double>> series =
        new EnumMap<>(AnomalyMetric.class);

    private SymbolState(String symbol, AtomicInteger gauge) {
      this.symbol = symbol;
      this.gauge = gauge;
    }

    void append(AnomalyMetric metric, double value) {
      Deque<Double> deque = series.computeIfAbsent(metric, m -> new ArrayDeque<>());
      if (deque.size() == SPARKLINE_POINTS) {
        deque.removeFirst();
      }
      deque.addLast(value);
    }

    void removeExpired(Instant now) {
      active.values().removeIf(a -> a.expiresAt.isBefore(now));
      updateGauge();
    }

    double multiplier() {
      return active.values().stream().mapToDouble(ActiveAnomaly::multiplier).min().orElse(1.0);
    }

    ExecutionOverride override() {
      ExecutionOverride override = ExecutionOverride.NONE;
      for (ActiveAnomaly anomaly : active.values()) {
        ExecutionOverride candidate = anomaly.override();
        if (candidate.higherPriorityThan(override)) {
          override = candidate;
        }
      }
      return override;
    }

    Optional<AnomalySnapshot> snapshot() {
      if (active.isEmpty()) {
        return Optional.empty();
      }
      ActiveAnomaly anomaly =
          Collections.max(active.values(), Comparator.comparing(a -> a.severity.gaugeLevel()));
      List<Double> sparkline = new ArrayList<>(series.getOrDefault(anomaly.metric, new ArrayDeque<>()));
      return Optional.of(
          new AnomalySnapshot(
              symbol,
              anomaly.metric.id(),
              anomaly.severity.name(),
              anomaly.action.name(),
              anomaly.value,
              anomaly.zScore,
              anomaly.triggeredAt,
              anomaly.expiresAt,
              anomaly.detail,
              sparkline));
    }

    void updateGauge() {
      int level =
          active.values().stream()
              .mapToInt(anomaly -> anomaly.severity.gaugeLevel())
              .max()
              .orElse(0);
      gauge.set(level);
    }
  }

  public record AnomalySnapshot(
      String symbol,
      String metric,
      String severity,
      String action,
      double value,
      double zScore,
      Instant triggeredAt,
      Instant expiresAt,
      String cause,
      List<Double> sparkline) {
    public Map<String, Object> asMap() {
      return Map.ofEntries(
          Map.entry("symbol", symbol),
          Map.entry("metric", metric),
          Map.entry("severity", severity),
          Map.entry("action", action),
          Map.entry("value", value),
          Map.entry("zScore", zScore),
          Map.entry("triggeredAt", triggeredAt),
          Map.entry("expiresAt", expiresAt),
          Map.entry("cause", cause),
          Map.entry("sparkline", sparkline),
          Map.entry("active", true));
    }
  }
}
