package com.bottrading.service.tca;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.config.TradingProps;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TcaServiceTest {

  private TradingProps props;
  private TcaService service;

  @BeforeEach
  void setUp() {
    props = new TradingProps();
    props.getTca().setEnabled(true);
    props.getTca().setHistorySize(100);
    service = new TcaService(props, new SimpleMeterRegistry());
  }

  @Test
  void shouldCaptureSlippageAndRecommendOrderTypes() {
    Instant base = Instant.parse("2024-01-01T12:00:00Z");
    service.recordSubmission(
        "order-1",
        "BTCUSDT",
        OrderSide.BUY,
        OrderType.MARKET,
        BigDecimal.valueOf(100),
        BigDecimal.valueOf(1_000_000),
        BigDecimal.ONE,
        base);
    service.recordFill(
        "order-1",
        BigDecimal.valueOf(100.2),
        BigDecimal.valueOf(0.1),
        base.plusMillis(500));

    double expectedMarket = service.expectedSlippageBps("BTCUSDT", OrderType.MARKET, base);
    assertThat(expectedMarket).isGreaterThan(0);
    OrderType recommended = service.recommendOrderType("BTCUSDT", OrderType.MARKET, base.plusSeconds(1));
    assertThat(recommended).isEqualTo(OrderType.LIMIT);

    Instant later = base.plusHours(1);
    service.recordSubmission(
        "order-2",
        "BTCUSDT",
        OrderSide.BUY,
        OrderType.LIMIT,
        BigDecimal.valueOf(100.1),
        BigDecimal.valueOf(1_000_000),
        BigDecimal.ONE,
        later);
    service.recordFill(
        "order-2",
        BigDecimal.valueOf(100.101),
        BigDecimal.ZERO,
        later.plusMillis(250));

    OrderType limitRecommendation = service.recommendOrderType("BTCUSDT", OrderType.LIMIT, later.plusSeconds(10));
    assertThat(limitRecommendation).isEqualTo(OrderType.MARKET);

    TcaService.AggregatedStats stats = service.aggregate("BTCUSDT", base.minusSeconds(10), later.plusSeconds(10));
    assertThat(stats.samples()).isEqualTo(2);
    assertThat(stats.averageBps()).isGreaterThan(0);
    assertThat(stats.hourlyAverage()).isNotEmpty();
  }
}

