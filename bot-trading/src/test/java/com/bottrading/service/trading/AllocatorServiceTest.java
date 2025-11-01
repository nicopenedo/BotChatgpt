package com.bottrading.service.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bottrading.config.TradingProps;
import com.bottrading.model.dto.Kline;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PositionStatus;
import com.bottrading.repository.PositionRepository;
import com.bottrading.service.binance.BinanceClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AllocatorServiceTest {

  private final PositionRepository positionRepository = mock(PositionRepository.class);
  private final BinanceClient binanceClient = mock(BinanceClient.class);
  private TradingProps props;
  private AllocatorService allocator;

  @BeforeEach
  void setUp() {
    props = new TradingProps();
    allocator = new AllocatorService(props, positionRepository, binanceClient, new SimpleMeterRegistry());
    when(positionRepository.findByStatus(PositionStatus.OPEN)).thenReturn(List.of());
    when(positionRepository.findByStatus(PositionStatus.OPENING)).thenReturn(List.of());
    when(positionRepository.findByStatus(PositionStatus.CLOSING)).thenReturn(List.of());
  }

  @Test
  void shouldBlockWhenMaxSimultaneousPositionsReached() {
    props.getAllocator().setEnabled(true);
    props.getAllocator().setMaxSimultaneous(1);
    when(positionRepository.findByStatus(PositionStatus.OPEN)).thenReturn(List.of(position("BTCUSDT")));

    AllocatorService.AllocationDecision decision = allocator.evaluate("ETHUSDT");

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo("MAX_SIMULTANEOUS");
  }

  @Test
  void shouldBlockWhenCorrelationAboveThreshold() {
    props.getAllocator().setEnabled(true);
    props.getAllocator().setCorrMaxPairwise(0.5);
    when(positionRepository.findByStatus(PositionStatus.OPEN))
        .thenReturn(List.of(position("ETHUSDT")));

    List<Kline> correlated = klines(40, 100, 1.5);
    when(binanceClient.getKlines("BTCUSDT", "1d", props.getAllocator().getCorrLookbackDays() + 1))
        .thenReturn(correlated);
    when(binanceClient.getKlines("ETHUSDT", "1d", props.getAllocator().getCorrLookbackDays() + 1))
        .thenReturn(correlated);

    AllocatorService.AllocationDecision decision = allocator.evaluate("BTCUSDT");

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo("CORRELATION");
  }

  private PositionEntity position(String symbol) {
    PositionEntity entity = new PositionEntity();
    entity.setSymbol(symbol);
    entity.setSide(OrderSide.BUY);
    entity.setStatus(PositionStatus.OPEN);
    entity.setEntryPrice(BigDecimal.valueOf(100));
    entity.setQtyInit(BigDecimal.ONE);
    return entity;
  }

  private List<Kline> klines(int length, double base, double step) {
    Instant start = Instant.parse("2024-01-01T00:00:00Z");
    return java.util.stream.IntStream.range(0, length)
        .mapToObj(
            i -> {
              double close = base + step * i;
              double open = close - step;
              double high = Math.max(open, close) + Math.abs(step) * 0.4;
              double low = Math.min(open, close) - Math.abs(step) * 0.4;
              return new Kline(
                  start.plusSeconds(i * 86400L),
                  start.plusSeconds((i + 1L) * 86400L),
                  BigDecimal.valueOf(open),
                  BigDecimal.valueOf(high),
                  BigDecimal.valueOf(low),
                  BigDecimal.valueOf(close),
                  BigDecimal.valueOf(1000));
            })
        .toList();
  }
}

