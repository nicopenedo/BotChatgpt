package com.bottrading.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bottrading.saas.model.entity.TenantBillingEntity;
import com.bottrading.saas.model.entity.TenantEntity;
import com.bottrading.saas.model.entity.TenantPlan;
import com.bottrading.saas.model.entity.TenantStatus;
import com.bottrading.saas.repository.TenantBillingRepository;
import com.bottrading.saas.repository.TenantRepository;
import com.bottrading.saas.service.AuditService;
import com.bottrading.saas.service.BillingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

  @Mock private TenantBillingRepository billingRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private AuditService auditService;
  @Captor private ArgumentCaptor<TenantEntity> tenantCaptor;

  private BillingService service;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setup() {
    service = new BillingService(billingRepository, tenantRepository, auditService);
    TenantBillingEntity billing = new TenantBillingEntity();
    billing.setTenantId(tenantId);
    billing.setPlan("STARTER");
    billing.setStatus("pending");
    billing.setProvider(TenantBillingEntity.Provider.STRIPE);
    billing.setHwmPnlNet(BigDecimal.ZERO);
    billing.setUpdatedAt(Instant.now());
    when(billingRepository.findById(tenantId)).thenReturn(Optional.of(billing));

    TenantEntity tenant = new TenantEntity();
    tenant.setId(tenantId);
    tenant.setPlan(TenantPlan.STARTER);
    tenant.setStatus(TenantStatus.PENDING);
    tenant.setUpdatedAt(Instant.now());
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
  }

  @Test
  void activatesTenantOnPaidInvoice() {
    service.handleInvoiceStatus(tenantId, "paid");
    verify(tenantRepository).save(tenantCaptor.capture());
    assertThat(tenantCaptor.getValue().getStatus()).isEqualTo(TenantStatus.ACTIVE);
  }

  @Test
  void suspendsOnCancellation() {
    service.handleSubscriptionUpdate(tenantId, "canceled", "sub");
    verify(tenantRepository).save(tenantCaptor.capture());
    assertThat(tenantCaptor.getValue().getStatus()).isEqualTo(TenantStatus.SUSPENDED);
  }

  @Test
  void computesSuccessFee() {
    BigDecimal fee = service.applySuccessFee(tenantId, new BigDecimal("100"), new BigDecimal("0.10"));
    assertThat(fee).isEqualByComparingTo("10.0");
  }
}
