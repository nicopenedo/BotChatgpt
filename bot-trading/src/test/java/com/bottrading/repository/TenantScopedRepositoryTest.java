package com.bottrading.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.model.entity.OrderEntity;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.entity.TradeEntity;
import com.bottrading.model.entity.TradeFillEntity;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import com.bottrading.model.enums.PositionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class TenantScopedRepositoryTest {

  private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private static final Instant BASE = Instant.parse("2024-02-01T00:00:00Z");

  @Autowired private TradeRepository tradeRepository;
  @Autowired private TradeFillRepository tradeFillRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private PositionRepository positionRepository;

  @BeforeEach
  void clean() {
    orderRepository.deleteAll();
    tradeFillRepository.deleteAll();
    tradeRepository.deleteAll();
    positionRepository.deleteAll();

    seedData();
  }

  @Test
  void tradeStreamFiltersByTenant() {
    try (var stream = tradeRepository.streamByTenantAndRange(TENANT_A, null, null)) {
      List<TradeEntity> trades = stream.toList();
      assertThat(trades).hasSize(3);
      assertThat(trades).allMatch(trade -> trade.getTenantId().equals(TENANT_A));
    }
  }

  @Test
  void tradeStreamHonorsRange() {
    try (var stream =
        tradeRepository.streamByTenantAndRange(
            TENANT_A, BASE.plus(1, ChronoUnit.DAYS), BASE.plus(3, ChronoUnit.DAYS))) {
      List<TradeEntity> trades = stream.toList();
      assertThat(trades).hasSize(1);
      assertThat(trades.get(0).getExecutedAt()).isEqualTo(BASE.plus(2, ChronoUnit.DAYS));
    }
  }

  @Test
  void fillStreamFiltersByTenant() {
    try (var stream = tradeFillRepository.streamByTenantAndRange(TENANT_B, null, null)) {
      List<TradeFillEntity> fills = stream.toList();
      assertThat(fills).hasSize(2);
      assertThat(fills).allMatch(fill -> fill.getTenantId().equals(TENANT_B));
    }
  }

  @Test
  void orderStreamFiltersByTenant() {
    try (var stream = orderRepository.streamByTenantAndRange(TENANT_A, null, null)) {
      List<OrderEntity> orders = stream.toList();
      assertThat(orders).hasSize(3);
      assertThat(orders).allMatch(order -> order.getTenantId().equals(TENANT_A));
    }
  }

  private void seedData() {
    PositionEntity posA = new PositionEntity();
    posA.setSymbol("BTCUSDT");
    posA.setSide(OrderSide.BUY);
    posA.setStatus(PositionStatus.OPEN);
    posA.setPresetId(UUID.randomUUID());
    PositionEntity savedA = positionRepository.save(posA);

    PositionEntity posB = new PositionEntity();
    posB.setSymbol("ETHUSDT");
    posB.setSide(OrderSide.SELL);
    posB.setStatus(PositionStatus.OPEN);
    posB.setPresetId(UUID.randomUUID());
    PositionEntity savedB = positionRepository.save(posB);

    tradeRepository.save(createTrade(savedA, TENANT_A, BASE));
    tradeRepository.save(createTrade(savedA, TENANT_A, BASE.plus(2, ChronoUnit.DAYS)));
    tradeRepository.save(createTrade(savedA, TENANT_A, BASE.plus(4, ChronoUnit.DAYS)));
    tradeRepository.save(createTrade(savedB, TENANT_B, BASE.plus(1, ChronoUnit.DAYS)));

    tradeFillRepository.save(createFill(TENANT_A, "A-1", BASE));
    tradeFillRepository.save(createFill(TENANT_B, "B-1", BASE.plus(1, ChronoUnit.HOURS)));
    tradeFillRepository.save(createFill(TENANT_B, "B-2", BASE.plus(2, ChronoUnit.HOURS)));

    orderRepository.save(createOrder(TENANT_A, "ORD-A1", BASE));
    orderRepository.save(createOrder(TENANT_A, "ORD-A2", BASE.plus(1, ChronoUnit.DAYS)));
    orderRepository.save(createOrder(TENANT_A, "ORD-A3", BASE.plus(2, ChronoUnit.DAYS)));
    orderRepository.save(createOrder(TENANT_B, "ORD-B1", BASE.plus(3, ChronoUnit.DAYS)));
  }

  private TradeEntity createTrade(PositionEntity position, UUID tenantId, Instant executedAt) {
    TradeEntity trade = new TradeEntity();
    trade.setPosition(position);
    trade.setTenantId(tenantId);
    trade.setPrice(BigDecimal.valueOf(100));
    trade.setQuantity(BigDecimal.ONE);
    trade.setFee(BigDecimal.ZERO);
    trade.setSide(position.getSide());
    trade.setExecutedAt(executedAt);
    return trade;
  }

  private TradeFillEntity createFill(UUID tenantId, String orderId, Instant executedAt) {
    TradeFillEntity entity = new TradeFillEntity();
    entity.setOrderId(orderId);
    entity.setClientOrderId(orderId + "-client");
    entity.setSymbol("BTCUSDT");
    entity.setOrderType(OrderType.MARKET);
    entity.setOrderSide(OrderSide.BUY);
    entity.setRefPrice(BigDecimal.valueOf(100));
    entity.setFillPrice(BigDecimal.valueOf(101));
    entity.setSlippageBps(2.0);
    entity.setQueueTimeMs(10L);
    entity.setExecutedAt(executedAt);
    entity.setTenantId(tenantId);
    return entity;
  }

  private OrderEntity createOrder(UUID tenantId, String orderId, Instant transactTime) {
    OrderEntity entity = new OrderEntity();
    entity.setOrderId(orderId);
    entity.setClientOrderId(orderId + "-client");
    entity.setSymbol("BTCUSDT");
    entity.setSide(OrderSide.BUY);
    entity.setType(OrderType.MARKET);
    entity.setPrice(BigDecimal.valueOf(99));
    entity.setQuantity(BigDecimal.ONE);
    entity.setExecutedQty(BigDecimal.ONE);
    entity.setQuoteQty(BigDecimal.valueOf(99));
    entity.setStatus("FILLED");
    entity.setTransactTime(transactTime);
    entity.setTenantId(tenantId);
    return entity;
  }
}
