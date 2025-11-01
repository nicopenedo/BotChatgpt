package com.bottrading.execution;

import com.bottrading.config.ExecutionProperties;
import com.bottrading.config.TradingProps;
import com.bottrading.model.dto.ExchangeInfo;
import com.bottrading.model.dto.OrderRequest;
import com.bottrading.model.dto.OrderResponse;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.tca.TcaService;
import com.bottrading.service.trading.OrderService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionEngineIntegrationTest {

  private ExecutionProperties properties;
  private ExecutionPolicy policy;
  private OrderService orderService;
  private BinanceClient binanceClient;
  private TcaService tcaService;
  private MeterRegistry meterRegistry;
  private Clock clock;
  private ExecutionEngine engine;
  private TradingProps tradingProps;

  @BeforeEach
  void setup() {
    properties = new ExecutionProperties();
    policy = new ExecutionPolicy(properties);
    orderService = mock(OrderService.class);
    binanceClient = mock(BinanceClient.class);
    meterRegistry = new SimpleMeterRegistry();
    tradingProps = new TradingProps();
    tcaService = new TcaService(tradingProps, meterRegistry, mock(com.bottrading.repository.TradeFillRepository.class));
    clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
    engine = new ExecutionEngine(policy, orderService, binanceClient, tcaService, properties, meterRegistry, clock);
  }

  @Test
  void limitShouldFallbackToMarketAfterTtl() {
    properties.getLimit().setTtlMs(50);
    properties.getLimit().setMaxRetries(0);
    ExecutionRequest request = baseRequest(OrderSide.BUY, new BigDecimal("0.1"), ExecutionRequest.Urgency.LOW);
    MarketSnapshot snapshot = snapshot(BigDecimal.TEN);

    AtomicInteger counter = new AtomicInteger();
    when(orderService.placeOrder(any()))
        .thenAnswer(
            invocation -> {
              OrderRequest orderRequest = invocation.getArgument(0);
              if (counter.getAndIncrement() == 0) {
                return new OrderResponse(
                    "order-1",
                    orderRequest.getClientOrderId(),
                    orderRequest.getSymbol(),
                    orderRequest.getSide(),
                    orderRequest.getType(),
                    orderRequest.getPrice(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    "NEW",
                    Instant.now(clock));
              }
              BigDecimal qty = Optional.ofNullable(orderRequest.getQuantity()).orElse(BigDecimal.ZERO);
              BigDecimal price = Optional.ofNullable(orderRequest.getPrice()).orElse(new BigDecimal("100"));
              return new OrderResponse(
                  "order-2",
                  orderRequest.getClientOrderId(),
                  orderRequest.getSymbol(),
                  orderRequest.getSide(),
                  orderRequest.getType(),
                  price,
                  qty,
                  price.multiply(qty),
                  "FILLED",
                  Instant.now(clock));
            });

    ExecutionEngine.ExecutionResult result = engine.execute(request, snapshot);

    assertThat(result.plan()).isInstanceOf(ExecutionPolicy.LimitPlan.class);
    assertThat(result.orders()).hasSize(2);
    assertThat(result.orders().get(1).type()).isEqualTo(OrderType.MARKET);
    assertThat(result.executedQty()).isEqualByComparingTo("0.1");
  }

  @Test
  void twapShouldSliceQuantityEvenly() {
    properties.getTwap().setSlices(3);
    properties.getTwap().setWindowSec(1);
    ExecutionRequest request = baseRequest(OrderSide.BUY, new BigDecimal("0.9"), ExecutionRequest.Urgency.MEDIUM);
    MarketSnapshot snapshot = new MarketSnapshot(new BigDecimal("100"), 1, 5, 0, new BigDecimal("1"), BigDecimal.valueOf(100));

    AtomicInteger counter = new AtomicInteger();
    when(orderService.placeOrder(any()))
        .thenAnswer(
            invocation -> {
              OrderRequest orderRequest = invocation.getArgument(0);
              BigDecimal qty = Optional.ofNullable(orderRequest.getQuantity()).orElse(BigDecimal.ZERO);
              BigDecimal price = Optional.ofNullable(orderRequest.getPrice()).orElse(new BigDecimal("100"));
              return new OrderResponse(
                  "twap-" + counter.getAndIncrement(),
                  orderRequest.getClientOrderId(),
                  orderRequest.getSymbol(),
                  orderRequest.getSide(),
                  orderRequest.getType(),
                  price,
                  qty,
                  price.multiply(qty),
                  "FILLED",
                  Instant.now(clock));
            });

    ExecutionEngine.ExecutionResult result = engine.execute(request, snapshot);

    assertThat(result.plan()).isInstanceOf(ExecutionPolicy.TwapPlan.class);
    assertThat(result.orders()).hasSize(3);
    assertThat(result.executedQty()).isEqualByComparingTo("0.9");
  }

  @Test
  void povShouldRespectTargetParticipation() {
    properties.getPov().setTargetPct(0.1);
    properties.getPov().setReassessIntervalSec(0);
    ExecutionRequest request = baseRequest(OrderSide.SELL, new BigDecimal("0.2"), ExecutionRequest.Urgency.MEDIUM);
    MarketSnapshot snapshot = new MarketSnapshot(new BigDecimal("100"), 1, 5, 0, new BigDecimal("1"), BigDecimal.valueOf(100));

    AtomicInteger counter = new AtomicInteger();
    when(orderService.placeOrder(any()))
        .thenAnswer(
            invocation -> {
              OrderRequest orderRequest = invocation.getArgument(0);
              BigDecimal qty = Optional.ofNullable(orderRequest.getQuantity()).orElse(BigDecimal.ZERO);
              BigDecimal price = Optional.ofNullable(orderRequest.getPrice()).orElse(new BigDecimal("100"));
              BigDecimal executed = counter.getAndIncrement() == 0 ? new BigDecimal("0.1") : qty;
              return new OrderResponse(
                  "pov-" + counter.get(),
                  orderRequest.getClientOrderId(),
                  orderRequest.getSymbol(),
                  orderRequest.getSide(),
                  orderRequest.getType(),
                  price,
                  executed,
                  price.multiply(executed),
                  "FILLED",
                  Instant.now(clock));
            });

    ExecutionEngine.ExecutionResult result = engine.execute(request, snapshot);

    assertThat(result.plan()).isInstanceOf(ExecutionPolicy.PovPlan.class);
    assertThat(result.executedQty()).isEqualByComparingTo("0.2");
    double participation =
        meterRegistry
            .get("exec.pov.participation")
            .tag("symbol", request.symbol())
            .gauge()
            .value();
    assertThat(participation).isCloseTo(1.0, within(0.01));
  }

  private ExecutionRequest baseRequest(OrderSide side, BigDecimal quantity, ExecutionRequest.Urgency urgency) {
    ExchangeInfo exchangeInfo = new ExchangeInfo(new BigDecimal("0.01"), new BigDecimal("0.001"), BigDecimal.ONE);
    return new ExecutionRequest(
        "BTCUSDT",
        side,
        quantity,
        new BigDecimal("100"),
        quantity.multiply(new BigDecimal("100")),
        exchangeInfo,
        urgency,
        5,
        Instant.now(clock).plusSeconds(30),
        true,
        BigDecimal.valueOf(1_000_000),
        BigDecimal.ONE,
        5,
        5,
        0,
        "base-id");
  }

  private MarketSnapshot snapshot(BigDecimal spread) {
    return new MarketSnapshot(new BigDecimal("100"), spread.doubleValue(), 5, 0, new BigDecimal("1"), BigDecimal.valueOf(100));
  }
}
