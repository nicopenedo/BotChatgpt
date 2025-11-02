package com.bottrading.execution.metrics;

import com.bottrading.config.ExecutionProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlippageMetricsTest {

  private MeterRegistry meterRegistry;
  private ExecutionProperties executionProperties;
  private MutableClock clock;
  private SlippageMetrics slippageMetrics;

  @BeforeEach
  void setup() {
    meterRegistry = new SimpleMeterRegistry();
    executionProperties = new ExecutionProperties();
    executionProperties.getMetrics().setCleanupMs(10);
    executionProperties.getMetrics().setTtlMs(Duration.ofSeconds(1).toMillis());
    clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
    slippageMetrics = new SlippageMetrics(meterRegistry, executionProperties, clock);
  }

  @Test
  void isolatesValuesPerTenantAndSymbol() {
    UUID t1 = UUID.randomUUID();
    UUID t2 = UUID.randomUUID();

    slippageMetrics.record(t1, "BTCUSDT", 10.0);
    slippageMetrics.record(t2, "BTCUSDT", 50.0);
    slippageMetrics.record(t1, "BTCUSDT", 20.0);

    double tenantOneValue =
        meterRegistry
            .get("exec.slippage.avg_bps")
            .tag("tenant", t1.toString())
            .tag("symbol", "BTCUSDT")
            .gauge()
            .value();
    double tenantTwoValue =
        meterRegistry
            .get("exec.slippage.avg_bps")
            .tag("tenant", t2.toString())
            .tag("symbol", "BTCUSDT")
            .gauge()
            .value();

    assertThat(tenantOneValue).isEqualTo(15.0);
    assertThat(tenantTwoValue).isEqualTo(50.0);
  }

  @Test
  void cleanupRemovesExpiredSeries() {
    UUID tenantId = UUID.randomUUID();
    slippageMetrics.record(tenantId, "ETHUSDT", 5.0);

    clock.advance(Duration.ofSeconds(2));
    slippageMetrics.cleanup();

    assertThat(
            meterRegistry
                .find("exec.slippage.avg_bps")
                .tag("tenant", tenantId.toString())
                .tag("symbol", "ETHUSDT")
                .gauge())
        .isNull();
  }

  private static final class MutableClock extends Clock {
    private Instant instant;
    private final ZoneId zone = ZoneId.of("UTC");

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }
  }
}
