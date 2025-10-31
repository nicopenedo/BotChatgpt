package com.bottrading.util;

import com.bottrading.model.dto.ExchangeInfo;
import com.bottrading.model.dto.OrderRequest;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OrderValidatorTest {

  @Test
  void validateLimitOrderNormalizesPriceAndQuantity() {
    OrderRequest request = new OrderRequest();
    request.setSymbol("BTCUSDT");
    request.setSide(OrderSide.BUY);
    request.setType(OrderType.LIMIT);
    request.setPrice(new BigDecimal("27300.1234"));
    request.setQuantity(new BigDecimal("0.123456"));

    ExchangeInfo exchangeInfo =
        new ExchangeInfo(new BigDecimal("0.01"), new BigDecimal("0.0001"), new BigDecimal("10"));
    OrderValidator.validate(request, exchangeInfo, new BigDecimal("27300"));

    Assertions.assertEquals(new BigDecimal("27300.12"), request.getPrice());
    Assertions.assertEquals(new BigDecimal("0.1234"), request.getQuantity());
  }

  @Test
  void validateMarketOrderThrowsWhenBelowNotional() {
    OrderRequest request = new OrderRequest();
    request.setSymbol("BTCUSDT");
    request.setSide(OrderSide.BUY);
    request.setType(OrderType.MARKET);
    request.setQuantity(new BigDecimal("0.0001"));

    ExchangeInfo exchangeInfo =
        new ExchangeInfo(new BigDecimal("0.01"), new BigDecimal("0.0001"), new BigDecimal("50"));

    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> OrderValidator.validate(request, exchangeInfo, new BigDecimal("10000")));
  }
}
