package com.bottrading.saas.web.api;

import com.bottrading.saas.model.dto.ApiKeyRequest;
import com.bottrading.saas.model.dto.SignupRequest;
import com.bottrading.saas.model.dto.TenantStatusResponse;
import com.bottrading.saas.model.entity.AuditEventEntity;
import com.bottrading.saas.security.TenantContext;
import com.bottrading.saas.security.TenantUserDetails;
import com.bottrading.saas.service.BillingService;
import com.bottrading.saas.service.OnboardingService;
import com.bottrading.saas.service.ShadowPromoterService;
import com.bottrading.saas.service.TenantNotificationService;
import com.bottrading.saas.service.TenantReportService;
import com.bottrading.saas.service.TenantSecurityService;
import com.bottrading.saas.service.TenantStatusService;
import com.bottrading.saas.repository.AuditEventRepository;
import com.bottrading.saas.service.LimitsGuardService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TenantApiController {

  private final TenantSecurityService tenantSecurityService;
  private final TenantStatusService tenantStatusService;
  private final TenantNotificationService notificationService;
  private final TenantReportService tenantReportService;
  private final AuditEventRepository auditEventRepository;
  private final LimitsGuardService limitsGuardService;
  private final ShadowPromoterService shadowPromoterService;
  private final OnboardingService onboardingService;
  private final BillingService billingService;

  public TenantApiController(
      TenantSecurityService tenantSecurityService,
      TenantStatusService tenantStatusService,
      TenantNotificationService notificationService,
      TenantReportService tenantReportService,
      AuditEventRepository auditEventRepository,
      LimitsGuardService limitsGuardService,
      ShadowPromoterService shadowPromoterService,
      OnboardingService onboardingService,
      BillingService billingService) {
    this.tenantSecurityService = tenantSecurityService;
    this.tenantStatusService = tenantStatusService;
    this.notificationService = notificationService;
    this.tenantReportService = tenantReportService;
    this.auditEventRepository = auditEventRepository;
    this.limitsGuardService = limitsGuardService;
    this.shadowPromoterService = shadowPromoterService;
    this.onboardingService = onboardingService;
    this.billingService = billingService;
  }

  @PostMapping("/signup")
  public ResponseEntity<Map<String, Object>> signup(@Valid @RequestBody SignupRequest request) {
    UUID tenantId = onboardingService.signup(request);
    return ResponseEntity.ok(Map.of("tenantId", tenantId));
  }

  @PostMapping("/tenant/api-keys")
  public ResponseEntity<?> createApiKey(@Valid @RequestBody ApiKeyRequest request) {
    TenantUserDetails principal = currentUser();
    tenantSecurityService.storeApiKey(principal.getTenantId(), principal.getId(), request);
    return ResponseEntity.ok(Map.of("status", "stored"));
  }

  @GetMapping("/tenant/status")
  public ResponseEntity<TenantStatusResponse> status() {
    return tenantStatusService
        .getStatus(currentTenant())
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/bots/{botId}/mode/{mode}")
  public ResponseEntity<Map<String, Object>> changeBotMode(
      @PathVariable("botId") String botId,
      @PathVariable("mode") String mode,
      @RequestBody(required = false) Map<String, BigDecimal> kpis) {
    if ("LIVE".equalsIgnoreCase(mode) && kpis != null) {
      boolean promote = shadowPromoterService.shouldPromote(kpis);
      if (!promote) {
        return ResponseEntity.badRequest().body(Map.of("error", "Shadow KPIs below thresholds"));
      }
    }
    notificationService.notify(currentTenant(), "mode-change", "Bot " + botId + " → " + mode);
    return ResponseEntity.ok(Map.of("botId", botId, "mode", mode));
  }

  @PostMapping("/bots/{botId}/limits")
  public ResponseEntity<Map<String, Object>> updateBotLimits(
      @PathVariable("botId") String botId, @RequestBody Map<String, Object> limits) {
    Object canaryValue = limits.get("canaryShare");
    if (canaryValue instanceof Number number) {
      boolean ok = limitsGuardService.withinCanary(currentTenant(), BigDecimal.valueOf(number.doubleValue()));
      if (!ok) {
        return ResponseEntity.badRequest().body(Map.of("error", "Canary share exceeds plan limits"));
      }
    }
    notificationService.notify(currentTenant(), "limits", "Updated limits for " + botId);
    return ResponseEntity.ok(Map.of("botId", botId, "limits", limits));
  }

  @GetMapping("/pnl/attribution")
  public ResponseEntity<List<Map<String, Object>>> pnlAttribution(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    List<Map<String, Object>> data =
        List.of(
            Map.of(
                "bucket",
                "signal",
                "pnl",
                BigDecimal.ZERO,
                "fees",
                BigDecimal.ZERO,
                "trades",
                0));
    return ResponseEntity.ok(data);
  }

  @GetMapping("/bandit/stats")
  public ResponseEntity<Map<String, Object>> banditStats(
      @RequestParam String symbol, @RequestParam(required = false) String regime) {
    Map<String, Object> stats =
        Map.of(
            "symbol",
            symbol,
            "regime",
            regime,
            "pulls",
            0,
            "reward",
            BigDecimal.ZERO,
            "canaryShare",
            BigDecimal.ZERO);
    return ResponseEntity.ok(stats);
  }

  @GetMapping("/reports/monthly")
  public ResponseEntity<Map<String, Object>> monthlyReport(@RequestParam("yyyymm") String yyyymm)
      throws Exception {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");
    YearMonth month = YearMonth.parse(yyyymm, formatter);
    var path = tenantReportService.generateMonthlyReport(currentTenant(), month);
    return ResponseEntity.ok(Map.of("path", path.toString()));
  }

  @PostMapping("/notifications/test")
  public ResponseEntity<Map<String, Object>> sendTestNotification(@RequestBody Map<String, String> body) {
    notificationService.notify(currentTenant(), body.getOrDefault("channel", "email"), "Test alert");
    return ResponseEntity.ok(Map.of("status", "sent"));
  }

  @GetMapping("/audit")
  public ResponseEntity<List<AuditEventEntity>> audit(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) String type) {
    List<AuditEventEntity> events =
        auditEventRepository.findByTenantIdAndTimestampBetweenOrderByTimestampDesc(
            currentTenant(), from, to);
    if (type != null) {
      events = events.stream().filter(event -> type.equals(event.getType())).toList();
    }
    return ResponseEntity.ok(events);
  }

  @PostMapping("/billing/webhook/invoice")
  public ResponseEntity<Void> invoiceWebhook(@RequestBody Map<String, Object> payload) {
    UUID tenantId = UUID.fromString(payload.get("tenantId").toString());
    String status = payload.getOrDefault("status", "unknown").toString();
    billingService.handleInvoiceStatus(tenantId, status);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/billing/webhook/subscription")
  public ResponseEntity<Void> subscriptionWebhook(@RequestBody Map<String, Object> payload) {
    UUID tenantId = UUID.fromString(payload.get("tenantId").toString());
    String status = payload.getOrDefault("status", "unknown").toString();
    String subId = (String) payload.getOrDefault("subscriptionId", "");
    billingService.handleSubscriptionUpdate(tenantId, status, subId);
    return ResponseEntity.ok().build();
  }

  private TenantUserDetails currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof TenantUserDetails details) {
      return details;
    }
    throw new IllegalStateException("User not authenticated");
  }

  private UUID currentTenant() {
    UUID tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      TenantUserDetails details = currentUser();
      return details.getTenantId();
    }
    return tenantId;
  }
}
