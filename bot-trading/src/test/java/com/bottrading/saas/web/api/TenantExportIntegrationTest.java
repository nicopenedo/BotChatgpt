package com.bottrading.saas.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bottrading.model.entity.OrderEntity;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.entity.TradeEntity;
import com.bottrading.model.entity.TradeFillEntity;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import com.bottrading.model.enums.PositionStatus;
import com.bottrading.repository.OrderRepository;
import com.bottrading.repository.PositionRepository;
import com.bottrading.repository.TradeFillRepository;
import com.bottrading.repository.TradeRepository;
import com.bottrading.saas.model.entity.TenantEntity;
import com.bottrading.saas.model.entity.TenantPlan;
import com.bottrading.saas.model.entity.TenantUserEntity;
import com.bottrading.saas.model.entity.TenantUserRole;
import com.bottrading.saas.security.TenantUserDetails;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantExportIntegrationTest {

  private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private TradeRepository tradeRepository;
  @Autowired private TradeFillRepository tradeFillRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private PositionRepository positionRepository;

  private final List<Long> tenantATradeIds = new ArrayList<>();
  private final List<Long> tenantBTradeIds = new ArrayList<>();
  private final List<Long> tenantAFillIds = new ArrayList<>();
  private final List<Long> tenantBFillIds = new ArrayList<>();
  private final List<String> tenantAOrderIds = new ArrayList<>();
  private final List<String> tenantBOrderIds = new ArrayList<>();

  @BeforeEach
  void setup() {
    orderRepository.deleteAll();
    tradeFillRepository.deleteAll();
    tradeRepository.deleteAll();
    positionRepository.deleteAll();
    tenantATradeIds.clear();
    tenantBTradeIds.clear();
    tenantAFillIds.clear();
    tenantBFillIds.clear();
    tenantAOrderIds.clear();
    tenantBOrderIds.clear();

    seedTrades();
    seedFills();
    seedOrders();
  }

  @Test
  void tradesCsvIsTenantScoped() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/tenant/{tenantId}/exports/trades.csv", TENANT_A)
                    .secure(true)
                    .with(user(tenantUser(TENANT_A))))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=trades.csv"))
            .andReturn();

    String csv = result.getResponse().getContentAsString();
    tenantATradeIds.forEach(id -> assertThat(csv).contains(id.toString()));
    tenantBTradeIds.forEach(id -> assertThat(csv).doesNotContain(id.toString()));
  }

  @Test
  void fillsCsvIsTenantScoped() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/tenant/{tenantId}/exports/fills.csv", TENANT_B)
                    .secure(true)
                    .with(user(tenantUser(TENANT_B))))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=fills.csv"))
            .andReturn();

    String csv = result.getResponse().getContentAsString();
    tenantBFillIds.forEach(id -> assertThat(csv).contains(id.toString()));
    tenantAFillIds.forEach(id -> assertThat(csv).doesNotContain(id.toString()));
  }

  @Test
  void executionsJsonIsTenantScoped() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/tenant/{tenantId}/exports/executions.json", TENANT_A)
                    .secure(true)
                    .with(user(tenantUser(TENANT_A))))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=executions.json"))
            .andReturn();

    String json = result.getResponse().getContentAsString();
    tenantAOrderIds.forEach(id -> assertThat(json).contains(id));
    tenantBOrderIds.forEach(id -> assertThat(json).doesNotContain(id));
  }

  @Test
  void requestingAnotherTenantIsForbidden() throws Exception {
    mockMvc
        .perform(
            get("/tenant/{tenantId}/exports/trades.csv", TENANT_B)
                .secure(true)
                .with(user(tenantUser(TENANT_A))))
        .andExpect(status().isForbidden());
  }

  @Test
  void rangeFilterRestrictsTrades() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/tenant/{tenantId}/exports/trades.csv", TENANT_A)
                    .param("from", BASE.plus(1, ChronoUnit.DAYS).toString())
                    .param("to", BASE.plus(2, ChronoUnit.DAYS).toString())
                    .secure(true)
                    .with(user(tenantUser(TENANT_A))))
            .andExpect(status().isOk())
            .andReturn();

    String csv = result.getResponse().getContentAsString();
    assertThat(csv).contains(tenantATradeIds.get(1).toString());
    assertThat(csv).doesNotContain(tenantATradeIds.get(0).toString());
    assertThat(csv).doesNotContain(tenantATradeIds.get(2).toString());
  }

  private void seedTrades() {
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

    tenantATradeIds.add(createTrade(savedA, TENANT_A, BASE, BigDecimal.valueOf(100)));
    tenantATradeIds.add(
        createTrade(savedA, TENANT_A, BASE.plus(1, ChronoUnit.DAYS), BigDecimal.valueOf(110)));
    tenantATradeIds.add(
        createTrade(savedA, TENANT_A, BASE.plus(3, ChronoUnit.DAYS), BigDecimal.valueOf(120)));

    tenantBTradeIds.add(createTrade(savedB, TENANT_B, BASE, BigDecimal.valueOf(200)));
    tenantBTradeIds.add(
        createTrade(savedB, TENANT_B, BASE.plus(2, ChronoUnit.DAYS), BigDecimal.valueOf(210)));
  }

  private Long createTrade(PositionEntity position, UUID tenantId, Instant executedAt, BigDecimal price) {
    TradeEntity trade = new TradeEntity();
    trade.setPosition(position);
    trade.setTenantId(tenantId);
    trade.setPrice(price);
    trade.setQuantity(BigDecimal.ONE);
    trade.setFee(BigDecimal.ZERO);
    trade.setSide(position.getSide());
    trade.setExecutedAt(executedAt);
    return tradeRepository.save(trade).getId();
  }

  private void seedFills() {
    tenantAFillIds.add(createFill(TENANT_A, "A-1", BASE, OrderSide.BUY));
    tenantAFillIds.add(createFill(TENANT_A, "A-2", BASE.plus(1, ChronoUnit.HOURS), OrderSide.BUY));
    tenantAFillIds.add(createFill(TENANT_A, "A-3", BASE.plus(2, ChronoUnit.HOURS), OrderSide.SELL));

    tenantBFillIds.add(createFill(TENANT_B, "B-1", BASE, OrderSide.BUY));
    tenantBFillIds.add(createFill(TENANT_B, "B-2", BASE.plus(3, ChronoUnit.HOURS), OrderSide.SELL));
  }

  private Long createFill(UUID tenantId, String orderId, Instant executedAt, OrderSide side) {
    TradeFillEntity entity = new TradeFillEntity();
    entity.setOrderId(orderId);
    entity.setClientOrderId(orderId + "-client");
    entity.setSymbol("BTCUSDT");
    entity.setOrderType(OrderType.MARKET);
    entity.setOrderSide(side);
    entity.setRefPrice(BigDecimal.valueOf(100));
    entity.setFillPrice(BigDecimal.valueOf(101));
    entity.setSlippageBps(5.0);
    entity.setQueueTimeMs(50L);
    entity.setExecutedAt(executedAt);
    entity.setTenantId(tenantId);
    return tradeFillRepository.save(entity).getId();
  }

  private void seedOrders() {
    tenantAOrderIds.add(createOrder(TENANT_A, "ORD-A1", BASE));
    tenantAOrderIds.add(createOrder(TENANT_A, "ORD-A2", BASE.plus(1, ChronoUnit.DAYS)));
    tenantAOrderIds.add(createOrder(TENANT_A, "ORD-A3", BASE.plus(2, ChronoUnit.DAYS)));

    tenantBOrderIds.add(createOrder(TENANT_B, "ORD-B1", BASE));
    tenantBOrderIds.add(createOrder(TENANT_B, "ORD-B2", BASE.plus(1, ChronoUnit.DAYS)));
  }

  private String createOrder(UUID tenantId, String orderId, Instant transactTime) {
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
    orderRepository.save(entity);
    return orderId;
  }

  private TenantUserDetails tenantUser(UUID tenantId) {
    TenantEntity tenant = new TenantEntity();
    tenant.setId(tenantId);
    tenant.setName("Tenant " + tenantId);
    tenant.setEmailOwner("owner@" + tenantId + ".example");
    tenant.setPlan(TenantPlan.STARTER);
    tenant.setCreatedAt(Instant.now());
    tenant.setUpdatedAt(Instant.now());

    TenantUserEntity user = new TenantUserEntity();
    user.setId(UUID.randomUUID());
    user.setTenant(tenant);
    user.setEmail("user@" + tenantId + ".example");
    user.setPasswordHash("password");
    user.setRole(TenantUserRole.ADMIN);
    user.setMfaEnabled(false);
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    return new TenantUserDetails(user);
  }
}
