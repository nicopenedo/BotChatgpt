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

class PovMetricsTest {

  private MeterRegistry meterRegistry;
  private ExecutionProperties executionProperties;
  private MutableClock clock;
  private PovMetrics povMetrics;

  @BeforeEach
  void setup() {
    meterRegistry = new SimpleMeterRegistry();
    executionProperties = new ExecutionProperties();
    executionProperties.getMetrics().setCleanupMs(10);
    executionProperties.getMetrics().setTtlMs(Duration.ofSeconds(1).toMillis());
    clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
    povMetrics = new PovMetrics(meterRegistry, executionProperties, clock);
  }

  @Test
  void isolatesParticipationPerTenantAndSymbol() {
    UUID t1 = UUID.randomUUID();
    UUID t2 = UUID.randomUUID();

    povMetrics.update(t1, "BTCUSDT", 0.25);
    povMetrics.update(t2, "BTCUSDT", 0.75);

    double tenantOneValue =
        meterRegistry
            .get("exec.pov.progress")
            .tag("tenant", t1.toString())
            .tag("symbol", "BTCUSDT")
            .gauge()
            .value();
    double tenantTwoValue =
        meterRegistry
            .get("exec.pov.progress")
            .tag("tenant", t2.toString())
            .tag("symbol", "BTCUSDT")
            .gauge()
            .value();

    assertThat(tenantOneValue).isEqualTo(0.25);
    assertThat(tenantTwoValue).isEqualTo(0.75);
  }

  @Test
  void cleanupEvictsStaleMeters() {
    UUID tenantId = UUID.randomUUID();
    povMetrics.update(tenantId, "ETHUSDT", 0.5);

    clock.advance(Duration.ofSeconds(2));
    povMetrics.cleanup();

    assertThat(
            meterRegistry
                .find("exec.pov.progress")
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
