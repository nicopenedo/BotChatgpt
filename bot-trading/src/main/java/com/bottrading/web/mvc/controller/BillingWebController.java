package com.bottrading.web.mvc.controller;

import com.bottrading.web.mvc.service.TenantViewService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/tenant/billing")
public class BillingWebController {

  private final TenantViewService tenantViewService;

  public BillingWebController(TenantViewService tenantViewService) {
    this.tenantViewService = tenantViewService;
  }

  @GetMapping
  public String billing(Model model) {
    UUID tenantId = currentTenantId();
    model.addAttribute("pageTitle", "Billing");
    model.addAttribute("activePage", "billing");
    model.addAttribute("tenantSwitcher", tenantViewService.tenantSwitcher(tenantId));
    model.addAttribute("notifications", tenantViewService.notifications());
    model.addAttribute("billing", tenantViewService.billing(tenantId));
    return "tenant/billing";
  }

  private UUID currentTenantId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getName() != null) {
      return UUID.nameUUIDFromBytes(authentication.getName().getBytes());
    }
    return UUID.randomUUID();
  }
}
