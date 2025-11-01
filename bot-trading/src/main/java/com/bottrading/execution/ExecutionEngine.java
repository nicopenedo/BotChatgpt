package com.bottrading.execution;

import com.bottrading.config.ExecutionProperties;
import com.bottrading.execution.ExecutionPolicy.LimitPlan;
import com.bottrading.execution.ExecutionPolicy.MarketPlan;
import com.bottrading.execution.ExecutionPolicy.OrderPlan;
import com.bottrading.execution.ExecutionPolicy.PovPlan;
import com.bottrading.execution.ExecutionPolicy.TwapPlan;
import com.bottrading.model.dto.OrderRequest;
import com.bottrading.model.dto.OrderResponse;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import com.bottrading.service.anomaly.AnomalyDetector;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.tca.TcaService;
import com.bottrading.service.trading.OrderService;
import com.bottrading.util.IdGenerator;
import com.bottrading.util.OrderValidator;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExecutionEngine {

  private static final Logger log = LoggerFactory.getLogger(ExecutionEngine.class);

  private final ExecutionPolicy policy;
  private final OrderService orderService;
  private final BinanceClient binanceClient;
  private final TcaService tcaService;
  private final ExecutionProperties properties;
  private final MeterRegistry meterRegistry;
  private final Clock clock;
  private final DistributionSummary queueTimes;
  private final DistributionSummary limitTtl;
  private final ConcurrentMap<String, RunningAverage> slippageAvg = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, AtomicReference<Double>> povGauge = new ConcurrentHashMap<>();
  private final AnomalyDetector anomalyDetector;

  public ExecutionEngine(
      ExecutionPolicy policy,
      OrderService orderService,
      BinanceClient binanceClient,
      TcaService tcaService,
      ExecutionProperties properties,
      MeterRegistry meterRegistry,
      Clock clock,
      AnomalyDetector anomalyDetector) {
    this.policy = policy;
    this.orderService = orderService;
    this.binanceClient = binanceClient;
    this.tcaService = tcaService;
    this.properties = properties;
    this.meterRegistry = meterRegistry;
    this.clock = clock;
    this.anomalyDetector = anomalyDetector;
    this.queueTimes =
        DistributionSummary.builder("exec.queueTime.ms").publishPercentileHistogram().register(meterRegistry);
    this.limitTtl =
        DistributionSummary.builder("exec.limit.ttl.ms")
            .baseUnit("milliseconds")
            .description("Configured TTL before a limit order is replaced")
            .register(meterRegistry);
    this.limitTtl.record(0);
  }

  public ExecutionResult execute(ExecutionRequest request, MarketSnapshot snapshot) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(snapshot, "snapshot");

    anomalyDetector.recordSpread(request.symbol(), request.spreadBps());
    OrderPlan plan = policy.planFor(request, snapshot, tcaService::expectedSlippageBps);
    AnomalyDetector.ExecutionOverride override = anomalyDetector.executionOverride(request.symbol());
    if (override == AnomalyDetector.ExecutionOverride.FORCE_MARKET) {
      plan = new MarketPlan();
    } else if (override == AnomalyDetector.ExecutionOverride.FORCE_TWAP) {
      int slices = Math.max(2, properties.getTwap().getSlices());
      Duration window = properties.getTwap().windowDuration();
      plan = new TwapPlan(slices, window);
    }
    if (plan instanceof MarketPlan marketPlan) {
      return executeMarket(request, snapshot, marketPlan);
    } else if (plan instanceof LimitPlan limitPlan) {
      return executeLimit(request, snapshot, limitPlan);
    } else if (plan instanceof TwapPlan twapPlan) {
      return executeTwap(request, snapshot, twapPlan);
    } else if (plan instanceof PovPlan povPlan) {
      return executePov(request, snapshot, povPlan);
    }
    throw new IllegalStateException("Unsupported order plan " + plan);
  }

  private ExecutionResult executeMarket(ExecutionRequest request, MarketSnapshot snapshot, MarketPlan plan) {
    List<OrderResponse> responses = new ArrayList<>();
    OrderResponse response = submitOrder(request, OrderType.MARKET, request.referencePrice(), request.quantity(), request.baseClientOrderId());
    responses.add(response);
    BigDecimal executedQty = safeQty(response.executedQty());
    BigDecimal avgPrice = resolveFillPrice(response, request.referencePrice());
    recordFillMetrics(request, avgPrice, executedQty);
    return new ExecutionResult(plan, responses, executedQty, avgPrice);
  }

  private ExecutionResult executeLimit(ExecutionRequest request, MarketSnapshot snapshot, LimitPlan plan) {
    List<OrderResponse> responses = new ArrayList<>();
    BigDecimal remaining = request.quantity();
    BigDecimal totalQuote = BigDecimal.ZERO;
    BigDecimal totalQty = BigDecimal.ZERO;
    for (int attempt = 0; attempt <= plan.maxRetries(); attempt++) {
      if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
        break;
      }
      double buffer = plan.bufferBps() * (attempt + 1);
      BigDecimal price = adjustLimitPrice(request.side(), request.referencePrice(), buffer);
      String clientOrderId =
          attempt == 0 ? request.baseClientOrderId() : request.baseClientOrderId() + "-r" + attempt;
      OrderResponse response = submitOrder(request, OrderType.LIMIT, price, remaining, clientOrderId);
      responses.add(response);
      BigDecimal executed = safeQty(response.executedQty());
      if (executed.compareTo(BigDecimal.ZERO) > 0) {
        totalQty = totalQty.add(executed);
        totalQuote = totalQuote.add(price.multiply(executed));
        recordFillMetrics(request, price, executed);
        remaining = remaining.subtract(executed);
      }
      if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
        break;
      }
      sleep(plan.ttlMs());
      limitTtl.record(plan.ttlMs());
      safeCancel(request.symbol(), clientOrderId);
      meterRegistry.counter("exec.limit.replaces", Tags.of("symbol", request.symbol())).increment();
    }
    if (remaining.compareTo(BigDecimal.ZERO) > 0) {
      log.debug("Remaining {} after limit attempts, switching to market", remaining);
      OrderResponse fallback =
          submitOrder(request, OrderType.MARKET, request.referencePrice(), remaining, request.baseClientOrderId() + "-m");
      responses.add(fallback);
      BigDecimal executed = safeQty(fallback.executedQty());
      if (executed.compareTo(BigDecimal.ZERO) > 0) {
        totalQty = totalQty.add(executed);
        BigDecimal price = resolveFillPrice(fallback, request.referencePrice());
        totalQuote = totalQuote.add(price.multiply(executed));
        recordFillMetrics(request, price, executed);
      }
    }
    BigDecimal avgPrice = totalQty.compareTo(BigDecimal.ZERO) > 0 ? totalQuote.divide(totalQty, 8, RoundingMode.HALF_UP) : request.referencePrice();
    return new ExecutionResult(plan, responses, totalQty, avgPrice);
  }

  private ExecutionResult executeTwap(ExecutionRequest request, MarketSnapshot snapshot, TwapPlan plan) {
    int slices = Math.max(1, plan.slices());
    Duration window = plan.window();
    List<OrderResponse> responses = new ArrayList<>();
    BigDecimal sliceQty = request.quantity().divide(BigDecimal.valueOf(slices), 8, RoundingMode.DOWN);
    BigDecimal executedTotal = BigDecimal.ZERO;
    BigDecimal totalQuote = BigDecimal.ZERO;
    Duration delay = window.dividedBy(slices);
    for (int i = 0; i < slices; i++) {
      BigDecimal qty = i == slices - 1 ? request.quantity().subtract(executedTotal) : sliceQty;
      if (qty.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
      }
      String clientOrderId = request.baseClientOrderId() + "-twap-" + i;
      OrderResponse response = submitOrder(request, OrderType.MARKET, request.referencePrice(), qty, clientOrderId);
      responses.add(response);
      BigDecimal executed = safeQty(response.executedQty());
      BigDecimal price = resolveFillPrice(response, request.referencePrice());
      if (executed.compareTo(BigDecimal.ZERO) > 0) {
        executedTotal = executedTotal.add(executed);
        totalQuote = totalQuote.add(price.multiply(executed));
        recordFillMetrics(request, price, executed);
      }
      meterRegistry.counter("exec.twap.slice_fills", Tags.of("symbol", request.symbol())).increment();
      sleep(delay.toMillis());
    }
    BigDecimal avgPrice =
        executedTotal.compareTo(BigDecimal.ZERO) > 0
            ? totalQuote.divide(executedTotal, 8, RoundingMode.HALF_UP)
            : request.referencePrice();
    return new ExecutionResult(plan, responses, executedTotal, avgPrice);
  }

  private ExecutionResult executePov(ExecutionRequest request, MarketSnapshot snapshot, PovPlan plan) {
    List<OrderResponse> responses = new ArrayList<>();
    BigDecimal executedTotal = BigDecimal.ZERO;
    BigDecimal totalQuote = BigDecimal.ZERO;
    BigDecimal targetParticipation = BigDecimal.valueOf(plan.targetParticipation());
    Duration reassess = properties.getPov().reassessInterval();
    BigDecimal remaining = request.quantity();
    int slice = 0;
    while (remaining.compareTo(BigDecimal.ZERO) > 0 && Instant.now(clock).isBefore(request.deadline())) {
      BigDecimal barVolume = snapshot.barVolume() == null ? BigDecimal.ZERO : snapshot.barVolume();
      if (barVolume.compareTo(BigDecimal.ZERO) <= 0) {
        barVolume = remaining.multiply(BigDecimal.valueOf(10));
      }
      BigDecimal desiredQty = barVolume.multiply(targetParticipation).setScale(8, RoundingMode.DOWN);
      if (desiredQty.compareTo(BigDecimal.ZERO) <= 0) {
        desiredQty = remaining.min(barVolume.multiply(BigDecimal.valueOf(0.1)));
      }
      BigDecimal qty = desiredQty.min(remaining);
      String clientOrderId = request.baseClientOrderId() + "-pov-" + slice++;
      OrderResponse response = submitOrder(request, OrderType.MARKET, request.referencePrice(), qty, clientOrderId);
      responses.add(response);
      BigDecimal executed = safeQty(response.executedQty());
      BigDecimal price = resolveFillPrice(response, request.referencePrice());
      if (executed.compareTo(BigDecimal.ZERO) > 0) {
        executedTotal = executedTotal.add(executed);
        totalQuote = totalQuote.add(price.multiply(executed));
        remaining = remaining.subtract(executed);
        recordFillMetrics(request, price, executed);
        updatePovGauge(request.symbol(), executedTotal, request.quantity());
      }
      sleep(reassess.toMillis());
    }
    if (remaining.compareTo(BigDecimal.ZERO) > 0) {
      log.debug("POV deadline reached, sending final market for remaining {}", remaining);
      OrderResponse finalOrder =
          submitOrder(request, OrderType.MARKET, request.referencePrice(), remaining, request.baseClientOrderId() + "-pov-final");
      responses.add(finalOrder);
      BigDecimal executed = safeQty(finalOrder.executedQty());
      BigDecimal price = resolveFillPrice(finalOrder, request.referencePrice());
      if (executed.compareTo(BigDecimal.ZERO) > 0) {
        executedTotal = executedTotal.add(executed);
        totalQuote = totalQuote.add(price.multiply(executed));
        recordFillMetrics(request, price, executed);
        updatePovGauge(request.symbol(), executedTotal, request.quantity());
      }
    }
    BigDecimal avgPrice =
        executedTotal.compareTo(BigDecimal.ZERO) > 0
            ? totalQuote.divide(executedTotal, 8, RoundingMode.HALF_UP)
            : request.referencePrice();
    return new ExecutionResult(plan, responses, executedTotal, avgPrice);
  }

  private OrderResponse submitOrder(
      ExecutionRequest request, OrderType type, BigDecimal price, BigDecimal quantity, String clientOrderId) {
    OrderRequest orderRequest = new OrderRequest();
    orderRequest.setSymbol(request.symbol());
    orderRequest.setSide(request.side());
    orderRequest.setType(type);
    orderRequest.setDryRun(request.dryRun());
    orderRequest.setClientOrderId(clientOrderId == null ? IdGenerator.newClientOrderId() : clientOrderId);
    if (type == OrderType.LIMIT) {
      orderRequest.setPrice(price);
      orderRequest.setQuantity(quantity);
      orderRequest.setTimeInForce("GTC");
    } else {
      orderRequest.setQuantity(quantity);
      if (request.side() == OrderSide.BUY) {
        orderRequest.setQuoteAmount(price.multiply(quantity));
      }
    }
    OrderValidator.validate(orderRequest, request.exchangeInfo(), request.referencePrice());
    Instant submittedAt = Instant.now(clock);
    tcaService.recordSubmission(
        orderRequest.getClientOrderId(),
        request.symbol(),
        request.side(),
        type,
        request.referencePrice(),
        request.volume24h(),
        request.atr(),
        submittedAt);
    OrderResponse response = orderService.placeOrder(orderRequest);
    long queue =
        response.transactTime() == null
            ? 0
            : Duration.between(submittedAt, response.transactTime()).toMillis();
    queueTimes.record(queue);
    anomalyDetector.recordQueueTime(request.symbol(), queue);
    BigDecimal fillPrice = resolveFillPrice(response, price);
    tcaService.recordFill(
        response.clientOrderId(), response.orderId(), fillPrice, BigDecimal.valueOf(request.spreadBps()), response.transactTime());
    return response;
  }

  private void safeCancel(String symbol, String clientOrderId) {
    try {
      binanceClient.cancelOrder(symbol, clientOrderId);
    } catch (Exception ex) {
      log.debug("Failed to cancel order {} for {}: {}", clientOrderId, symbol, ex.getMessage());
    }
  }

  private void recordFillMetrics(ExecutionRequest request, BigDecimal price, BigDecimal qty) {
    if (qty.compareTo(BigDecimal.ZERO) <= 0) {
      return;
    }
    double slippage =
        request.referencePrice().compareTo(BigDecimal.ZERO) <= 0
            ? Double.NaN
            : price.subtract(request.referencePrice())
                    .divide(request.referencePrice(), 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(10000))
                    .doubleValue() * (request.side() == OrderSide.BUY ? 1 : -1);
    if (!Double.isNaN(slippage)) {
      updateSlippageGauge(request.symbol(), slippage);
      anomalyDetector.recordSlippage(request.symbol(), slippage);
    }
  }

  private BigDecimal adjustLimitPrice(OrderSide side, BigDecimal reference, double bufferBps) {
    BigDecimal factor = BigDecimal.valueOf(bufferBps).divide(BigDecimal.valueOf(10000), 8, RoundingMode.HALF_UP);
    if (side == OrderSide.BUY) {
      return reference.multiply(BigDecimal.ONE.subtract(factor)).setScale(8, RoundingMode.HALF_UP);
    }
    return reference.multiply(BigDecimal.ONE.add(factor)).setScale(8, RoundingMode.HALF_UP);
  }

  private BigDecimal resolveFillPrice(OrderResponse response, BigDecimal fallback) {
    if (response.price() != null && response.price().compareTo(BigDecimal.ZERO) > 0) {
      return response.price();
    }
    BigDecimal executed = safeQty(response.executedQty());
    if (executed.compareTo(BigDecimal.ZERO) > 0 && response.cummulativeQuoteQty() != null) {
      return response.cummulativeQuoteQty().divide(executed, 8, RoundingMode.HALF_UP);
    }
    return fallback;
  }

  private BigDecimal safeQty(BigDecimal qty) {
    return qty == null ? BigDecimal.ZERO : qty;
  }

  private void updateSlippageGauge(String symbol, double value) {
    RunningAverage average =
        slippageAvg.computeIfAbsent(
            symbol,
            key -> {
              RunningAverage avg = new RunningAverage();
              meterRegistry.gauge("exec.slippage.avg_bps", Tags.of("symbol", key), avg.value, AtomicReference::get);
              return avg;
            });
    average.update(value);
  }

  private void updatePovGauge(String symbol, BigDecimal executed, BigDecimal target) {
    AtomicReference<Double> ref =
        povGauge.computeIfAbsent(
            symbol,
            key -> {
              AtomicReference<Double> reference = new AtomicReference<>(0.0);
              meterRegistry.gauge("exec.pov.participation", Tags.of("symbol", key), reference, AtomicReference::get);
              return reference;
            });
    if (target.compareTo(BigDecimal.ZERO) <= 0) {
      return;
    }
    double participation = executed.divide(target, 6, RoundingMode.HALF_UP).doubleValue();
    ref.set(participation);
  }

  private void sleep(long millis) {
    if (millis <= 0) {
      return;
    }
    try {
      Thread.sleep(Math.min(millis, 1000));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  private static class RunningAverage {
    private final AtomicReference<Double> value = new AtomicReference<>(0.0);
    private final AtomicInteger count = new AtomicInteger();

    void update(double sample) {
      int newCount = count.updateAndGet(prev -> prev + 1);
      value.updateAndGet(prev -> prev + (sample - prev) / Math.max(1, newCount));
    }
  }

  public record ExecutionResult(OrderPlan plan, List<OrderResponse> orders, BigDecimal executedQty, BigDecimal averagePrice) {
    public ExecutionResult {
      orders = List.copyOf(orders);
    }
  }
}
