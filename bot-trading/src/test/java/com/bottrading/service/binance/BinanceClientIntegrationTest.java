package com.bottrading.service.binance;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.config.BinanceProperties;
import com.bottrading.config.CacheConfig;
import com.bottrading.model.dto.OrderRequest;
import com.bottrading.model.dto.OrderResponse;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.RetryConfig;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

class BinanceClientIntegrationTest {

  private WireMockServer wireMockServer;
  private BinanceClientImpl binanceClient;

  @BeforeEach
  void setup() {
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());

    BinanceProperties properties =
        new BinanceProperties("key", "secret", wireMockServer.baseUrl());
    CacheManager cacheManager = new CaffeineCacheManager(CacheConfig.EXCHANGE_INFO_CACHE, CacheConfig.COMMISSION_CACHE);
    ((CaffeineCacheManager) cacheManager).setCaffeine(Caffeine.newBuilder());
    RetryConfig retryConfig = RetryConfig.custom().maxAttempts(1).waitDuration(Duration.ofMillis(10)).build();
    RateLimiterConfig rateLimiterConfig =
        RateLimiterConfig.custom().limitRefreshPeriod(Duration.ofSeconds(1)).limitForPeriod(10).timeoutDuration(Duration.ofSeconds(1)).build();
    binanceClient = new BinanceClientImpl(properties, cacheManager, retryConfig, rateLimiterConfig);
  }

  @AfterEach
  void tearDown() {
    wireMockServer.stop();
  }

  @Test
  void placeLimitOrderParsesResponse() {
    stubExchangeEndpoints();

    OrderRequest request = new OrderRequest();
    request.setSymbol("BTCUSDT");
    request.setSide(OrderSide.BUY);
    request.setType(OrderType.LIMIT);
    request.setPrice(new BigDecimal("27300.12"));
    request.setQuantity(new BigDecimal("0.01"));

    OrderResponse response = binanceClient.placeOrder(request);
    assertThat(response.orderId()).isEqualTo("12345");
    assertThat(response.symbol()).isEqualTo("BTCUSDT");
  }

  private void stubExchangeEndpoints() {
    wireMockServer.stubFor(
        WireMock.get(WireMock.urlPathEqualTo("/api/v3/exchangeInfo"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"symbols\":[{\"symbol\":\"BTCUSDT\",\"filters\":[{\"filterType\":\"PRICE_FILTER\",\"tickSize\":\"0.01\"},{\"filterType\":\"LOT_SIZE\",\"stepSize\":\"0.0001\"},{\"filterType\":\"MIN_NOTIONAL\",\"minNotional\":\"10\"}]}]}")));
    wireMockServer.stubFor(
        WireMock.get(WireMock.urlPathEqualTo("/api/v3/ticker/price"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"symbol\":\"BTCUSDT\",\"price\":\"27300.12\"}")));
    wireMockServer.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/api/v3/order"))
            .willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"orderId\":\"12345\",\"clientOrderId\":\"abc\",\"symbol\":\"BTCUSDT\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":\"27300.12\",\"executedQty\":\"0.00\",\"cummulativeQuoteQty\":\"0\",\"status\":\"NEW\",\"transactTime\":1696118400000}")));
  }
}
