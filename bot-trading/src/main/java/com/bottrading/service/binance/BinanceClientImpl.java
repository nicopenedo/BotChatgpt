package com.bottrading.service.binance;

import com.binance.connector.client.exceptions.BinanceClientException;
import com.binance.connector.client.exceptions.BinanceServerException;
import com.binance.connector.client.impl.SpotClientImpl;
import com.bottrading.chaos.ChaosSuite;
import com.bottrading.config.BinanceProperties;
import com.bottrading.config.CacheConfig;
import com.bottrading.model.dto.AccountBalancesResponse;
import com.bottrading.model.dto.ExchangeInfo;
import com.bottrading.model.dto.Kline;
import com.bottrading.model.dto.OrderRequest;
import com.bottrading.model.dto.OrderResponse;
import com.bottrading.model.dto.PriceTicker;
import com.bottrading.model.entity.ManagedOrderEntity;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import com.bottrading.throttle.Endpoint;
import com.bottrading.throttle.Throttle;
import com.bottrading.util.IdGenerator;
import com.bottrading.util.OrderValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
public class BinanceClientImpl implements BinanceClient {

  private static final Logger log = LoggerFactory.getLogger(BinanceClientImpl.class);

  private final SpotClientImpl spotClient;
  private final CacheManager cacheManager;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Throttle throttle;
  private final ChaosSuite chaosSuite;
  private final MeterRegistry meterRegistry;
  private final ConcurrentMap<String, Counter> apiCounters = new ConcurrentHashMap<>();
  private final ConcurrentMap<Endpoint, Timer> latencyTimers = new ConcurrentHashMap<>();

  public BinanceClientImpl(
      BinanceProperties properties,
      CacheManager cacheManager,
      Throttle throttle,
      ChaosSuite chaosSuite,
      MeterRegistry meterRegistry) {
    this.spotClient =
        new SpotClientImpl(properties.apiKey(), properties.apiSecret(), properties.baseUrl());
    this.cacheManager = cacheManager;
    this.throttle = throttle;
    this.chaosSuite = chaosSuite;
    this.meterRegistry = meterRegistry;
    for (Endpoint endpoint : Endpoint.values()) {
      latencyTimers.computeIfAbsent(
          endpoint,
          key ->
              Timer.builder("binance.api.latency")
                  .description("Latency of Binance REST calls")
                  .tag("endpoint", key.name().toLowerCase())
                  .publishPercentileHistogram()
                  .register(meterRegistry));
      for (String status : List.of("success", "rate_limited", "server_error", "error")) {
        counterFor(endpoint, status).increment(0);
      }
    }
  }

  @Override
  public PriceTicker getPrice(String symbol) {
    String response =
        execute(
            Endpoint.PRICE_TICKER,
            symbol,
            () -> spotClient.createMarket().tickerSymbol(Map.of("symbol", symbol)));
    JsonNode node = readTree(response);
    return new PriceTicker(symbol, new BigDecimal(node.get("price").asText()));
  }

  @Override
  public List<Kline> getKlines(String symbol, String interval, int limit) {
    Map<String, Object> params = new HashMap<>();
    params.put("symbol", symbol);
    params.put("interval", interval);
    params.put("limit", limit);
    String response =
        execute(Endpoint.KLINES, symbol, () -> spotClient.createMarket().klines(params));
    JsonNode array = readTree(response);
    List<Kline> klines = new ArrayList<>();
    for (JsonNode kline : array) {
      klines.add(
          new Kline(
              Instant.ofEpochMilli(kline.get(0).asLong()),
              Instant.ofEpochMilli(kline.get(6).asLong()),
              new BigDecimal(kline.get(1).asText()),
              new BigDecimal(kline.get(2).asText()),
              new BigDecimal(kline.get(3).asText()),
              new BigDecimal(kline.get(4).asText()),
              new BigDecimal(kline.get(5).asText())));
    }
    return klines;
  }

  @Override
  public BigDecimal get24hQuoteVolume(String symbol) {
    String response =
        execute(
            Endpoint.TICKER_24H,
            symbol,
            () -> spotClient.createMarket().ticker24H(Map.of("symbol", symbol)));
    JsonNode node = readTree(response);
    return new BigDecimal(node.get("quoteVolume").asText());
  }

