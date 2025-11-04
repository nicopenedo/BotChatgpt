package com.bottrading.chaos;

import com.bottrading.config.ChaosProperties;
import com.bottrading.config.ChaosProperties.GapPattern;
import com.bottrading.executor.KlineEvent;
import com.bottrading.service.risk.RiskGuard;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChaosSuite {

  private static final Logger log = LoggerFactory.getLogger(ChaosSuite.class);

  private final ChaosProperties properties;
  private final RiskGuard riskGuard;
  private final Clock clock;
  private final Counter wsDrops;
  private final Counter api429;
  private final AtomicReference<Double> latencyGauge = new AtomicReference<>(1.0);
  private final AtomicInteger activeGauge = new AtomicInteger();
  private final AtomicReference<ChaosSettings> activeSettings = new AtomicReference<>();
  private final AtomicLong startedAt = new AtomicLong();
  private final AtomicLong endsAt = new AtomicLong();
  private final AtomicLong burstEndsAt = new AtomicLong();
  private final AtomicBoolean websocketHealthy = new AtomicBoolean(true);
  private final ConcurrentMap<String, AtomicInteger> wsSequence = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, AtomicLong> restPolls = new ConcurrentHashMap<>();
  private final ScheduledExecutorService safetyExecutor;
  private final AtomicReference<ScheduledFuture<?>> safetyFuture = new AtomicReference<>();

  public ChaosSuite(ChaosProperties properties, RiskGuard riskGuard, MeterRegistry meterRegistry, Clock clock) {
    this.properties = Objects.requireNonNull(properties, "properties");
    this.riskGuard = Objects.requireNonNull(riskGuard, "riskGuard");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.wsDrops = meterRegistry.counter("chaos.ws_drops", Tags.empty());
    this.api429 = meterRegistry.counter("chaos.api_429", Tags.empty());
    Gauge.builder("chaos.latency_mult", latencyGauge, AtomicReference::get)
        .tags(Tags.empty())
        .register(meterRegistry);
    Gauge.builder("chaos.active", activeGauge, AtomicInteger::get)
        .tags(Tags.empty())
        .register(meterRegistry);
    this.safetyExecutor =
        Executors.newSingleThreadScheduledExecutor(
            new ChaosThreadFactory("chaos-safety"));
  }

  @PostConstruct
  void autoStart() {
    if (properties.isEnabled()) {
      log.info("Chaos properties enabled on startup; activating default scenario");
      ChaosSettings defaults = fromProperties().withEnabled(true);
      start(defaults);
    }
  }

  @PreDestroy
  void shutdown() {
    ScheduledFuture<?> future = safetyFuture.getAndSet(null);
    if (future != null) {
      future.cancel(true);
    }
    safetyExecutor.shutdownNow();
  }

  public ChaosStatus start(ChaosRequest request) {
    ChaosSettings settings = merge(request);
    return start(settings);
  }

  public ChaosStatus start(ChaosSettings settings) {
    if (settings == null || !settings.enabled()) {
      stop();
      return status();
    }
    if (isActive()) {
      stop();
    }
    log.info(
        "Starting chaos scenario dropRate={}%, burst={}s, latencyMult={}, gapPattern={}, driftMs={} (max {}s)",
        settings.wsDropRatePct(),
        settings.apiBurst429Seconds(),
        settings.latencyMultiplier(),
        settings.gapPattern(),
        settings.clockDriftMs(),
        settings.maxDurationSec());
    activeSettings.set(settings);
    latencyGauge.set(settings.latencyMultiplier());
    activeGauge.set(1);
    Instant now = Instant.now(clock);
    startedAt.set(now.toEpochMilli());
    long endMillis = now.toEpochMilli() + TimeUnit.SECONDS.toMillis(settings.maxDurationSec());
    endsAt.set(endMillis);
    if (settings.apiBurst429Seconds() > 0) {
      burstEndsAt.set(now.toEpochMilli() + TimeUnit.SECONDS.toMillis(settings.apiBurst429Seconds()));
    } else {
      burstEndsAt.set(0);
    }
    websocketHealthy.set(true);
    scheduleSafety(settings.maxDurationSec());
    updateMarketDataStaleFlag();
    return status();
  }

  public ChaosStatus stop() {
    ChaosSettings previous = activeSettings.getAndSet(null);
    if (previous != null) {
      log.info("Stopping chaos scenario");
    }
    latencyGauge.set(1.0);
    activeGauge.set(0);
    startedAt.set(0);
    endsAt.set(0);
    burstEndsAt.set(0);
    websocketHealthy.set(true);
    wsSequence.clear();
    restPolls.clear();
    ScheduledFuture<?> future = safetyFuture.getAndSet(null);
    if (future != null) {
      future.cancel(true);
    }
    updateMarketDataStaleFlag();
    return status();
  }

  public ChaosStatus status() {
    ChaosSettings settings = activeSettings.get();
    boolean active = settings != null;
    Instant start = startedAt.get() == 0 ? null : Instant.ofEpochMilli(startedAt.get());
    Instant end = endsAt.get() == 0 ? null : Instant.ofEpochMilli(endsAt.get());
    long remaining = 0;
    if (active && end != null) {
      long now = Instant.now(clock).toEpochMilli();
      remaining = Math.max(0, (end.toEpochMilli() - now) / 1000);
    }
    return new ChaosStatus(active, settings, start, end, remaining, websocketHealthy.get());
  }

  public boolean isActive() {
    return activeSettings.get() != null;
  }

  public List<KlineEvent> applyWsChaos(KlineEvent event) {
    ChaosSettings settings = activeSettings.get();
    if (settings == null) {
      return List.of(event);
    }
    List<KlineEvent> events = new ArrayList<>();
    events.add(event);
    int dropRate = Math.min(100, Math.max(0, settings.wsDropRatePct()));
    if (dropRate > 0) {
      double probability = dropRate / 100.0;
      if (ThreadLocalRandom.current().nextDouble() < probability) {
        if (ThreadLocalRandom.current().nextBoolean()) {
          wsDrops.increment();
          events.clear();
        } else {
          events.add(event);
        }
      }
    }
    if (events.isEmpty()) {
      return Collections.emptyList();
    }
    List<KlineEvent> result = new ArrayList<>();
    for (KlineEvent candidate : events) {
      if (!shouldSkipByPattern(candidate, settings.gapPattern())) {
        result.add(candidate);
      }
    }
    return result;
  }

  public <T> Supplier<T> decorateApiCall(Supplier<T> supplier) {
    ChaosSettings settings = activeSettings.get();
    if (settings == null) {
      return supplier;
    }
    return () -> {
      maybeThrowBurst429(settings);
      long start = System.nanoTime();
      try {
        return supplier.get();
      } finally {
        applyLatency(settings, start);
      }
    };
  }

  public boolean forceRestFallback() {
    if (!isActive()) {
      return false;
    }
    if (!websocketHealthy.get()) {
      return true;
    }
    ChaosSettings settings = activeSettings.get();
    return settings != null && settings.wsDropRatePct() >= 50;
  }

  public boolean allowRestPoll(String symbol, long baseDelayMs, Instant now) {
    if (!forceRestFallback()) {
      restPolls.remove(symbol);
      return true;
    }
    ChaosSettings settings = activeSettings.get();
    double multiplier = settings != null ? Math.max(1.0, settings.latencyMultiplier()) : 1.0;
    long minInterval = Math.max(baseDelayMs, Math.round(baseDelayMs * multiplier));
    AtomicLong last = restPolls.computeIfAbsent(symbol, key -> new AtomicLong());
    long previous = last.get();
    long nowMs = now.toEpochMilli();
    if (previous == 0 || nowMs - previous >= minInterval) {
      last.set(nowMs);
      return true;
    }
    return false;
  }

  public void onWebsocketState(boolean healthy) {
    websocketHealthy.set(healthy);
    updateMarketDataStaleFlag();
  }

  public long clockDriftMs() {
    ChaosSettings settings = activeSettings.get();
    if (settings != null) {
      return settings.clockDriftMs();
    }
    return properties.getClockDriftMs();
  }

  private void maybeThrowBurst429(ChaosSettings settings) {
    long burstEnd = burstEndsAt.get();
    if (burstEnd == 0) {
      return;
    }
    long now = Instant.now(clock).toEpochMilli();
    if (now <= burstEnd) {
      api429.increment();
      int status = (now / 1000) % 2 == 0 ? 429 : 418;
      throw new ChaosHttpException(status, "Chaos burst rate limit");
    }
  }

  private void applyLatency(ChaosSettings settings, long startNano) {
    double multiplier = Math.max(1.0, settings.latencyMultiplier());
    if (multiplier <= 1.0) {
      return;
    }
    long elapsed = System.nanoTime() - startNano;
    if (elapsed < 0) {
      return;
    }
    long target = (long) (elapsed * multiplier);
    long additional = Math.max(0, target - elapsed);
    if (additional == 0) {
      additional = (long) (TimeUnit.MILLISECONDS.toNanos(5) * (multiplier - 1));
    }
    if (additional <= 0) {
      return;
    }
    try {
      TimeUnit.NANOSECONDS.sleep(additional);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  private boolean shouldSkipByPattern(KlineEvent event, GapPattern pattern) {
    if (pattern == null || pattern == GapPattern.NONE) {
      return false;
    }
    AtomicInteger counter = wsSequence.computeIfAbsent(key(event), key -> new AtomicInteger());
    int current = counter.incrementAndGet();
    if (pattern == GapPattern.SKIP_EVERY_10 && current % 10 == 0) {
      wsDrops.increment();
      return true;
    }
    if (pattern == GapPattern.RANDOM_1PCT && ThreadLocalRandom.current().nextDouble() < 0.01) {
      wsDrops.increment();
      return true;
    }
    return false;
  }

  private ChaosSettings merge(ChaosRequest request) {
    ChaosSettings.Builder builder = ChaosSettings.builder();
    if (request == null) {
      builder.enabled(true);
    } else {
      builder.enabled(request.enabled() != null ? request.enabled() : true);
    }
    builder
        .wsDropRatePct(
            request != null && request.wsDropRatePct() != null
                ? request.wsDropRatePct()
                : properties.getWsDropRatePct())
        .apiBurst429Seconds(
            request != null && request.apiBurst429Seconds() != null
                ? request.apiBurst429Seconds()
                : properties.getApiBurst429Seconds())
        .latencyMultiplier(
            request != null && request.latencyMultiplier() != null
                ? request.latencyMultiplier()
                : properties.getLatencyMultiplier())
        .gapPattern(
            request != null && request.candlesGapPattern() != null
                ? request.candlesGapPattern()
                : properties.getCandlesGapPattern())
        .clockDriftMs(
            request != null && request.clockDriftMs() != null
                ? request.clockDriftMs()
                : properties.getClockDriftMs());
    int requestedDuration =
        request != null && request.maxDurationSec() != null
            ? request.maxDurationSec()
            : properties.getSafety().getMaxDurationSec();
    builder.maxDurationSec(Math.min(requestedDuration, properties.getSafety().getMaxDurationSec()));
    return builder.build();
  }

  private ChaosSettings fromProperties() {
    return ChaosSettings.builder()
        .enabled(properties.isEnabled())
        .wsDropRatePct(properties.getWsDropRatePct())
        .apiBurst429Seconds(properties.getApiBurst429Seconds())
        .latencyMultiplier(properties.getLatencyMultiplier())
        .gapPattern(properties.getCandlesGapPattern())
        .clockDriftMs(properties.getClockDriftMs())
        .maxDurationSec(properties.getSafety().getMaxDurationSec())
        .build();
  }

  private void scheduleSafety(int maxDurationSec) {
    ScheduledFuture<?> future = safetyFuture.getAndSet(null);
    if (future != null) {
      future.cancel(true);
    }
    safetyFuture.set(
        safetyExecutor.schedule(
            () -> {
              log.warn("Chaos scenario timed out after {} seconds; stopping", maxDurationSec);
              stop();
            },
            maxDurationSec,
            TimeUnit.SECONDS));
  }

  private void updateMarketDataStaleFlag() {
    boolean stale = forceRestFallback();
    riskGuard.setMarketDataStale(stale);
  }

  private String key(KlineEvent event) {
    return event.symbol() + "|" + event.interval();
  }

  private static final class ChaosThreadFactory implements ThreadFactory {
    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger();

    private ChaosThreadFactory(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable r) {
      Thread thread = new Thread(r, prefix + "-" + counter.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    }
  }
}
