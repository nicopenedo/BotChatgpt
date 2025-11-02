package com.bottrading.saas.service;

import com.bottrading.saas.model.entity.TenantEntity;
import com.bottrading.saas.model.entity.TenantStatus;
import com.bottrading.saas.repository.TenantRepository;
import java.time.YearMonth;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TenantReportScheduler {

  private static final Logger log = LoggerFactory.getLogger(TenantReportScheduler.class);
  private final TenantRepository tenantRepository;
  private final TenantReportService tenantReportService;

  public TenantReportScheduler(
      TenantRepository tenantRepository, TenantReportService tenantReportService) {
    this.tenantRepository = tenantRepository;
    this.tenantReportService = tenantReportService;
  }

  @Scheduled(cron = "0 15 3 1 * *")
  public void generateMonthlyReports() {
    YearMonth month = YearMonth.now().minusMonths(1);
    List<TenantEntity> tenants = tenantRepository.findAll();
    tenants.stream()
        .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE || tenant.getStatus() == TenantStatus.GRACE)
        .forEach(
            tenant -> {
              try {
                tenantReportService.generateMonthlyReport(tenant.getId(), month);
              } catch (Exception e) {
                log.warn("Failed to generate monthly report for tenant {}", tenant.getId(), e);
              }
            });
  }
}
