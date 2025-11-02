package com.bottrading.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bottrading.saas.config.SaasProperties;
import com.bottrading.saas.model.entity.TenantEntity;
import com.bottrading.saas.model.entity.TenantPlan;
import com.bottrading.saas.repository.TenantRepository;
import com.bottrading.saas.security.TenantContext;
import com.bottrading.saas.service.TenantMetrics;
import io.micrometer.core.instrument.Tags;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TenantMetricsTest {

  private TenantRepository tenantRepository;
  private SaasProperties properties;
  private MutableClock clock;
  private TenantMetrics tenantMetrics;
  private UUID tenantId;

  @BeforeEach
  void setUp() {
    tenantRepository = mock(TenantRepository.class);
    properties = new SaasProperties();
    properties.getMetrics().setPlanCacheTtlSeconds(120);
    clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
    tenantMetrics = new TenantMetrics(tenantRepository, properties, clock);
    tenantId = UUID.randomUUID();
    TenantContext.setTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void cachesPlanLookupsWithinTtl() {
    TenantEntity entity = new TenantEntity();
    entity.setPlan(TenantPlan.PRO);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(entity));

    Tags first = tenantMetrics.tags("BTCUSDT");
    Tags second = tenantMetrics.tags("ETHUSDT");

    assertThat(first.getTag("plan")).isEqualTo("pro");
    assertThat(second.getTag("plan")).isEqualTo("pro");
    verify(tenantRepository, times(1)).findById(tenantId);
  }

  @Test
  void refreshesPlanAfterTtlExpires() {
    TenantEntity starter = new TenantEntity();
    starter.setPlan(TenantPlan.STARTER);
    TenantEntity pro = new TenantEntity();
    pro.setPlan(TenantPlan.PRO);
    when(tenantRepository.findById(tenantId))
        .thenReturn(Optional.of(starter))
        .thenReturn(Optional.of(pro));

    Tags initial = tenantMetrics.tags("BTCUSDT");
    clock.advance(Duration.ofSeconds(properties.getMetrics().getPlanCacheTtlSeconds() + 1));
    Tags refreshed = tenantMetrics.tags("BTCUSDT");

    assertThat(initial.getTag("plan")).isEqualTo("starter");
    assertThat(refreshed.getTag("plan")).isEqualTo("pro");
    verify(tenantRepository, times(2)).findById(tenantId);
  }

  private static final class MutableClock extends Clock {
    private Instant instant;
    private final ZoneId zone;

    private MutableClock(Instant instant) {
      this(instant, ZoneOffset.UTC);
    }

    private MutableClock(Instant instant, ZoneId zone) {
      this.instant = instant;
      this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return new MutableClock(instant, zone);
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
