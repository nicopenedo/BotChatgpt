package com.bottrading.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.model.entity.OrderEntity;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.entity.TradeEntity;
import com.bottrading.model.entity.TradeFillEntity;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonUtilsTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void orderToJsonProducesValidJsonWithExpectedOrder() throws Exception {
    OrderEntity order = new OrderEntity();
    order.setOrderId("order-1");
    order.setSymbol("BTCUSDT");
    order.setSide(OrderSide.BUY);
    order.setPrice(new BigDecimal("12345.67"));
    order.setQuantity(new BigDecimal("0.5"));
    order.setStatus("FILLED");
    order.setTransactTime(Instant.parse("2024-01-02T03:04:05Z"));

    String json = JsonUtils.orderToJson(order);

    JsonNode node = mapper.readTree(json);
    assertThat(node.get("orderId").asText()).isEqualTo("order-1");
    assertThat(node.get("symbol").asText()).isEqualTo("BTCUSDT");
    assertThat(node.get("side").asText()).isEqualTo("BUY");
    assertThat(node.get("price").asText()).isEqualTo("12345.67");
    assertThat(node.get("quantity").asText()).isEqualTo("0.5");
    assertThat(node.get("status").asText()).isEqualTo("FILLED");
    assertThat(node.get("createdAt").asText()).isEqualTo("2024-01-02T03:04:05Z");

    assertThat(fieldNames(node))
        .containsExactly("orderId", "symbol", "side", "price", "quantity", "status", "createdAt");
  }

  @Test
  void orderToJsonEscapesSpecialCharacters() throws Exception {
    OrderEntity order = new OrderEntity();
    order.setOrderId("id\"123");
    order.setSymbol("BTC\\USDT\n");

    String json = JsonUtils.orderToJson(order);

    JsonNode node = mapper.readTree(json);
    assertThat(node.get("orderId").asText()).isEqualTo("id\"123");
    assertThat(node.get("symbol").asText()).isEqualTo("BTC\\USDT\n");
    assertThat(fieldNames(node))
        .containsExactly("orderId", "symbol", "side", "price", "quantity", "status", "createdAt");
  }

  @Test
  void orderToJsonConvertsNullsToEmptyStrings() throws Exception {
    OrderEntity order = new OrderEntity();

    String json = JsonUtils.orderToJson(order);

    JsonNode node = mapper.readTree(json);
    assertThat(node.get("orderId").asText()).isEmpty();
    assertThat(node.get("symbol").asText()).isEmpty();
    assertThat(node.get("side").asText()).isEmpty();
    assertThat(node.get("price").asText()).isEmpty();
    assertThat(node.get("quantity").asText()).isEmpty();
    assertThat(node.get("status").asText()).isEmpty();
    assertThat(node.get("createdAt").asText()).isEmpty();
  }

  @Test
  void tradeToJsonSerializesAllFields() throws Exception {
    PositionEntity position = new PositionEntity();
    position.setId(42L);

    TradeEntity trade = new TradeEntity();
    trade.setId(100L);
    trade.setPosition(position);
    trade.setOrderId("order-1");
    trade.setPrice(new BigDecimal("123.45"));
    trade.setQuantity(new BigDecimal("0.1"));
    trade.setFee(new BigDecimal("0.005"));
    trade.setSide(OrderSide.SELL);
    trade.setExecutedAt(Instant.parse("2024-02-02T00:00:00Z"));

    String json = JsonUtils.tradeToJson(trade);

    JsonNode node = mapper.readTree(json);
    assertThat(fieldNames(node))
        .containsExactly(
            "id",
            "positionId",
            "orderId",
            "price",
            "quantity",
            "fee",
            "side",
            "executedAt");
    assertThat(node.get("positionId").asText()).isEqualTo("42");
    assertThat(node.get("executedAt").asText()).isEqualTo("2024-02-02T00:00:00Z");
  }

  @Test
  void tradeFillToJsonSerializesAllFields() throws Exception {
    TradeFillEntity fill = new TradeFillEntity();
    fill.setId(10L);
    fill.setOrderId("order-1");
    fill.setClientOrderId("client-1");
    fill.setSymbol("ETHUSDT");
    fill.setOrderType(OrderType.LIMIT);
    fill.setOrderSide(OrderSide.BUY);
    fill.setRefPrice(new BigDecimal("100.00"));
    fill.setFillPrice(new BigDecimal("100.50"));
    fill.setSlippageBps(0.5);
    fill.setQueueTimeMs(500L);
    fill.setExecutedAt(Instant.parse("2024-03-02T00:00:00Z"));

    String json = JsonUtils.tradeFillToJson(fill);

    JsonNode node = mapper.readTree(json);
    assertThat(fieldNames(node))
        .containsExactly(
            "id",
            "orderId",
            "clientOrderId",
            "symbol",
            "orderType",
            "orderSide",
            "refPrice",
            "fillPrice",
            "slippageBps",
            "queueTimeMs",
            "executedAt");
    assertThat(node.get("orderSide").asText()).isEqualTo("BUY");
    assertThat(node.get("slippageBps").asText()).isEqualTo("0.5");
  }

  @Test
  void toJsonSerializesMapUsingJackson() throws Exception {
    String json = JsonUtils.toJson(Map.of("error", "geo_blocked"));

    JsonNode node = mapper.readTree(json);
    assertThat(fieldNames(node)).containsExactly("error");
    assertThat(node.get("error").asText()).isEqualTo("geo_blocked");
  }

  private List<String> fieldNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    Iterator<String> iterator = node.fieldNames();
    while (iterator.hasNext()) {
      names.add(iterator.next());
    }
    return names;
  }
}

