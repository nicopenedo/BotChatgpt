package com.bottrading.throttle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateBudgetTest {

  @Test
  void shouldDelayWhenBudgetExceeded() {
    RateBudget budget = new RateBudget(2, 100);
    long now = 0L;

    assertThat(budget.reserve(Endpoint.PRICE_TICKER, now)).isZero();
    assertThat(budget.reserve(Endpoint.PRICE_TICKER, now)).isZero();

    long wait = budget.reserve(Endpoint.PRICE_TICKER, now);
    assertThat(wait).isEqualTo(Duration.ofSeconds(1).toNanos());

    long later = now + wait + 1;
    assertThat(budget.reserve(Endpoint.PRICE_TICKER, later)).isZero();
  }
}
