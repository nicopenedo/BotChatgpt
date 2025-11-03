package com.bottrading.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bottrading.config.OcoProperties;
import com.bottrading.config.PositionManagerProperties;
import com.bottrading.execution.PositionManager.ManagedOrderUpdate;
import com.bottrading.execution.PositionManager.OpenPositionCommand;
import com.bottrading.model.entity.ManagedOrderEntity;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.entity.TradeEntity;
import com.bottrading.model.enums.ManagedOrderStatus;
import com.bottrading.model.enums.ManagedOrderType;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PositionStatus;
import com.bottrading.notify.TelegramNotifier;
import com.bottrading.repository.ManagedOrderRepository;
import com.bottrading.repository.PositionRepository;
import com.bottrading.repository.TradeRepository;
import com.bottrading.saas.security.TenantAccessGuard;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.report.PnlAttributionService;
import com.bottrading.service.risk.drift.DriftWatchdog;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PositionManagerTests {

  private final PositionRepository positionRepository = mock(PositionRepository.class);
  private final ManagedOrderRepository managedOrderRepository = mock(ManagedOrderRepository.class);
  private final TradeRepository tradeRepository = mock(TradeRepository.class);
  private final BinanceClient binanceClient = mock(BinanceClient.class);
  private final RecordingNotifier notifier = new RecordingNotifier();
  private final OcoProperties ocoProperties = new OcoProperties();
  private final PositionManagerProperties managerProperties = new PositionManagerProperties();
  private final Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
  private final AtomicLong idSeq = new AtomicLong(1);
  private final Map<Long, PositionEntity> positions = new HashMap<>();
  private final Map<Long, ManagedOrderEntity> orders = new HashMap<>();
  private final DriftWatchdog driftWatchdog = mock(DriftWatchdog.class);
  private final PnlAttributionService pnlAttributionService = mock(PnlAttributionService.class);
  private final TenantAccessGuard tenantAccessGuard = mock(TenantAccessGuard.class);
  private final UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

  @BeforeEach
  void setUp() {
    positions.clear();
    orders.clear();
    notifier.events.clear();

    when(tenantAccessGuard.requireCurrentTenant()).thenReturn(tenantId);

    when(positionRepository.save(any(PositionEntity.class)))
        .thenAnswer(
            invocation -> {
              PositionEntity entity = invocation.getArgument(0);
              if (entity.getId() == null) {
                entity.setId(idSeq.getAndIncrement());
              }
              positions.put(entity.getId(), entity);
              return entity;
            });
    when(positionRepository.findById(any(Long.class)))
        .thenAnswer(invocation -> Optional.ofNullable(positions.get(invocation.getArgument(0))));
    when(positionRepository.findByStatus(PositionStatus.OPEN))
        .thenAnswer(invocation -> positions.values().stream().filter(p -> p.getStatus() == PositionStatus.OPEN).toList());

    when(managedOrderRepository.save(any(ManagedOrderEntity.class)))
        .thenAnswer(
            invocation -> {
              ManagedOrderEntity entity = invocation.getArgument(0);
              if (entity.getId() == null) {
                entity.setId(idSeq.getAndIncrement());
              }
              orders.put(entity.getId(), entity);
              return entity;
            });
    when(managedOrderRepository.findByClientOrderId(any(String.class)))
        .thenAnswer(
            invocation ->
                orders.values().stream()
                    .filter(o -> invocation.getArgument(0).equals(o.getClientOrderId()))
                    .findFirst());
    when(managedOrderRepository.findByPosition(any(PositionEntity.class)))
        .thenAnswer(
            invocation ->
                orders.values().stream()
                    .filter(o -> o.getPosition().equals(invocation.getArgument(0)))
                    .toList());
    when(managedOrderRepository.findByPositionAndType(any(PositionEntity.class), any(ManagedOrderType.class)))
        .thenAnswer(
            invocation ->
                orders.values().stream()
                    .filter(
                        o ->
                            o.getPosition().equals(invocation.getArgument(0))
                                && o.getType() == invocation.getArgument(1))
                    .findFirst());
    when(managedOrderRepository.findByPositionIdAndStatusIn(any(Long.class), any(Collection.class)))
        .thenReturn(List.of());

    when(tradeRepository.save(any(TradeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    doThrow(new UnsupportedOperationException("oco"))
        .when(binanceClient)
        .placeOcoOrder(any(), any(), any());
  }

  @Test
  void shouldHandlePartialAndFullFillTransitions() {
    PositionManager manager = newManager();

    manager.openPosition(
        new OpenPositionCommand(
            "BTCUSDT",
            OrderSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.ONE,
            BigDecimal.valueOf(98),
            BigDecimal.valueOf(102),
            null,
            "entry",
            null,
            null,
            null,
            null));

    ManagedOrderEntity takeProfit =
        orders.values().stream().filter(o -> o.getType() == ManagedOrderType.TAKE_PROFIT).findFirst().orElseThrow();
    ManagedOrderEntity stopLoss =
        orders.values().stream().filter(o -> o.getType() == ManagedOrderType.STOP_LOSS).findFirst().orElseThrow();

    manager.onOrderUpdate(
        new ManagedOrderUpdate(
            "BTCUSDT",
            takeProfit.getClientOrderId(),
            "tp-1",
            ManagedOrderStatus.PARTIAL,
            new BigDecimal("0.4"),
            new BigDecimal("0.4"),
            BigDecimal.valueOf(101),
            Instant.now(clock)));

    assertThat(takeProfit.getStatus()).isEqualTo(ManagedOrderStatus.PARTIAL);
    assertThat(stopLoss.getQuantity()).isEqualByComparingTo(new BigDecimal("0.6"));

    manager.onOrderUpdate(
        new ManagedOrderUpdate(
            "BTCUSDT",
            takeProfit.getClientOrderId(),
            "tp-1",
            ManagedOrderStatus.FILLED,
            new BigDecimal("0.6"),
            new BigDecimal("1.0"),
            BigDecimal.valueOf(102),
            Instant.now(clock)));

    assertThat(takeProfit.getStatus()).isEqualTo(ManagedOrderStatus.FILLED);
    assertThat(stopLoss.getStatus()).isEqualTo(ManagedOrderStatus.CANCELED);
    PositionEntity position = positions.values().iterator().next();
    assertThat(position.getStatus()).isEqualTo(PositionStatus.CLOSED);
  }

  @Test
  void shouldCancelOppositeOrderOnFill() {
    PositionManager manager = newManager();

    manager.openPosition(
        new OpenPositionCommand(
            "BTCUSDT",
            OrderSide.SELL,
            BigDecimal.valueOf(100),
            BigDecimal.ONE,
            BigDecimal.valueOf(102),
            BigDecimal.valueOf(98),
            null,
            "entry",
            null,
            null,
            null,
            null));

    ManagedOrderEntity stopLoss =
        orders.values().stream().filter(o -> o.getType() == ManagedOrderType.STOP_LOSS).findFirst().orElseThrow();
    ManagedOrderEntity takeProfit =
        orders.values().stream().filter(o -> o.getType() == ManagedOrderType.TAKE_PROFIT).findFirst().orElseThrow();

    manager.onOrderUpdate(
        new ManagedOrderUpdate(
            "BTCUSDT",
            stopLoss.getClientOrderId(),
            "sl-1",
            ManagedOrderStatus.FILLED,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.valueOf(102),
            Instant.now(clock)));

    verify(binanceClient, times(1)).cancelOrder(takeProfit);
    assertThat(takeProfit.getStatus()).isEqualTo(ManagedOrderStatus.CANCELED);
  }

  private PositionManager newManager() {
    return new PositionManager(
        positionRepository,
        managedOrderRepository,
        tradeRepository,
        binanceClient,
        notifier,
        ocoProperties,
        managerProperties,
        new SimpleMeterRegistry(),
        Optional.of(clock), driftWatchdog, pnlAttributionService, tenantAccessGuard);
  }

  private static class RecordingNotifier extends TelegramNotifier {
    private final List<String> events = new ArrayList<>();

    RecordingNotifier() {
      super(new com.bottrading.config.TelegramProperties());
    }

    @Override
    public void notifyPartialFill(
        String symbol, OrderSide side, BigDecimal qty, BigDecimal price, String clientOrderId) {
      events.add("partial:" + clientOrderId);
    }

    @Override
    public void notifyTakeProfit(String symbol, OrderSide side, BigDecimal price, BigDecimal pnl) {
      events.add("tp:" + symbol);
    }

    @Override
    public void notifyStopHit(String symbol, OrderSide side, BigDecimal price, BigDecimal pnl) {
      events.add("sl:" + symbol);
    }

    @Override
    public void notifyPositionOpened(
        String symbol, OrderSide side, BigDecimal qty, BigDecimal entryPrice, String correlationId) {
      events.add("open:" + correlationId);
    }
  }
}
