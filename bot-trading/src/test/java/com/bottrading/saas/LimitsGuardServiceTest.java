package com.bottrading.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bottrading.saas.model.entity.TenantLimitsEntity;
import com.bottrading.saas.repository.TenantLimitsRepository;
import com.bottrading.saas.service.LimitsGuardService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LimitsGuardServiceTest {

  @Mock private TenantLimitsRepository repository;

  private LimitsGuardService service;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setup() {
    service = new LimitsGuardService(repository);
    TenantLimitsEntity limits = new TenantLimitsEntity();
    limits.setTenantId(tenantId);
    limits.setMaxBots(1);
    limits.setMaxSymbols(1);
    limits.setCanaryShareMax(new BigDecimal("0.10"));
    limits.setMaxTradesPerDay(10);
    limits.setDataRetentionDays(90);
    limits.setUpdatedAt(Instant.now());
    when(repository.findById(tenantId)).thenReturn(Optional.of(limits));
  }

  @Test
  void blocksExcessBots() {
    assertThat(service.canOpenBot(tenantId, 1)).isFalse();
  }

  @Test
  void allowsWithinLimits() {
    assertThat(service.canOpenBot(tenantId, 0)).isTrue();
    assertThat(service.canTradeSymbol(tenantId, 1)).isTrue();
    assertThat(service.canOpenTrade(tenantId, 5)).isTrue();
    assertThat(service.withinCanary(tenantId, new BigDecimal("0.10"))).isTrue();
  }

  @Test
  void blocksCanaryOverage() {
    assertThat(service.withinCanary(tenantId, new BigDecimal("0.50"))).isFalse();
  }
}
