package com.bottrading.service.tca;

import com.bottrading.config.TradingProps;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class TcaService {

  private static final Duration SAMPLE_RETENTION = Duration.ofDays(7);

  private final TradingProps tradingProps;
  private final MeterRegistry meterRegistry;
  private final Deque<TcaSample> samples;
  private final ConcurrentMap<String, PendingOrder> pending = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, AtomicReference<Double>> averageGauge = new ConcurrentHashMap<>();

  public TcaService(TradingProps tradingProps, MeterRegistry meterRegistry) {
    this.tradingProps = tradingProps;
    this.meterRegistry = meterRegistry;
    this.samples = new ArrayDeque<>(tradingProps.getTca().getHistorySize());
  }

  public void recordSubmission(
      String clientOrderId,
      String symbol,
      OrderSide side,
      OrderType type,
      BigDecimal referencePrice,
      BigDecimal volume24h,
      BigDecimal atr,
      Instant timestamp) {
    if (!tradingProps.getTca().isEnabled() || clientOrderId == null) {
      return;
    }
    PendingOrder pendingOrder =
        new PendingOrder(
            symbol,
            side,
            type,
            referencePrice == null ? null : referencePrice.doubleValue(),
            volume24h == null ? null : volume24h.doubleValue(),
            atr == null ? null : atr.doubleValue(),
            timestamp == null ? Instant.now() : timestamp);
    pending.put(clientOrderId, pendingOrder);
  }

  public void recordFill(
      String clientOrderId,
      BigDecimal fillPrice,
      BigDecimal spread,
      Instant timestamp) {
    if (!tradingProps.getTca().isEnabled() || clientOrderId == null) {
      return;
    }
    PendingOrder pendingOrder = pending.remove(clientOrderId);
    if (pendingOrder == null || fillPrice == null) {
      return;
    }
    Instant filledAt = timestamp == null ? Instant.now() : timestamp;
    long queueMs = Duration.between(pendingOrder.timestamp(), filledAt).toMillis();
    double slippageBps =
        computeSlippage(
            pendingOrder.side(),
            pendingOrder.referencePrice(),
            fillPrice.doubleValue());
    TcaSample sample =
        new TcaSample(
            pendingOrder.symbol(),
            pendingOrder.side(),
            pendingOrder.type(),
            filledAt,
            slippageBps,
            queueMs,
            pendingOrder.referencePrice(),
            fillPrice.doubleValue(),
            pendingOrder.volume24h(),
            pendingOrder.atr(),
            spread == null ? null : spread.doubleValue());
    append(sample);
  }

  public double expectedSlippageBps(String symbol, OrderType type, Instant timestamp) {
    if (!tradingProps.getTca().isEnabled()) {
      return Double.NaN;
    }
    int targetHour = hourOf(timestamp);
    List<TcaSample> relevant = new ArrayList<>();
    synchronized (samples) {
      for (TcaSample sample : samples) {
        if (!sample.symbol().equalsIgnoreCase(symbol)) {
          continue;
        }
        if (sample.type() != type) {
          continue;
        }
        if (hourOf(sample.timestamp()) == targetHour) {
          relevant.add(sample);
        }
      }
    }
    if (relevant.isEmpty()) {
      return averageSlippage(symbol);
    }
    return relevant.stream().mapToDouble(TcaSample::slippageBps).average().orElse(Double.NaN);
  }

  public OrderType recommendOrderType(String symbol, OrderType baseline, Instant now) {
    double expected = expectedSlippageBps(symbol, baseline, now);
    if (Double.isNaN(expected)) {
      return baseline;
    }
    if (expected > 8.0 && baseline == OrderType.MARKET) {
      return OrderType.LIMIT;
    }
    if (expected < 4.0 && baseline == OrderType.LIMIT) {
      return OrderType.MARKET;
    }
    return baseline;
  }

  public AggregatedStats aggregate(String symbol, Instant from, Instant to) {
    List<TcaSample> filtered = new ArrayList<>();
    synchronized (samples) {
      for (TcaSample sample : samples) {
        if (symbol != null && !symbol.equalsIgnoreCase(sample.symbol())) {
          continue;
        }
        if (from != null && sample.timestamp().isBefore(from)) {
          continue;
        }
        if (to != null && sample.timestamp().isAfter(to)) {
          continue;
        }
        filtered.add(sample);
      }
    }
    Map<Integer, List<TcaSample>> byHour = new HashMap<>();
    for (TcaSample sample : filtered) {
      byHour.computeIfAbsent(hourOf(sample.timestamp()), h -> new ArrayList<>()).add(sample);
    }
    Map<Integer, Double> avgByHour = new HashMap<>();
    for (Map.Entry<Integer, List<TcaSample>> entry : byHour.entrySet()) {
      avgByHour.put(
          entry.getKey(),
          entry.getValue().stream().mapToDouble(TcaSample::slippageBps).average().orElse(Double.NaN));
    }
    double avg = filtered.stream().mapToDouble(TcaSample::slippageBps).average().orElse(Double.NaN);
    double avgQueue = filtered.stream().mapToLong(TcaSample::queueTimeMs).average().orElse(Double.NaN);
    return new AggregatedStats(filtered.size(), avg, avgQueue, avgByHour);
  }

  private void append(TcaSample sample) {
    synchronized (samples) {
      samples.addLast(sample);
      while (samples.size() > tradingProps.getTca().getHistorySize()) {
        samples.removeFirst();
      }
      pruneOld();
      double avg = samples.stream().mapToDouble(TcaSample::slippageBps).average().orElse(0);
      AtomicReference<Double> gaugeRef =
          averageGauge.computeIfAbsent(
              sample.symbol(),
              sym -> {
                AtomicReference<Double> ref = new AtomicReference<>(0.0);
                meterRegistry.gauge("tca.slippage.avg_bps", Tags.of("symbol", sym), ref, AtomicReference::get);
                return ref;
              });
      gaugeRef.set(avg);
      meterRegistry.counter("tca.samples", Tags.of("symbol", sample.symbol(), "type", sample.type().name())).increment();
    }
  }

  private void pruneOld() {
    Instant cutoff = Instant.now().minus(SAMPLE_RETENTION);
    while (!samples.isEmpty() && samples.peekFirst().timestamp().isBefore(cutoff)) {
      samples.removeFirst();
    }
  }

  private double averageSlippage(String symbol) {
    synchronized (samples) {
      return samples.stream()
          .filter(sample -> symbol == null || sample.symbol().equalsIgnoreCase(symbol))
          .mapToDouble(TcaSample::slippageBps)
          .average()
          .orElse(Double.NaN);
    }
  }

  private double computeSlippage(OrderSide side, Double referencePrice, double fillPrice) {
    if (referencePrice == null || referencePrice <= 0) {
      return Double.NaN;
    }
    double diff = fillPrice - referencePrice;
    if (side == OrderSide.SELL) {
      diff = referencePrice - fillPrice;
    }
    return diff / referencePrice * 10000;
  }

  private int hourOf(Instant timestamp) {
    return ZonedDateTime.ofInstant(timestamp, ZoneId.systemDefault()).getHour();
  }

  private record PendingOrder(
      String symbol,
      OrderSide side,
      OrderType type,
      Double referencePrice,
      Double volume24h,
      Double atr,
      Instant timestamp) {}

  public record TcaSample(
      String symbol,
      OrderSide side,
      OrderType type,
      Instant timestamp,
      double slippageBps,
      long queueTimeMs,
      Double referencePrice,
      Double fillPrice,
      Double volume24h,
      Double atr,
      Double spread) {}

  public record AggregatedStats(int samples, double averageBps, double averageQueueMs, Map<Integer, Double> hourlyAverage) {}
}
