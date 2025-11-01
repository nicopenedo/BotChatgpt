package com.bottrading.service.health;

import com.bottrading.config.TradingProps;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

  private static final Duration ERROR_WINDOW = Duration.ofMinutes(10);

  private final TradingProps tradingProps;
  private final Deque<Instant> wsReconnects = new ArrayDeque<>();
  private final Deque<ApiSample> apiSamples = new ArrayDeque<>();
  private final AtomicBoolean healthy = new AtomicBoolean(true);
  private final AtomicReference<Double> errorRate = new AtomicReference<>(0.0);
  private final Counter pauses;

  public HealthService(TradingProps tradingProps, MeterRegistry meterRegistry) {
    this.tradingProps = tradingProps;
    this.pauses = meterRegistry.counter("health.pauses");
    meterRegistry.gauge("health.api.error.rate", errorRate, AtomicReference::get);
    meterRegistry.gauge("health.status", Tags.empty(), healthy, flag -> flag.get() ? 1.0 : 0.0);
  }

  public void onWebsocketReconnect() {
    if (!tradingProps.getHealth().isEnabled()) {
      return;
    }
    Instant now = Instant.now();
    synchronized (wsReconnects) {
      wsReconnects.addLast(now);
      pruneReconnects(now);
      if (wsReconnects.size() > tradingProps.getHealth().getWsMaxReconnectsPerHour()) {
        markUnhealthy();
      }
    }
  }

  public void onApiCall(long latencyMs, boolean success) {
    if (!tradingProps.getHealth().isEnabled()) {
      return;
    }
    Instant now = Instant.now();
    synchronized (apiSamples) {
      apiSamples.addLast(new ApiSample(now, success, latencyMs));
      pruneSamples(now);
      evaluateSamples();
    }
  }

  public boolean isHealthy() {
    if (!tradingProps.getHealth().isEnabled()) {
      return true;
    }
    return healthy.get();
  }

  public HealthStatus status() {
    synchronized (apiSamples) {
      return new HealthStatus(healthy.get(), errorRate.get(), wsReconnects.size(), apiSamples.size());
    }
  }

  public void reset() {
    synchronized (apiSamples) {
      apiSamples.clear();
      errorRate.set(0.0);
    }
    synchronized (wsReconnects) {
      wsReconnects.clear();
    }
    healthy.set(true);
  }

  private void pruneReconnects(Instant now) {
    Instant cutoff = now.minus(Duration.ofHours(1));
    while (!wsReconnects.isEmpty() && wsReconnects.peekFirst().isBefore(cutoff)) {
      wsReconnects.removeFirst();
    }
    if (wsReconnects.size() <= tradingProps.getHealth().getWsMaxReconnectsPerHour() && healthy.get() == false) {
      healthy.set(true);
    }
  }

  private void pruneSamples(Instant now) {
    Instant cutoff = now.minus(ERROR_WINDOW);
    while (!apiSamples.isEmpty() && apiSamples.peekFirst().timestamp().isBefore(cutoff)) {
      apiSamples.removeFirst();
    }
  }

  private void evaluateSamples() {
    int total = apiSamples.size();
    if (total == 0) {
      errorRate.set(0.0);
      healthy.set(true);
      return;
    }
    long errors = apiSamples.stream().filter(sample -> !sample.success()).count();
    double rate = (double) errors / total * 100;
    errorRate.set(rate);
    boolean overThreshold = rate > tradingProps.getHealth().getApiMaxErrorRatePct();
    boolean latencyBreach =
        apiSamples.stream().anyMatch(sample -> sample.latencyMs() > tradingProps.getHealth().getApiLatencyThresholdMs());
    if (overThreshold || latencyBreach) {
      markUnhealthy();
    } else if (healthy.get() == false) {
      healthy.set(true);
    }
  }

  private void markUnhealthy() {
    if (healthy.compareAndSet(true, false)) {
      pauses.increment();
    }
  }

  private record ApiSample(Instant timestamp, boolean success, long latencyMs) {}

  public record HealthStatus(boolean healthy, double apiErrorRatePct, int wsReconnects, int apiSamples) {}
}