  @Override
  public ExchangeInfo getExchangeInfo(String symbol) {
    Cache cache = cacheManager.getCache(CacheConfig.EXCHANGE_INFO_CACHE);
    ExchangeInfo cached = cache.get(symbol, ExchangeInfo.class);
    if (cached != null) {
      return cached;
    }
    String response =
        execute(
            Endpoint.EXCHANGE_INFO,
            symbol,
            () -> spotClient.createMarket().exchangeInfo(Map.of("symbol", symbol)));
    JsonNode root = readTree(response);
    JsonNode symbolNode = root.path("symbols").get(0);
    BigDecimal tickSize = BigDecimal.ONE;
    BigDecimal stepSize = BigDecimal.ONE;
    BigDecimal minNotional = BigDecimal.ZERO;
    for (JsonNode filter : symbolNode.path("filters")) {
      String type = filter.get("filterType").asText();
      switch (type) {
        case "PRICE_FILTER" -> tickSize = new BigDecimal(filter.get("tickSize").asText());
        case "LOT_SIZE" -> stepSize = new BigDecimal(filter.get("stepSize").asText());
        case "MIN_NOTIONAL", "NOTIONAL" ->
            minNotional = new BigDecimal(filter.get("minNotional").asText());
        default -> {
        }
      }
    }
    ExchangeInfo info = new ExchangeInfo(tickSize, stepSize, minNotional);
    cache.put(symbol, info);
    return info;
  }

  @Override
  public AccountBalancesResponse getAccountBalances(List<String> assets) {
    String response = execute(Endpoint.ACCOUNT_INFORMATION, null, () -> spotClient.createTrade().account(new HashMap<>()));
    JsonNode node = readTree(response);
    List<AccountBalancesResponse.Balance> balances = new ArrayList<>();
    for (JsonNode balance : node.path("balances")) {
      String asset = balance.get("asset").asText();
      if (assets == null || assets.isEmpty() || assets.contains(asset)) {
        balances.add(
            new AccountBalancesResponse.Balance(
                asset,
                new BigDecimal(balance.get("free").asText()),
                new BigDecimal(balance.get("locked").asText())));
      }
    }
    return new AccountBalancesResponse(balances);
  }

  @Override
  public BigDecimal getTradingCommission(String symbol) {
    Cache cache = cacheManager.getCache(CacheConfig.COMMISSION_CACHE);
    BigDecimal cached = cache.get(symbol, BigDecimal.class);
    if (cached != null) {
      return cached;
    }
    Map<String, Object> params = new HashMap<>();
    params.put("symbol", symbol);
    String response =
        execute(Endpoint.COMMISSION, symbol, () -> spotClient.createTrade().commission(params));
    JsonNode node = readTree(response);
    BigDecimal maker = new BigDecimal(node.get("makerCommission").asText());
    cache.put(symbol, maker);
    return maker;
  }

  @Override
  public OrderResponse placeOrder(OrderRequest request) {
    ExchangeInfo exchangeInfo = getExchangeInfo(request.getSymbol());
    BigDecimal lastPrice = getPrice(request.getSymbol()).price();
    OrderValidator.validate(request, exchangeInfo, lastPrice);

    Map<String, Object> params = new HashMap<>();
    params.put("symbol", request.getSymbol());
    params.put("side", request.getSide().name());
    params.put("type", request.getType().name());
    String clientOrderId =
        request.getClientOrderId() != null ? request.getClientOrderId() : IdGenerator.newClientOrderId();
    params.put("newClientOrderId", clientOrderId);
    if (request.getType() == OrderType.LIMIT) {
      params.put("price", request.getPrice().toPlainString());
      params.put("quantity", request.getQuantity().toPlainString());
      params.put("timeInForce", request.getTimeInForce() != null ? request.getTimeInForce() : "GTC");
    } else {
      if (request.getSide() == OrderSide.BUY && request.getQuoteAmount() != null) {
        params.put("quoteOrderQty", request.getQuoteAmount().toPlainString());
      } else {
        params.put("quantity", request.getQuantity().toPlainString());
      }
      if (request.getTimeInForce() != null) {
        params.put("timeInForce", request.getTimeInForce());
      }
    }

    String response =
        execute(Endpoint.NEW_ORDER, request.getSymbol(), () -> spotClient.createTrade().newOrder(params));
    return mapOrderResponse(response);
  }

  @Override
  public OrderResponse getOrder(String symbol, String orderId) {
    Map<String, Object> params = new HashMap<>();
    params.put("symbol", symbol);
    params.put("orderId", orderId);
    String response =
        execute(Endpoint.ORDER_STATUS, symbol, () -> spotClient.createTrade().getOrder(params));
    return mapOrderResponse(response);
  }

  @Override
  public boolean placeOcoOrder(String symbol, ManagedOrderEntity stopLoss, ManagedOrderEntity takeProfit) {
    throw new UnsupportedOperationException("OCO not implemented for Spot connector");
  }

  @Override
  public void placeChildOrder(ManagedOrderEntity order) {
    log.debug("Simulating placement of managed order {}", order.getClientOrderId());
  }

