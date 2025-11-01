package com.bottrading.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bottrading.config.StopProperties;
import com.bottrading.model.entity.ManagedOrderEntity;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.enums.ManagedOrderType;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PositionStatus;
import com.bottrading.notify.TelegramNotifier;
import com.bottrading.repository.ManagedOrderRepository;
import com.bottrading.repository.PositionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StopEngineTests {

  private final PositionRepository positionRepository = mock(PositionRepository.class);
  private final ManagedOrderRepository managedOrderRepository = mock(ManagedOrderRepository.class);
  private final List<PositionEntity> storedPositions = new ArrayList<>();
  private final List<ManagedOrderEntity> storedOrders = new ArrayList<>();
  private final AtomicLong idSeq = new AtomicLong(1);
  private final RecordingNotifier notifier = new RecordingNotifier();

  StopEngineTests() {
    when(positionRepository.save(any(PositionEntity.class)))
        .thenAnswer(
            invocation -> {
              PositionEntity entity = invocation.getArgument(0);
              if (entity.getId() == null) {
                entity.setId(idSeq.getAndIncrement());
              }
              storedPositions.removeIf(p -> p.getId().equals(entity.getId()));
              storedPositions.add(entity);
              return entity;
            });
    when(managedOrderRepository.save(any(ManagedOrderEntity.class)))
        .thenAnswer(
            invocation -> {
              ManagedOrderEntity entity = invocation.getArgument(0);
              if (entity.getId() == null) {
                entity.setId(idSeq.getAndIncrement());
              }
              storedOrders.removeIf(o -> o.getId().equals(entity.getId()));
              storedOrders.add(entity);
              return entity;
            });
    when(managedOrderRepository.findByPosition(any(PositionEntity.class)))
        .thenAnswer(
            invocation -> {
              PositionEntity position = invocation.getArgument(0);
              return storedOrders.stream().filter(o -> o.getPosition().equals(position)).toList();
            });
    when(managedOrderRepository.findByPositionAndType(any(PositionEntity.class), any(ManagedOrderType.class)))
        .thenAnswer(
            invocation -> {
              PositionEntity position = invocation.getArgument(0);
              ManagedOrderType type = invocation.getArgument(1);
              return storedOrders.stream()
                  .filter(o -> o.getPosition().equals(position) && o.getType() == type)
                  .findFirst();
            });
  }

  @BeforeEach
  void reset() {
    storedPositions.clear();
    storedOrders.clear();
    notifier.events.clear();
  }

  @Test
  void shouldHitTakeProfitAndCancelOppositeOrder() {
    StopEngine engine = newEngine();
    engine.registerPosition("BTCUSDT", OrderSide.BUY, BigDecimal.valueOf(100), BigDecimal.ONE, null, "oco");

    engine.onPriceUpdate("BTCUSDT", BigDecimal.valueOf(102), null);

    assertThat(storedPositions).hasSize(1);
    PositionEntity position = storedPositions.getFirst();
    assertThat(position.getStatus()).isEqualTo(PositionStatus.TAKE_PROFIT);
    assertThat(storedOrders)
        .anyMatch(order -> order.getType() == ManagedOrderType.STOP_LOSS && "CANCELLED".equals(order.getStatus()));
  }

  @Test
  void shouldMoveStopToBreakeven() {
    StopEngine engine = newEngine();
    engine.registerPosition("BTCUSDT", OrderSide.BUY, BigDecimal.valueOf(100), BigDecimal.ONE, null, "oco");

    engine.onPriceUpdate("BTCUSDT", BigDecimal.valueOf(101), null);

    PositionEntity position = storedPositions.getFirst();
    assertThat(position.getStopLoss()).isEqualByComparingTo(BigDecimal.valueOf(100));
    assertThat(notifier.events).anyMatch(e -> e.contains("Breakeven"));
  }

  @Test
  void shouldAdjustTrailingStop() {
    StopEngine engine = newEngine();
    engine.registerPosition("BTCUSDT", OrderSide.BUY, BigDecimal.valueOf(100), BigDecimal.ONE, null, "oco");

    engine.onPriceUpdate("BTCUSDT", BigDecimal.valueOf(101), null);

    PositionEntity position = storedPositions.getFirst();
    assertThat(position.getStopLoss()).isGreaterThan(BigDecimal.valueOf(99));
    assertThat(notifier.events).anyMatch(e -> e.contains("Trailing"));
  }

  private StopEngine newEngine() {
    StopProperties props = new StopProperties();
    return new StopEngine(
        props,
        positionRepository,
        managedOrderRepository,
        notifier,
        new SimpleMeterRegistry(),
        Optional.of(Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)));
  }

  private static class RecordingNotifier extends TelegramNotifier {
    private final List<String> events = new ArrayList<>();

    RecordingNotifier() {
      super(new com.bottrading.config.TelegramProperties());
    }

    @Override
    public void notifyStopHit(String symbol, OrderSide side, BigDecimal price, BigDecimal pnl) {
      events.add("Stop " + symbol + " " + price);
    }

    @Override
    public void notifyBreakeven(String symbol, BigDecimal price) {
      events.add("Breakeven " + symbol + " " + price);
    }

    @Override
    public void notifyTrailingAdjustment(String symbol, BigDecimal stop) {
      events.add("Trailing " + symbol + " " + stop);
    }
  }
}
