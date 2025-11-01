package com.bottrading.executor;

import com.bottrading.config.BinanceProperties;
import com.bottrading.config.TradingProps;
import com.bottrading.config.TradingProps.Mode;
import com.bottrading.model.dto.AccountBalancesResponse;
import com.bottrading.model.dto.AccountBalancesResponse.Balance;
import com.bottrading.model.dto.ExchangeInfo;
import com.bottrading.model.dto.Kline;
import com.bottrading.model.dto.OrderRequest;
import com.bottrading.model.dto.OrderResponse;
import com.bottrading.model.dto.PriceTicker;
import com.bottrading.model.entity.DecisionEntity;
import com.bottrading.model.entity.OrderEntity;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import com.bottrading.repository.DecisionRepository;
import com.bottrading.repository.OrderRepository;
import com.bottrading.service.OrderExecutionService;
import com.bottrading.service.StrategyService;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.risk.RiskGuard;
import com.bottrading.service.risk.TradingState;
import com.bottrading.service.trading.OrderService;
import com.bottrading.strategy.SignalResult;
import com.bottrading.strategy.SignalSide;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;

class TradingSchedulerTest {

  private TradingProps props;
  private TradingState tradingState;
  private SimpleMeterRegistry meterRegistry;
  private TestBinanceClient binanceClient;
  private RiskGuard riskGuard;
  private OrderRepositoryStub orderRepositoryStub;
  private DecisionRepositoryStub decisionRepositoryStub;
  private OrderService orderService;
  private OrderExecutionService executionService;
  private StrategyService strategyService;
  private WSKlineSubscriber noopSubscriber;
  private ObjectProvider<Clock> clockProvider;

