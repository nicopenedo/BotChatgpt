package com.bottrading.saas.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TenantAccountCleanupJob {

  private final TenantAccountService tenantAccountService;

  public TenantAccountCleanupJob(TenantAccountService tenantAccountService) {
    this.tenantAccountService = tenantAccountService;
  }

  @Scheduled(cron = "0 0 * * * *")
  public void purgeMarkedTenants() {
    tenantAccountService.purgeExpiredTenants();
  }
}
