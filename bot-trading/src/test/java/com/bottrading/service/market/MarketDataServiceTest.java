package com.bottrading.service.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.model.dto.Kline;
import com.bottrading.service.binance.BinanceClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketDataServiceTest {

  private MarketDataService service;
  private StubBinanceClient client;

  @BeforeEach
  void setUp() {
    client = new StubBinanceClient();
    client.klines.addAll(
        List.of(
            kline("2024-01-01T00:00:00Z", 100, 105, 95, 102, 10),
            kline("2024-01-01T00:01:00Z", 102, 107, 100, 106, 12),
            kline("2024-01-01T00:02:00Z", 106, 110, 104, 108, 15),
            kline("2024-01-02T00:00:00Z", 108, 111, 107, 110, 20)));
    service = new MarketDataService(client);
  }

  @Test
  void vwapDailyResetsByDefault() {
    List<?> points = service.vwap("BTCUSDT", "1m", null, null, null);
    assertThat(points).hasSize(4);
  }

  @Test
  void anchoredVwapStartsFromAnchor() {
    List<?> points =
        service.vwap(
            "BTCUSDT", "1m", null, null, Instant.parse("2024-01-01T00:02:00Z"));
    assertThat(points).hasSize(2);
  }

  @Test
  void atrBandsProducesUpperAndLower() {
    var bands =
        service.atrBands(
            "BTCUSDT",
            "1m",
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-01-01T00:03:00Z"),
            2,
            BigDecimal.ONE);
    assertThat(bands).isNotEmpty();
    assertThat(bands.get(0).upper()).isNotNull();
    assertThat(bands.get(0).lower()).isNotNull();
  }

  private static Kline kline(String close, double open, double high, double low, double closePrice, double volume) {
    Instant openTime = Instant.parse(close).minusSeconds(60);
    return new Kline(
        openTime,
        Instant.parse(close),
        BigDecimal.valueOf(open),
        BigDecimal.valueOf(high),
        BigDecimal.valueOf(low),
        BigDecimal.valueOf(closePrice),
        BigDecimal.valueOf(volume));
  }

  private static class StubBinanceClient implements BinanceClient {
    private final List<Kline> klines = new ArrayList<>();

    @Override
    public List<Kline> getKlines(String symbol, String interval, int limit) {
      return klines.subList(0, Math.min(limit, klines.size()));
    }

    @Override
    public com.bottrading.model.dto.PriceTicker getPrice(String symbol) {
      throw new UnsupportedOperationException();
    }

    @Override
    public java.math.BigDecimal get24hQuoteVolume(String symbol) {
      throw new UnsupportedOperationException();
    }

    @Override
    public com.bottrading.model.dto.ExchangeInfo getExchangeInfo(String symbol) {
      throw new UnsupportedOperationException();
    }

    @Override
    public com.bottrading.model.dto.AccountBalancesResponse getAccountBalances(List<String> assets) {
      throw new UnsupportedOperationException();
    }

    @Override
    public java.math.BigDecimal getTradingCommission(String symbol) {
      throw new UnsupportedOperationException();
    }

    @Override
    public com.bottrading.model.dto.OrderResponse placeOrder(com.bottrading.model.dto.OrderRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public com.bottrading.model.dto.OrderResponse getOrder(String symbol, String orderId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean placeOcoOrder(String symbol, com.bottrading.model.entity.ManagedOrderEntity stopLoss, com.bottrading.model.entity.ManagedOrderEntity takeProfit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void placeChildOrder(com.bottrading.model.entity.ManagedOrderEntity order) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void cancelOrder(com.bottrading.model.entity.ManagedOrderEntity order) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<ExchangeOrder> getOpenOrders(String symbol) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<ExchangeOrder> getRecentOrders(String symbol, int lookbackMinutes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String startUserDataStream() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void keepAliveUserDataStream(String listenKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void closeUserDataStream(String listenKey) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void connectUserDataStream(String listenKey, java.util.function.Consumer<String> onMessage, java.util.function.Consumer<Throwable> onError) {
      throw new UnsupportedOperationException();
    }

  }
}
