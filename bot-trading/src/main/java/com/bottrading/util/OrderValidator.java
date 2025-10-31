package com.bottrading.util;

import com.bottrading.model.dto.ExchangeInfo;
import com.bottrading.model.dto.OrderRequest;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class OrderValidator {

  private OrderValidator() {}

  public static void validate(OrderRequest request, ExchangeInfo exchangeInfo, BigDecimal lastPrice) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(exchangeInfo, "exchangeInfo");

    BigDecimal tickSize = exchangeInfo.tickSize();
    BigDecimal stepSize = exchangeInfo.stepSize();
    BigDecimal minNotional = exchangeInfo.minNotional();

    if (request.getType() == OrderType.LIMIT) {
      BigDecimal normalizedPrice = MathUtil.floorToIncrement(request.getPrice(), tickSize);
      request.setPrice(normalizedPrice);
      BigDecimal normalizedQty = MathUtil.floorToIncrement(request.getQuantity(), stepSize);
      request.setQuantity(normalizedQty);
      ensureNotional(normalizedPrice.multiply(normalizedQty), minNotional);
    } else {
      if (request.getQuoteAmount() != null && request.getSide() == OrderSide.BUY) {
        BigDecimal normalizedQuote = request.getQuoteAmount().setScale(8, RoundingMode.DOWN);
        request.setQuoteAmount(normalizedQuote);
      } else {
        BigDecimal qty = request.getQuantity();
        if (qty == null && request.getQuoteAmount() != null) {
          qty = request.getQuoteAmount().divide(lastPrice, 8, RoundingMode.DOWN);
          request.setQuantity(MathUtil.floorToIncrement(qty, stepSize));
        } else if (qty != null) {
          request.setQuantity(MathUtil.floorToIncrement(qty, stepSize));
        }
        BigDecimal estimatedPrice = request.getPrice() != null ? request.getPrice() : lastPrice;
        ensureNotional(estimatedPrice.multiply(request.getQuantity()), minNotional);
      }
    }
  }

  private static void ensureNotional(BigDecimal notional, BigDecimal minNotional) {
    if (notional.compareTo(minNotional) < 0) {
      throw new IllegalArgumentException(
          "Order notional %s below exchange minimum %s".formatted(notional, minNotional));
    }
  }
}
