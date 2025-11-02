package com.bottrading.saas.service;

import com.bottrading.saas.model.entity.TenantBillingEntity;
import com.bottrading.saas.model.entity.TenantEntity;
import com.bottrading.saas.model.entity.TenantPlan;
import com.bottrading.saas.model.entity.TenantStatus;
import com.bottrading.saas.repository.TenantBillingRepository;
import com.bottrading.saas.repository.TenantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {

  private static final Logger log = LoggerFactory.getLogger(BillingService.class);
  private final TenantBillingRepository billingRepository;
  private final TenantRepository tenantRepository;
  private final AuditService auditService;

  public BillingService(
      TenantBillingRepository billingRepository,
      TenantRepository tenantRepository,
      AuditService auditService) {
    this.billingRepository = billingRepository;
    this.tenantRepository = tenantRepository;
    this.auditService = auditService;
  }

  @Transactional
  public void handleInvoiceStatus(UUID tenantId, String invoiceStatus) {
    TenantBillingEntity billing = billingRepository.findById(tenantId).orElse(null);
    if (billing == null) {
      log.warn("Tenant {} invoice event without billing record", tenantId);
      return;
    }
    billing.setStatus(invoiceStatus);
    billing.setUpdatedAt(Instant.now());
    billingRepository.save(billing);
    auditService.record(tenantId, null, "billing.invoice", Map.of("status", invoiceStatus));
    if ("paid".equalsIgnoreCase(invoiceStatus)) {
      activateTenant(tenantId, billing.getPlan());
    }
  }

  @Transactional
  public void handleSubscriptionUpdate(UUID tenantId, String status, String subscriptionId) {
    TenantBillingEntity billing = billingRepository.findById(tenantId).orElse(null);
    if (billing == null) {
      return;
    }
    billing.setStatus(status);
    billing.setSubscriptionId(subscriptionId);
    billing.setUpdatedAt(Instant.now());
    billingRepository.save(billing);
    auditService.record(
        tenantId, null, "billing.subscription", Map.of("status", status, "subscription", subscriptionId));
    if ("canceled".equalsIgnoreCase(status) || "unpaid".equalsIgnoreCase(status)) {
      tenantRepository
          .findById(tenantId)
          .ifPresent(
              tenant -> {
                tenant.setStatus(TenantStatus.SUSPENDED);
                tenant.setUpdatedAt(Instant.now());
                tenantRepository.save(tenant);
              });
    }
  }

  @Transactional
  public BigDecimal applySuccessFee(UUID tenantId, BigDecimal pnlNet, BigDecimal rate) {
    TenantBillingEntity billing = billingRepository.findById(tenantId).orElse(null);
    if (billing == null) {
      throw new IllegalStateException("Tenant billing not found");
    }
    BigDecimal hwm = Optional.ofNullable(billing.getHwmPnlNet()).orElse(BigDecimal.ZERO);
    if (pnlNet.compareTo(hwm) <= 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal profitAbove = pnlNet.subtract(hwm);
    BigDecimal fee = profitAbove.multiply(rate);
    billing.setHwmPnlNet(pnlNet);
    billing.setUpdatedAt(Instant.now());
    billingRepository.save(billing);
    auditService.record(
        tenantId,
        null,
        "billing.success-fee",
        Map.of("pnl", pnlNet, "fee", fee, "rate", rate));
    return fee;
  }

  private void activateTenant(UUID tenantId, String planName) {
    tenantRepository
        .findById(tenantId)
        .ifPresent(
            tenant -> {
              tenant.setStatus(TenantStatus.ACTIVE);
              tenant.setUpdatedAt(Instant.now());
              tenantRepository.save(tenant);
              auditService.record(
                  tenantId,
                  null,
                  "tenant.activated",
                  Map.of("plan", planName));
            });
  }
}