  @Override
  public void cancelOrder(ManagedOrderEntity order) {
    log.debug("Simulating cancel of managed order {}", order.getClientOrderId());
  }

  @Override
  public void cancelOrder(String symbol, String clientOrderId) {
    log.debug("Simulating cancel for symbol {} clientOrderId {}", symbol, clientOrderId);
  }

  @Override
  public List<ExchangeOrder> getOpenOrders(String symbol) {
    return List.of();
  }

  @Override
  public List<ExchangeOrder> getRecentOrders(String symbol, int lookbackMinutes) {
    return List.of();
  }

  @Override
  public String startUserDataStream() {
    throw new UnsupportedOperationException("User data stream not configured");
  }

  @Override
  public void keepAliveUserDataStream(String listenKey) {
    log.debug("Skipping keepAlive for listenKey {}", listenKey);
  }

  @Override
  public void closeUserDataStream(String listenKey) {
    log.debug("Skipping close for listenKey {}", listenKey);
  }

  @Override
  public void connectUserDataStream(String listenKey, Consumer<String> onMessage, Consumer<Throwable> onError) {
    throw new UnsupportedOperationException("User data stream WS not implemented");
  }

  private OrderResponse mapOrderResponse(String response) {
    JsonNode node = readTree(response);
    return new OrderResponse(
        node.path("orderId").asText(),
        node.path("clientOrderId").asText(),
        node.path("symbol").asText(),
        OrderSide.valueOf(node.path("side").asText()),
        OrderType.valueOf(node.path("type").asText()),
        new BigDecimal(node.path("price").asText("0")),
        new BigDecimal(node.path("executedQty").asText("0")),
        new BigDecimal(node.path("cummulativeQuoteQty").asText("0")),
        node.path("status").asText(),
        Instant.ofEpochMilli(node.path("transactTime").asLong()));
  }

  private JsonNode readTree(String response) {
    try {
      return objectMapper.readTree(response);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Unable to parse response from Binance", e);
    }
  }

  private <T> T execute(Endpoint endpoint, String symbol, Supplier<T> supplier) {
    Supplier<T> decorated = chaosSuite.decorateApiCall(supplier);
    Supplier<T> instrumented =
        () -> {
          long started = System.nanoTime();
          try {
            T result = decorated.get();
            recordSuccess(endpoint, System.nanoTime() - started);
            return result;
          } catch (RuntimeException ex) {
            recordFailure(endpoint, System.nanoTime() - started, ex);
            throw ex;
          }
        };
    try {
      return throttle.submit(endpoint, symbol, instrumented).toCompletableFuture().join();
    } catch (CompletionException | CancellationException ex) {
      throw propagate(ex);
    }
  }

  private void recordSuccess(Endpoint endpoint, long nanos) {
    counterFor(endpoint, "success").increment();
    timerFor(endpoint).record(nanos, TimeUnit.NANOSECONDS);
  }

  private void recordFailure(Endpoint endpoint, long nanos, RuntimeException ex) {
    String status = classify(ex);
    counterFor(endpoint, status).increment();
    timerFor(endpoint).record(nanos, TimeUnit.NANOSECONDS);
  }

  private String classify(RuntimeException ex) {
    Throwable cause = ex;
    if (ex.getCause() instanceof RuntimeException runtime) {
      cause = runtime;
    }
    if (cause instanceof BinanceClientException client) {
      int status = client.getHttpStatusCode();
      if (status == 429 || status == 418) {
        return "rate_limited";
      }
      if (status >= 500) {
        return "server_error";
      }
      return "error";
    }
    if (cause instanceof BinanceServerException server && server.getHttpStatusCode() >= 500) {
      return "server_error";
    }
    return "error";
  }

  private Counter counterFor(Endpoint endpoint, String status) {
    String key = endpoint.name() + "|" + status;
    return apiCounters.computeIfAbsent(
        key,
        ignored ->
            meterRegistry.counter(
                "binance.api.requests", Tags.of("endpoint", endpoint.name().toLowerCase(), "status", status)));
  }

  private Timer timerFor(Endpoint endpoint) {
    return latencyTimers.get(endpoint);
  }

  private RuntimeException propagate(Throwable throwable) {
    Throwable cause = throwable;
    if (throwable instanceof CompletionException completion && completion.getCause() != null) {
      cause = completion.getCause();
    } else if (throwable instanceof CancellationException cancellation && cancellation.getCause() != null) {
      cause = cancellation.getCause();
    }
    if (cause instanceof RuntimeException runtime) {
      return runtime;
    }
    return new IllegalStateException("Binance call failed", cause);
  }
}