  @BeforeEach
  void setup() {
    props = new TradingProps();
    props.setSymbol("BTCUSDT");
    props.setLiveEnabled(true);
    props.setRiskPerTradePct(BigDecimal.valueOf(10));
    props.setMinVolume24h(BigDecimal.valueOf(100));
    props.setMode(Mode.WEBSOCKET);
    props.setJitterSeconds(0);
    props.setInterval("1m");

    tradingState = new TradingState();
    tradingState.setLiveEnabled(true);

    meterRegistry = new SimpleMeterRegistry();
    binanceClient = new TestBinanceClient();
    binanceClient.price = new BigDecimal("100");
    binanceClient.exchangeInfo = new ExchangeInfo(new BigDecimal("0.01"), new BigDecimal("0.001"), new BigDecimal("10"));
    binanceClient.volume24h = BigDecimal.valueOf(1_000_000);
    binanceClient.addBalance("BTC", new BigDecimal("2"));
    binanceClient.addBalance("USDT", new BigDecimal("1000"));

    riskGuard = new RiskGuard(props, tradingState, meterRegistry);
    orderRepositoryStub = new OrderRepositoryStub();
    decisionRepositoryStub = new DecisionRepositoryStub();
    orderService = new OrderService(binanceClient, props, tradingState, riskGuard, orderRepositoryStub.repository, meterRegistry);
    executionService = new OrderExecutionService(props, binanceClient, orderService, riskGuard, meterRegistry);
    strategyService = new StubStrategyService(props, SignalResult.buy(1.0, "test"));
    noopSubscriber = new WSKlineSubscriber(new BinanceProperties(null, null, "https://testnet.binance.vision", "wss://testnet.binance.vision/ws/")) {
      @Override
      public void start(String symbol, String interval, java.util.function.Consumer<KlineEvent> listener) {
        // no-op for tests
      }
    };
    clockProvider = new StaticClockProvider(Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void shouldProcessSingleDecisionPerCandle() {
    TradingScheduler scheduler =
        new TradingScheduler(
            props,
            strategyService,
            tradingState,
            riskGuard,
            binanceClient,
            executionService,
            decisionRepositoryStub.repository,
            meterRegistry,
            noopSubscriber,
            clockProvider);

    scheduler.onCandleClosed("BTCUSDT", "1m", 1704067200000L);
    scheduler.onCandleClosed("BTCUSDT", "1m", 1704067200000L);

    assertThat(orderRepositoryStub.entities).hasSize(1);
    assertThat(decisionRepositoryStub.store).hasSize(1);
    DecisionEntity decision = decisionRepositoryStub.store.values().iterator().next();
    assertThat(decision.getDecisionKey()).isEqualTo("BTCUSDT|1m|1704067200000");
    assertThat(decision.isExecuted()).isTrue();
  }

  @Test
  void shouldDetectNewCandleWhenPolling() {
    props.setMode(Mode.POLLING);
    binanceClient.queueKlines(
        List.of(new Kline(Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T00:01:00Z"), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE)),
        List.of(new Kline(Instant.parse("2024-01-01T00:01:00Z"), Instant.parse("2024-01-01T00:02:00Z"), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE)));

    TradingScheduler scheduler =
        new TradingScheduler(
            props,
            strategyService,
            tradingState,
            riskGuard,
            binanceClient,
            executionService,
            decisionRepositoryStub.repository,
            meterRegistry,
            noopSubscriber,
            clockProvider);

    scheduler.pollCandles();
    scheduler.pollCandles();

    assertThat(orderRepositoryStub.entities).hasSize(2);
    assertThat(decisionRepositoryStub.store).hasSize(2);
  }

  private static class StubStrategyService extends StrategyService {

    private final SignalResult result;

    StubStrategyService(TradingProps props, SignalResult result) {
      super(null, null, props);
      this.result = result;
    }

    @Override
    public SignalResult decide(String symbol) {
      return result;
    }
  }

  private static class StaticClockProvider implements ObjectProvider<Clock> {

    private final Clock clock;

    StaticClockProvider(Clock clock) {
      this.clock = clock;
    }

    @Override
    public Clock getObject(Object... args) {
      return clock;
    }

    @Override
    public Clock getIfAvailable() {
      return clock;
    }

    @Override
    public Clock getIfUnique() {
      return clock;
    }

    @Override
    public Clock getObject() {
      return clock;
    }

    @Override
    public Stream<Clock> stream() {
      return Stream.of(clock);
    }

    @Override
    public Stream<Clock> orderedStream() {
      return Stream.of(clock);
    }
  }

  private static class DecisionRepositoryStub {
    private final Map<String, DecisionEntity> store = new HashMap<>();
    private final DecisionRepository repository =
        (DecisionRepository)
            Proxy.newProxyInstance(
                DecisionRepository.class.getClassLoader(),
                new Class[] {DecisionRepository.class},
                new DecisionInvocationHandler(store));
  }

  private static class DecisionInvocationHandler implements InvocationHandler {
    private final Map<String, DecisionEntity> store;

    DecisionInvocationHandler(Map<String, DecisionEntity> store) {
      this.store = store;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      return switch (method.getName()) {
        case "save" -> {
          DecisionEntity entity = (DecisionEntity) args[0];
          store.put(entity.getDecisionKey(), entity);
          yield entity;
        }
        case "existsById" -> store.containsKey(args[0]);
        case "findByDecisionKey" -> Optional.ofNullable(store.get(args[0]));
        case "findTopBySymbolOrderByDecidedAtDesc" ->
            store.values().stream()
                .filter(entity -> entity.getSymbol().equals(args[0]))
                .sorted((a, b) -> b.getDecidedAt().compareTo(a.getDecidedAt()))
                .findFirst();
        case "countByDecidedAtAfter" ->
            store.values().stream().filter(entity -> entity.getDecidedAt().isAfter((Instant) args[0])).count();
        case "findAll" -> new ArrayList<>(store.values());
        case "deleteAll" -> {
          store.clear();
          yield null;
        }
        default -> throw new UnsupportedOperationException("Method not supported: " + method.getName());
      };
    }
  }

  private static class OrderRepositoryStub {
    private final List<OrderEntity> entities = new ArrayList<>();
    private final OrderRepository repository =
        (OrderRepository)
            Proxy.newProxyInstance(
                OrderRepository.class.getClassLoader(),
                new Class[] {OrderRepository.class},
                new OrderInvocationHandler(entities));
  }

  private static class OrderInvocationHandler implements InvocationHandler {
    private final List<OrderEntity> entities;

    OrderInvocationHandler(List<OrderEntity> entities) {
      this.entities = entities;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      return switch (method.getName()) {
        case "save" -> {
          OrderEntity entity = (OrderEntity) args[0];
          entities.add(entity);
          yield entity;
        }
        case "findByOrderId" ->
            entities.stream()
                .filter(e -> e.getOrderId().equals(args[0]))
                .findFirst();
        case "findAll" -> new ArrayList<>(entities);
        case "deleteAll" -> {
          entities.clear();
          yield null;
        }
        default -> throw new UnsupportedOperationException("Method not supported: " + method.getName());
      };
    }
  }

  private static class TestBinanceClient implements BinanceClient {
    private BigDecimal price = BigDecimal.ONE;
    private BigDecimal volume24h = BigDecimal.ONE;
    private ExchangeInfo exchangeInfo = new ExchangeInfo(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
    private final Map<String, BigDecimal> balances = new HashMap<>();
    private final Deque<List<Kline>> queuedKlines = new ArrayDeque<>();

    void addBalance(String asset, BigDecimal amount) {
      balances.put(asset, amount);
    }

    void queueKlines(List<Kline>... responses) {
      queuedKlines.clear();
      for (List<Kline> response : responses) {
        queuedKlines.addLast(response);
      }
    }

    @Override
    public PriceTicker getPrice(String symbol) {
      return new PriceTicker(symbol, price);
    }

    @Override
    public List<Kline> getKlines(String symbol, String interval, int limit) {
      if (queuedKlines.isEmpty()) {
        return List.of();
      }
      return queuedKlines.removeFirst();
    }

    @Override
    public BigDecimal get24hQuoteVolume(String symbol) {
      return volume24h;
    }

    @Override
    public ExchangeInfo getExchangeInfo(String symbol) {
      return exchangeInfo;
    }

    @Override
    public AccountBalancesResponse getAccountBalances(List<String> assets) {
      List<Balance> balancesList = new ArrayList<>();
      for (String asset : assets) {
        BigDecimal free = balances.getOrDefault(asset, BigDecimal.ZERO);
        balancesList.add(new Balance(asset, free, BigDecimal.ZERO));
      }
      return new AccountBalancesResponse(balancesList);
    }

    @Override
    public BigDecimal getTradingCommission(String symbol) {
      return BigDecimal.ZERO;
    }

    @Override
    public OrderResponse placeOrder(OrderRequest request) {
      throw new UnsupportedOperationException("Not expected in tests");
    }

    @Override
    public OrderResponse getOrder(String symbol, String orderId) {
      throw new UnsupportedOperationException("Not expected in tests");
    }
  }
}
