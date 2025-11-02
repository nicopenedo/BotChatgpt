package com.bottrading.web.staff;

import com.bottrading.saas.repository.BillingWebhookEventRepository;
import com.bottrading.saas.repository.TenantBillingRepository;
import com.bottrading.saas.repository.TenantRepository;
import com.bottrading.saas.service.BillingService;
import com.bottrading.saas.service.TenantAccountService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/staff")
public class StaffPortalController {

  private final TenantRepository tenantRepository;
  private final TenantBillingRepository tenantBillingRepository;
  private final BillingWebhookEventRepository billingWebhookEventRepository;
  private final BillingService billingService;
  private final TenantAccountService tenantAccountService;
  private final ObjectMapper objectMapper;

  public StaffPortalController(
      TenantRepository tenantRepository,
      TenantBillingRepository tenantBillingRepository,
      BillingWebhookEventRepository billingWebhookEventRepository,
      BillingService billingService,
      TenantAccountService tenantAccountService,
      ObjectMapper objectMapper) {
    this.tenantRepository = tenantRepository;
    this.tenantBillingRepository = tenantBillingRepository;
    this.billingWebhookEventRepository = billingWebhookEventRepository;
    this.billingService = billingService;
    this.tenantAccountService = tenantAccountService;
    this.objectMapper = objectMapper;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('SUPPORT_READ','OPS_ACTIONS','FINANCE')")
  public String index(Model model) {
    model.addAttribute("tenants", tenantRepository.findAll());
    model.addAttribute("billing", tenantBillingRepository.findAll());
    model.addAttribute("incidents", List.of());
    return "staff/dashboard";
  }

  @GetMapping("/tenants")
  @PreAuthorize("hasRole('SUPPORT_READ')")
  public String tenants(Model model) {
    model.addAttribute("tenants", tenantRepository.findAll());
    return "staff/tenants";
  }

  @GetMapping("/billing")
  @PreAuthorize("hasRole('FINANCE')")
  public String billing(Model model) {
    model.addAttribute("records", tenantBillingRepository.findAll());
    return "staff/billing";
  }

  @GetMapping("/auditoria")
  @PreAuthorize("hasRole('SUPPORT_READ')")
  public String audit(Model model) {
    model.addAttribute("webhooks", billingWebhookEventRepository.findAll());
    return "staff/audit";
  }

  @PostMapping("/risk/pause-all")
  @PreAuthorize("hasRole('OPS_ACTIONS')")
  public String pauseAll(@RequestParam("tenantId") UUID tenantId, @RequestParam("pause") boolean pause) {
    tenantAccountService.updatePause(tenantId, pause, null);
    return "redirect:/staff/tenants";
  }

  @PostMapping("/billing/replay")
  @PreAuthorize("hasRole('FINANCE')")
  public String replayWebhook(@RequestParam("eventId") String eventId) {
    billingWebhookEventRepository
        .findByEventId(eventId)
        .ifPresent(
            event -> {
              try {
                Map<String, Object> payload =
                    objectMapper.readValue(event.getPayloadJson(), new TypeReference<>() {});
                if ("invoice".equals(event.getType())) {
                  billingService.handleInvoiceStatus(event.getTenantId(), String.valueOf(payload.get("status")));
                } else if ("subscription".equals(event.getType())) {
                  billingService.handleSubscriptionUpdate(
                      event.getTenantId(),
                      String.valueOf(payload.get("status")),
                      String.valueOf(payload.getOrDefault("subscriptionId", "")));
                }
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            });
    return "redirect:/staff/auditoria";
  }
}
