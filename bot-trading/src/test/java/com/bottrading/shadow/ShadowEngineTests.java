package com.bottrading.shadow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bottrading.config.ShadowProperties;
import com.bottrading.config.StopProperties;
import com.bottrading.execution.StopEngine.StopPlan;
import com.bottrading.model.entity.ShadowPositionEntity;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PositionStatus;
import com.bottrading.notify.TelegramNotifier;
import com.bottrading.repository.ShadowPositionRepository;
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

class ShadowEngineTests {

  private final ShadowPositionRepository repository = mock(ShadowPositionRepository.class);
  private final List<ShadowPositionEntity> stored = new ArrayList<>();
  private final AtomicLong idSeq = new AtomicLong(1);
  private final RecordingNotifier notifier = new RecordingNotifier();
  private ShadowProperties properties;

  ShadowEngineTests() {
    when(repository.save(any(ShadowPositionEntity.class)))
        .thenAnswer(
            invocation -> {
              ShadowPositionEntity entity = invocation.getArgument(0);
              if (entity.getId() == null) {
                entity.setId(idSeq.getAndIncrement());
              }
              stored.removeIf(p -> p.getId().equals(entity.getId()));
              stored.add(entity);
              return entity;
            });
    when(repository.findBySymbolOrderByOpenedAtDesc(any(String.class)))
        .thenAnswer(
            invocation -> {
              String symbol = invocation.getArgument(0);
              return stored.stream()
                  .filter(p -> p.getSymbol().equalsIgnoreCase(symbol))
                  .sorted((a, b) -> b.getOpenedAt().compareTo(a.getOpenedAt()))
                  .toList();
            });
  }

  @BeforeEach
  void setup() {
    stored.clear();
    notifier.events.clear();
    properties = new ShadowProperties();
    properties.setEnabled(true);
    properties.setDivergenceMinTrades(1);
    properties.setDivergencePctThreshold(BigDecimal.valueOf(5));
  }

  @Test
  void shouldCloseShadowOnTakeProfit() {
    ShadowEngine engine = newEngine();
    StopPlan plan = new StopPlan(BigDecimal.valueOf(99), BigDecimal.valueOf(101), BigDecimal.valueOf(0.5), null, StopProperties.StopSymbolProperties.from(new StopProperties()));
    engine.registerShadow("BTCUSDT", OrderSide.BUY, BigDecimal.valueOf(100), BigDecimal.ONE, plan);

    engine.onPriceUpdate("BTCUSDT", BigDecimal.valueOf(101.5));

    ShadowEngine.ShadowStatus status = engine.status("BTCUSDT");
    assertThat(status.shadowPnl()).isGreaterThan(BigDecimal.ZERO);
    assertThat(stored).allMatch(p -> p.getStatus() == PositionStatus.CLOSED);
  }

  @Test
  void shouldNotifyDivergence() {
    ShadowEngine engine = newEngine();
    ShadowPositionEntity closed = new ShadowPositionEntity();
    closed.setId(idSeq.getAndIncrement());
    closed.setSymbol("ETHUSDT");
    closed.setStatus(PositionStatus.CLOSED);
    closed.setOpenedAt(Instant.now());
    stored.add(closed);

    engine.registerLiveFill("ETHUSDT", BigDecimal.valueOf(10));
    engine.registerShadowFill("ETHUSDT", BigDecimal.valueOf(40));

    assertThat(notifier.events).anyMatch(e -> e.contains("divergence"));
  }

  private ShadowEngine newEngine() {
    return new ShadowEngine(
        properties,
        repository,
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
    public void notifyDivergence(String symbol, BigDecimal livePnl, BigDecimal shadowPnl, BigDecimal thresholdPct) {
      events.add("divergence " + symbol);
    }
  }
}
