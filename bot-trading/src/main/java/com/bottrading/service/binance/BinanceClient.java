package com.bottrading.service.binance;

import com.bottrading.model.dto.AccountBalancesResponse;
import com.bottrading.model.dto.ExchangeInfo;
import com.bottrading.model.dto.Kline;
import com.bottrading.model.dto.OrderRequest;
import com.bottrading.model.dto.OrderResponse;
import com.bottrading.model.dto.PriceTicker;
import java.math.BigDecimal;
import java.util.List;

public interface BinanceClient {
  PriceTicker getPrice(String symbol);

  List<Kline> getKlines(String symbol, String interval, int limit);

  BigDecimal get24hQuoteVolume(String symbol);

  ExchangeInfo getExchangeInfo(String symbol);

  AccountBalancesResponse getAccountBalances(List<String> assets);

  BigDecimal getTradingCommission(String symbol);

  OrderResponse placeOrder(OrderRequest request);

  OrderResponse getOrder(String symbol, String orderId);
}
