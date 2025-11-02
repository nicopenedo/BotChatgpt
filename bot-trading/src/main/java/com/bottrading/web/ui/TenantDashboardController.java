package com.bottrading.web.ui;

import com.bottrading.saas.security.TenantContext;
import com.bottrading.saas.security.TenantUserDetails;
import com.bottrading.saas.service.TenantStatusService;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TenantDashboardController {

  private final TenantStatusService tenantStatusService;

  public TenantDashboardController(TenantStatusService tenantStatusService) {
    this.tenantStatusService = tenantStatusService;
  }

  @GetMapping("/ui/tenant")
  public String dashboard(Model model) {
    UUID tenantId = currentTenant();
    tenantStatusService
        .getStatus(tenantId)
        .ifPresent(status -> model.addAttribute("status", status));
    model.addAttribute("alerts", Map.of("dailyPnl", 0, "maxDd", 0));
    return "tenant/dashboard";
  }

  private UUID currentTenant() {
    UUID tenantId = TenantContext.getTenantId();
    if (tenantId != null) {
      return tenantId;
    }
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof TenantUserDetails details) {
      return details.getTenantId();
    }
    throw new IllegalStateException("Tenant not resolved");
  }
}
