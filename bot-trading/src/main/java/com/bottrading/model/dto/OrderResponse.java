package com.bottrading.model.dto;

import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
    String orderId,
    String clientOrderId,
    String symbol,
    OrderSide side,
    OrderType type,
    BigDecimal price,
    BigDecimal executedQty,
    BigDecimal cummulativeQuoteQty,
    String status,
    Instant transactTime) {}
