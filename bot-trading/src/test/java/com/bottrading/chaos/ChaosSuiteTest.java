package com.bottrading.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.bottrading.config.ChaosProperties;
import com.bottrading.executor.KlineEvent;
import com.bottrading.service.risk.RiskGuard;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChaosSuiteTest {

  private ChaosProperties properties;
  private RiskGuard riskGuard;
  private ChaosSuite suite;

  @BeforeEach
  void setUp() {
    properties = new ChaosProperties();
    riskGuard = mock(RiskGuard.class);
    suite = new ChaosSuite(properties, riskGuard, new SimpleMeterRegistry(), Clock.systemUTC());
  }

  @AfterEach
  void tearDown() {
    suite.stop();
  }

  @Test
  void shouldThrow429DuringBurst() {
    ChaosSettings settings =
        ChaosSettings.builder().enabled(true).apiBurst429Seconds(5).wsDropRatePct(0).build();
    suite.start(settings);
    assertThatThrownBy(() -> suite.decorateApiCall(() -> "ok").get())
        .isInstanceOf(ChaosHttpException.class)
        .hasMessageContaining("Chaos burst");
  }

  @Test
  void shouldDropOrDuplicateEvents() {
    ChaosSettings settings =
        ChaosSettings.builder()
            .enabled(true)
            .wsDropRatePct(100)
            .gapPattern(ChaosProperties.GapPattern.SKIP_EVERY_10)
            .build();
    suite.start(settings);
    List<KlineEvent> mutated = suite.applyWsChaos(new KlineEvent("BTC", "1m", 10L, true));
    assertThat(mutated.size()).isNotEqualTo(1);
  }

  @Test
  void shouldThrottleRestPollsWhenFallbackActive() {
    ChaosSettings settings = ChaosSettings.builder().enabled(true).wsDropRatePct(100).build();
    suite.start(settings);
    Instant now = Instant.now(Clock.systemUTC());
    boolean first = suite.allowRestPoll("BTCUSDT", 1000, now);
    boolean second = suite.allowRestPoll("BTCUSDT", 1000, now);
    assertThat(first).isTrue();
    assertThat(second).isFalse();
  }
}
