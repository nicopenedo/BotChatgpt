package com.bottrading.execution.metrics;

import com.bottrading.config.ExecutionProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlippageMetrics {

  private final MeterRegistry meterRegistry;
  private final ExecutionProperties executionProperties;
  private final Clock clock;
  private final ConcurrentMap<MetricKey, Holder> holders = new ConcurrentHashMap<>();

  public SlippageMetrics(
      MeterRegistry meterRegistry, ExecutionProperties executionProperties, Clock clock) {
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    this.executionProperties =
        Objects.requireNonNull(executionProperties, "executionProperties");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public void record(UUID tenantId, String symbol, double slippageBps) {
    if (!Double.isFinite(slippageBps)) {
      return;
    }
    MetricKey key = MetricKey.of(tenantId, symbol);
    Holder holder = holders.computeIfAbsent(key, this::registerGauge);
    holder.sum.add(slippageBps);
    holder.count.increment();
    long samples = holder.count.sum();
    if (samples > 0L) {
      holder.value.set(holder.sum.doubleValue() / samples);
    }
    holder.lastUpdated = clock.millis();
  }

  public void remove(UUID tenantId, String symbol) {
    remove(MetricKey.of(tenantId, symbol));
  }

  @Scheduled(fixedDelayString = "${exec.metrics.cleanup-ms:600000}")
  public void cleanup() {
    long now = clock.millis();
    long ttl = Math.max(0L, executionProperties.getMetrics().getTtlMs());
    holders.forEach(
        (key, holder) -> {
          if (now - holder.lastUpdated > ttl) {
            remove(key);
          }
        });
  }

  private Holder registerGauge(MetricKey key) {
    Holder holder = new Holder();
    Gauge gauge =
        Gauge.builder("exec.slippage.avg_bps", holder.value, AtomicReference::get)
            .description("Average slippage (bps) per tenant and symbol")
            .baseUnit("bps")
            .tags(tagsFor(key))
            .strongReference(true)
            .register(meterRegistry);
    holder.meter = gauge;
    holder.lastUpdated = clock.millis();
    return holder;
  }

  private void remove(MetricKey key) {
    Holder removed = holders.remove(key);
    if (removed != null && removed.meter != null) {
      meterRegistry.remove(removed.meter);
    }
  }

  private Tags tagsFor(MetricKey key) {
    if (key.venue() == null || key.venue().isBlank()) {
      return Tags.of("tenant", key.tenant(), "symbol", key.symbol());
    }
    return Tags.of("tenant", key.tenant(), "symbol", key.symbol(), "venue", key.venue());
  }

  private static final class Holder {
    private final AtomicReference<Double> value = new AtomicReference<>(0.0);
    private final DoubleAdder sum = new DoubleAdder();
    private final LongAdder count = new LongAdder();
    private volatile long lastUpdated;
    private Meter meter;
  }
}
