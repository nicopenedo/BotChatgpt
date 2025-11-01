package com.bottrading.execution;

import com.bottrading.config.ExecutionProperties;
import com.bottrading.execution.ExecutionPolicy.LimitPlan;
import com.bottrading.execution.ExecutionPolicy.MarketPlan;
import com.bottrading.execution.ExecutionPolicy.PovPlan;
import com.bottrading.execution.ExecutionPolicy.TcaModel;
import com.bottrading.execution.ExecutionPolicy.TwapPlan;
import com.bottrading.model.dto.ExchangeInfo;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionPolicyTest {

  private final ExecutionProperties properties = new ExecutionProperties();
  private final ExecutionPolicy policy = new ExecutionPolicy(properties);

  private final TcaModel neutralTca = (symbol, type, timestamp) -> Double.NaN;

  @Test
  void shouldSelectLimitWhenSpreadWideAndUrgencyLow() {
    ExecutionRequest request =
        new ExecutionRequest(
            "BTCUSDT",
            OrderSide.BUY,
            new BigDecimal("1"),
            new BigDecimal("100"),
            new BigDecimal("100"),
            new ExchangeInfo(new BigDecimal("0.01"), new BigDecimal("0.001"), BigDecimal.TEN),
            ExecutionRequest.Urgency.LOW,
            5,
            Instant.now().plusSeconds(60),
            true,
            BigDecimal.valueOf(1_000_000),
            BigDecimal.ONE,
            12,
            5,
            10,
            "id");
    MarketSnapshot snapshot =
        new MarketSnapshot(new BigDecimal("100"), 12, 5, 10, BigDecimal.TEN, BigDecimal.valueOf(1000));

    var plan = policy.planFor(request, snapshot, neutralTca);

    assertThat(plan).isInstanceOf(LimitPlan.class);
  }

  @Test
  void shouldSelectMarketWhenUrgencyHigh() {
    ExecutionRequest request =
        new ExecutionRequest(
            "BTCUSDT",
            OrderSide.BUY,
            new BigDecimal("1"),
            new BigDecimal("100"),
            new BigDecimal("100"),
            new ExchangeInfo(new BigDecimal("0.01"), new BigDecimal("0.001"), BigDecimal.TEN),
            ExecutionRequest.Urgency.HIGH,
            5,
            Instant.now().plusSeconds(60),
            true,
            BigDecimal.valueOf(1_000_000),
            BigDecimal.ONE,
            2,
            5,
            10,
            "id");
    MarketSnapshot snapshot =
        new MarketSnapshot(new BigDecimal("100"), 2, 5, 10, BigDecimal.TEN, BigDecimal.valueOf(1000));

    var plan = policy.planFor(request, snapshot, neutralTca);

    assertThat(plan).isInstanceOf(MarketPlan.class);
  }

  @Test
  void shouldSelectTwapForVeryLargeParticipation() {
    ExecutionRequest request =
        new ExecutionRequest(
            "BTCUSDT",
            OrderSide.BUY,
            new BigDecimal("3"),
            new BigDecimal("100"),
            new BigDecimal("300"),
            new ExchangeInfo(new BigDecimal("0.01"), new BigDecimal("0.001"), BigDecimal.TEN),
            ExecutionRequest.Urgency.MEDIUM,
            5,
            Instant.now().plusSeconds(60),
            true,
            BigDecimal.valueOf(1_000_000),
            BigDecimal.ONE,
            1,
            5,
            10,
            "id");
    MarketSnapshot snapshot =
        new MarketSnapshot(new BigDecimal("100"), 1, 5, 10, new BigDecimal("5"), BigDecimal.valueOf(500));

    var plan = policy.planFor(request, snapshot, neutralTca);

    assertThat(plan).isInstanceOf(TwapPlan.class);
  }

  @Test
  void shouldSelectPovWhenParticipationModerate() {
    ExecutionRequest request =
        new ExecutionRequest(
            "BTCUSDT",
            OrderSide.SELL,
            new BigDecimal("1"),
            new BigDecimal("100"),
            new BigDecimal("100"),
            new ExchangeInfo(new BigDecimal("0.01"), new BigDecimal("0.001"), BigDecimal.TEN),
            ExecutionRequest.Urgency.MEDIUM,
            5,
            Instant.now().plusSeconds(60),
            true,
            BigDecimal.valueOf(1_000_000),
            BigDecimal.ONE,
            1,
            5,
            10,
            "id");
    MarketSnapshot snapshot =
        new MarketSnapshot(new BigDecimal("100"), 1, 5, 10, new BigDecimal("5"), BigDecimal.valueOf(500));

    var plan = policy.planFor(
        request,
        snapshot,
        (symbol, type, timestamp) -> type == OrderType.MARKET ? 20 : Double.NaN);

    assertThat(plan).isInstanceOf(PovPlan.class);
  }
}
