package com.bottrading.service;

import com.bottrading.config.TradingProps;
import com.bottrading.model.dto.AccountBalancesResponse;
import com.bottrading.model.dto.AccountBalancesResponse.Balance;
import com.bottrading.model.dto.ExchangeInfo;
import com.bottrading.model.dto.Kline;
import com.bottrading.model.dto.OrderRequest;
import com.bottrading.model.dto.OrderResponse;
import com.bottrading.model.dto.PriceTicker;
import com.bottrading.model.entity.OrderEntity;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import com.bottrading.repository.OrderRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderExecutionServiceTest {

  private TradingProps props;
  private TradingState tradingState;
  private SimpleMeterRegistry meterRegistry;
  private TestBinanceClient binanceClient;
  private RiskGuard riskGuard;
  private OrderRepositoryStub orderRepositoryStub;
  private OrderService orderService;
  private OrderExecutionService executionService;

  @BeforeEach
  void setup() {
    props = new TradingProps();
    props.setSymbol("BTCUSDT");
    props.setRiskPerTradePct(BigDecimal.valueOf(10));
    props.setDryRun(true);
    props.setLiveEnabled(true);
    props.setMinVolume24h(BigDecimal.ONE);

    tradingState = new TradingState();
    tradingState.setLiveEnabled(true);

    meterRegistry = new SimpleMeterRegistry();
    binanceClient = new TestBinanceClient();
    binanceClient.exchangeInfo = new ExchangeInfo(new BigDecimal("0.01"), new BigDecimal("0.001"), new BigDecimal("10"));
    binanceClient.price = new BigDecimal("100");

    riskGuard = new RiskGuard(props, tradingState, meterRegistry);
    orderRepositoryStub = new OrderRepositoryStub();
    orderService = new OrderService(binanceClient, props, tradingState, riskGuard, orderRepositoryStub.repository, meterRegistry);
    executionService = new OrderExecutionService(props, binanceClient, orderService, riskGuard, meterRegistry);
  }

  @Test
  void shouldAllocateQuoteForBuyUsingRiskFraction() {
    binanceClient.setBalance("USDT", new BigDecimal("1000"));
    binanceClient.setBalance("BTC", BigDecimal.ZERO);

    OrderResponse response =
        executionService
            .execute("BTCUSDT|1m|1704067200000", SignalResult.buy(1.0, "test"), 1704067200000L)
            .orElseThrow();

    assertThat(orderRepositoryStub.entities).hasSize(1);
    OrderEntity order = orderRepositoryStub.entities.get(0);
    assertThat(order.getSide()).isEqualTo(OrderSide.BUY);
    assertThat(response.cummulativeQuoteQty()).isEqualByComparingTo("100");
    assertThat(order.getClientOrderId()).isEqualTo("candle-BTCUSDT-1m-1704067200000");
  }

  @Test
  void shouldAllocateQuantityForSellUsingRiskFraction() {
    binanceClient.price = new BigDecimal("200");
    binanceClient.setBalance("BTC", new BigDecimal("1"));
    binanceClient.setBalance("USDT", BigDecimal.ZERO);

    OrderResponse response =
        executionService
            .execute("BTCUSDT|1m|1704067260000", SignalResult.sell(1.0, "test"), 1704067260000L)
            .orElseThrow();

    assertThat(orderRepositoryStub.entities).hasSize(1);
    OrderEntity order = orderRepositoryStub.entities.get(0);
    assertThat(order.getSide()).isEqualTo(OrderSide.SELL);
    assertThat(response.executedQty()).isEqualByComparingTo("0.5");
    assertThat(order.getClientOrderId()).isEqualTo("candle-BTCUSDT-1m-1704067260000");
  }

  private static class TestBinanceClient implements BinanceClient {
    private BigDecimal price = BigDecimal.ONE;
    private BigDecimal volume24h = BigDecimal.ONE;
    private ExchangeInfo exchangeInfo = new ExchangeInfo(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
    private final Map<String, BigDecimal> balances = new HashMap<>();

    void setBalance(String asset, BigDecimal amount) {
      balances.put(asset, amount);
    }

    @Override
    public PriceTicker getPrice(String symbol) {
      return new PriceTicker(symbol, price);
    }

    @Override
    public List<Kline> getKlines(String symbol, String interval, int limit) {
      return List.of();
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
      List<Balance> results = new ArrayList<>();
      for (String asset : assets) {
        results.add(new Balance(asset, balances.getOrDefault(asset, BigDecimal.ZERO), BigDecimal.ZERO));
      }
      return new AccountBalancesResponse(results);
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
          if (entity.getClientOrderId() == null) {
            entity.setClientOrderId("SIM");
          }
          entities.add(entity);
          yield entity;
        }
        case "findByOrderId" -> entities.stream().filter(e -> e.getOrderId().equals(args[0])).findFirst();
        case "findAll" -> new ArrayList<>(entities);
        case "deleteAll" -> {
          entities.clear();
          yield null;
        }
        default -> throw new UnsupportedOperationException("Method not supported: " + method.getName());
      };
    }
  }
}
