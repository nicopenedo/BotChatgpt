package com.bottrading.saas.service;

import com.bottrading.saas.repository.TenantRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TenantNotificationService {

  private static final Logger log = LoggerFactory.getLogger(TenantNotificationService.class);
  private final TenantRepository tenantRepository;
  private final AuditService auditService;

  public TenantNotificationService(TenantRepository tenantRepository, AuditService auditService) {
    this.tenantRepository = tenantRepository;
    this.auditService = auditService;
  }

  public void notify(UUID tenantId, String channel, String message) {
    tenantRepository
        .findById(tenantId)
        .ifPresent(
            tenant -> {
              log.info("Alert [{}] for tenant {} ({}): {}", channel, tenant.getId(), tenant.getPlan(), message);
              auditService.record(
                  tenant.getId(), null, "notification.sent", Map.of("channel", channel, "message", message));
            });
  }
}
