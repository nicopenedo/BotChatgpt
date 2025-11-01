package com.bottrading.service.binance;

import com.binance.connector.client.impl.SpotClientImpl;
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
import com.bottrading.util.IdGenerator;
import com.bottrading.util.OrderValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.List;
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
  private final Retry retry;
  private final RateLimiter rateLimiter;

  public BinanceClientImpl(
      BinanceProperties properties,
      CacheManager cacheManager,
      RetryConfig retryConfig,
      RateLimiterConfig rateLimiterConfig) {
    this.spotClient =
        new SpotClientImpl(properties.apiKey(), properties.apiSecret(), properties.baseUrl());
    this.cacheManager = cacheManager;
    RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
    this.retry = retryRegistry.retry("binance");
    this.rateLimiter = RateLimiter.of("binance", rateLimiterConfig);
  }

  @Override
  public PriceTicker getPrice(String symbol) {
    String response = execute(() -> spotClient.createMarket().tickerPrice(Map.of("symbol", symbol)));
    JsonNode node = readTree(response);
    return new PriceTicker(symbol, new BigDecimal(node.get("price").asText()));
  }

  @Override
  public List<Kline> getKlines(String symbol, String interval, int limit) {
    Map<String, Object> params = new HashMap<>();
    params.put("symbol", symbol);
    params.put("interval", interval);
    params.put("limit", limit);
    String response = execute(() -> spotClient.createMarket().klines(params));
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
        execute(() -> spotClient.createMarket().ticker24H(Map.of("symbol", symbol)));
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
    String response = execute(() -> spotClient.createMarket().exchangeInfo(Map.of("symbol", symbol)));
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
    String response = execute(() -> spotClient.createTrade().account(new HashMap<>()));
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
    String response = execute(() -> spotClient.createTrade().commissionRate(params));
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
      params.put("timeInForce", "GTC");
    } else {
      if (request.getSide() == OrderSide.BUY && request.getQuoteAmount() != null) {
        params.put("quoteOrderQty", request.getQuoteAmount().toPlainString());
      } else {
        params.put("quantity", request.getQuantity().toPlainString());
      }
    }

    String response = execute(() -> spotClient.createTrade().newOrder(params));
    return mapOrderResponse(response);
  }

  @Override
  public OrderResponse getOrder(String symbol, String orderId) {
    Map<String, Object> params = new HashMap<>();
    params.put("symbol", symbol);
    params.put("orderId", orderId);
    String response = execute(() -> spotClient.createTrade().getOrder(params));
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

  private <T> T execute(Supplier<T> supplier) {
    Supplier<T> decorated = Retry.decorateSupplier(retry, supplier);
    decorated = RateLimiter.decorateSupplier(rateLimiter, decorated);
    return decorated.get();
  }
}
