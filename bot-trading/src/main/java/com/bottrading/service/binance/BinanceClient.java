package com.bottrading.service.binance;

import com.bottrading.model.dto.AccountBalancesResponse;
import com.bottrading.model.dto.ExchangeInfo;
import com.bottrading.model.dto.Kline;
import com.bottrading.model.dto.OrderRequest;
import com.bottrading.model.dto.OrderResponse;
import com.bottrading.model.dto.PriceTicker;
import com.bottrading.model.entity.ManagedOrderEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

public interface BinanceClient {
  PriceTicker getPrice(String symbol);

  List<Kline> getKlines(String symbol, String interval, int limit);

  BigDecimal get24hQuoteVolume(String symbol);

  ExchangeInfo getExchangeInfo(String symbol);

  AccountBalancesResponse getAccountBalances(List<String> assets);

  BigDecimal getTradingCommission(String symbol);

  OrderResponse placeOrder(OrderRequest request);

  OrderResponse getOrder(String symbol, String orderId);

  boolean placeOcoOrder(String symbol, ManagedOrderEntity stopLoss, ManagedOrderEntity takeProfit);

  void placeChildOrder(ManagedOrderEntity order);

  void cancelOrder(ManagedOrderEntity order);

  List<ExchangeOrder> getOpenOrders(String symbol);

  List<ExchangeOrder> getRecentOrders(String symbol, int lookbackMinutes);

  String startUserDataStream();

  void keepAliveUserDataStream(String listenKey);

  void closeUserDataStream(String listenKey);

  void connectUserDataStream(String listenKey, Consumer<String> onMessage, Consumer<Throwable> onError);

  record ExchangeOrder(
      String symbol,
      String clientOrderId,
      String exchangeOrderId,
      String status,
      BigDecimal executedQty,
      BigDecimal price,
      long updateTime) {}
}
