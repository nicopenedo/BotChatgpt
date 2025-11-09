package com.bottrading.service.binance;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bottrading.chaos.ChaosSuite;
import com.bottrading.config.BinanceProperties;
import com.bottrading.config.CacheConfig;
import com.bottrading.throttle.Endpoint;
import com.bottrading.throttle.Throttle;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

@ExtendWith(MockitoExtension.class)
class BinanceClientImplTest {

  @Mock private Throttle throttle;
  @Mock private ChaosSuite chaosSuite;

  private WireMockServer wireMockServer;
  private CacheManager cacheManager;

  @BeforeEach
  void startServer() {
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();
    cacheManager = new ConcurrentMapCacheManager(CacheConfig.EXCHANGE_INFO_CACHE, CacheConfig.COMMISSION_CACHE);
  }

  @AfterEach
  void stopServer() {
    wireMockServer.stop();
  }

  @Test
  void getPriceUsesWireMockedEndpoint() {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v3/ticker/price"))
            .withQueryParam("symbol", equalTo("BTCUSDT"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"symbol\":\"BTCUSDT\",\"price\":\"123.45\"}")));

    BinanceClientImpl client = newClient();

    var ticker = client.getPrice("BTCUSDT");

    assertThat(ticker.price()).isEqualByComparingTo("123.45");
  }

  @Test
  void tradingCommissionFallsBackToDefault() {
    BinanceClientImpl client = newClient();

    BigDecimal commission = client.getTradingCommission("BTCUSDT");
    BigDecimal cached = client.getTradingCommission("BTCUSDT");

    assertThat(commission).isEqualByComparingTo("0.0010");
    assertThat(cached).isEqualByComparingTo(commission);
  }

  private BinanceClientImpl newClient() {
    BinanceProperties properties = new BinanceProperties("key", "secret", wireMockServer.baseUrl(), wireMockServer.baseUrl());
    when(chaosSuite.decorateApiCall(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(throttle.submit(any(Endpoint.class), anyString(), any()))
        .thenAnswer(invocation -> {
          @SuppressWarnings("unchecked")
          var supplier = (java.util.function.Supplier<Object>) invocation.getArgument(2);
          return CompletableFuture.completedFuture(supplier.get());
        });
    return new BinanceClientImpl(properties, cacheManager, throttle, chaosSuite, new SimpleMeterRegistry());
  }
}
