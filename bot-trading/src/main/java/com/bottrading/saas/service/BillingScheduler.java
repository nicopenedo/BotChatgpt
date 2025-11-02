package com.bottrading.saas.service;

import com.bottrading.saas.repository.TenantBillingRepository;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BillingScheduler {

  private final BillingService billingService;
  private final TenantBillingRepository tenantBillingRepository;

  public BillingScheduler(BillingService billingService, TenantBillingRepository tenantBillingRepository) {
    this.billingService = billingService;
    this.tenantBillingRepository = tenantBillingRepository;
  }

  @Scheduled(cron = "0 */30 * * * *")
  public void evaluateGrace() {
    billingService.evaluateGracePeriods();
    tenantBillingRepository
        .findAll()
        .forEach(entity -> billingService.downgradeIfNeeded(entity.getTenantId()));
  }
}
