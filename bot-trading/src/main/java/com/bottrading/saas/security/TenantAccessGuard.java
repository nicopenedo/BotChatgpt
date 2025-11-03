package com.bottrading.saas.security;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class TenantAccessGuard {

  public UUID requireCurrentTenant() {
    UUID tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new AccessDeniedException("Tenant no resuelto");
    }
    return tenantId;
  }

  public void assertTenant(UUID tenantId) {
    UUID current = requireCurrentTenant();
    if (!current.equals(tenantId)) {
      throw new AccessDeniedException("Tenant inválido");
    }
  }
}
