package com.bottrading.util;

import com.bottrading.model.entity.OrderEntity;
import com.bottrading.model.entity.TradeEntity;
import com.bottrading.model.entity.TradeFillEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonUtils {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private JsonUtils() {}

  public static String orderToJson(OrderEntity order) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("orderId", stringValue(order.getOrderId()));
    payload.put("symbol", stringValue(order.getSymbol()));
    payload.put("side", order.getSide() != null ? order.getSide().name() : "");
    payload.put("price", stringValue(order.getPrice()));
    payload.put("quantity", stringValue(order.getQuantity()));
    payload.put("status", stringValue(order.getStatus()));
    payload.put("createdAt", stringValue(order.getTransactTime()));
    return write(payload);
  }

  public static String tradeToJson(TradeEntity trade) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", stringValue(trade.getId()));
    payload.put(
        "positionId",
        trade.getPosition() != null ? stringValue(trade.getPosition().getId()) : "");
    payload.put("orderId", stringValue(trade.getOrderId()));
    payload.put("price", stringValue(trade.getPrice()));
    payload.put("quantity", stringValue(trade.getQuantity()));
    payload.put("fee", stringValue(trade.getFee()));
    payload.put("side", trade.getSide() != null ? trade.getSide().name() : "");
    payload.put("executedAt", stringValue(trade.getExecutedAt()));
    return write(payload);
  }

  public static String tradeFillToJson(TradeFillEntity fill) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", stringValue(fill.getId()));
    payload.put("orderId", stringValue(fill.getOrderId()));
    payload.put("clientOrderId", stringValue(fill.getClientOrderId()));
    payload.put("symbol", stringValue(fill.getSymbol()));
    payload.put("orderType", fill.getOrderType() != null ? fill.getOrderType().name() : "");
    payload.put("orderSide", fill.getOrderSide() != null ? fill.getOrderSide().name() : "");
    payload.put("refPrice", stringValue(fill.getRefPrice()));
    payload.put("fillPrice", stringValue(fill.getFillPrice()));
    payload.put("slippageBps", stringValue(fill.getSlippageBps()));
    payload.put("queueTimeMs", stringValue(fill.getQueueTimeMs()));
    payload.put("executedAt", stringValue(fill.getExecutedAt()));
    return write(payload);
  }

  public static String toJson(Map<String, ?> payload) {
    return write(new LinkedHashMap<>(payload));
  }

  private static String write(Map<String, ?> payload) {
    try {
      return OBJECT_MAPPER.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Unable to serialize JSON", e);
    }
  }

  private static String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}

