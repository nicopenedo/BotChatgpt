package com.bottrading.saas.web.api;

import com.bottrading.saas.model.dto.ApiKeyRequest;
import com.bottrading.saas.model.dto.SignupRequest;
import com.bottrading.saas.model.dto.TenantStatusResponse;
import com.bottrading.saas.model.entity.AuditEventEntity;
import com.bottrading.saas.security.TenantContext;
import com.bottrading.saas.security.TenantUserDetails;
import com.bottrading.saas.config.SaasProperties;
import com.bottrading.saas.service.BillingService;
import com.bottrading.saas.service.ConsentService;
import com.bottrading.saas.service.LimitsGuardService;
import com.bottrading.saas.service.OnboardingService;
import com.bottrading.saas.service.ShadowPromoterService;
import com.bottrading.saas.service.TenantAccountService;
import com.bottrading.saas.service.TenantDataExportService;
import com.bottrading.saas.service.TenantNotificationService;
import com.bottrading.saas.service.TenantReportService;
import com.bottrading.saas.service.TenantSecurityService;
import com.bottrading.saas.service.TenantStatusService;
import com.bottrading.saas.repository.AuditEventRepository;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
  private final TenantAccountService tenantAccountService;
  private final ConsentService consentService;
  private final TenantDataExportService tenantDataExportService;
  private final SaasProperties properties;

  public TenantApiController(
      TenantSecurityService tenantSecurityService,
      TenantStatusService tenantStatusService,
      TenantNotificationService notificationService,
      TenantReportService tenantReportService,
      AuditEventRepository auditEventRepository,
      LimitsGuardService limitsGuardService,
      ShadowPromoterService shadowPromoterService,
      OnboardingService onboardingService,
      BillingService billingService,
      TenantAccountService tenantAccountService,
      ConsentService consentService,
      TenantDataExportService tenantDataExportService,
      SaasProperties properties) {
    this.tenantSecurityService = tenantSecurityService;
    this.tenantStatusService = tenantStatusService;
    this.notificationService = notificationService;
    this.tenantReportService = tenantReportService;
    this.auditEventRepository = auditEventRepository;
    this.limitsGuardService = limitsGuardService;
    this.shadowPromoterService = shadowPromoterService;
    this.onboardingService = onboardingService;
    this.billingService = billingService;
    this.tenantAccountService = tenantAccountService;
    this.consentService = consentService;
    this.tenantDataExportService = tenantDataExportService;
    this.properties = properties;
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
      @RequestBody(required = false) Map<String, Object> payload,
      @RequestHeader(value = "X-Forwarded-For", required = false) String ip) {
    UUID tenantId = currentTenant();
    if (tenantAccountService.isTradingPaused(tenantId) && "LIVE".equalsIgnoreCase(mode)) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("error", "Trading paused for tenant"));
    }
    if ("LIVE".equalsIgnoreCase(mode)) {
      if (!consentService.hasCurrentConsent(tenantId)) {
        if (payload != null) {
          Object consent = payload.get("consent");
          if (consent instanceof Map<?, ?> consentMap) {
            String terms = toString(consentMap.get("termsVersionHash"));
            String risk = toString(consentMap.get("riskVersionHash"));
            Boolean accepted = Boolean.valueOf(String.valueOf(consentMap.getOrDefault("accepted", false)));
            if (Boolean.TRUE.equals(accepted)) {
              TenantUserDetails user = currentUser();
              consentService.recordConsent(
                  tenantId,
                  user.getId(),
                  terms != null ? terms : properties.getLegal().getTermsVersion(),
                  risk != null ? risk : properties.getLegal().getRiskVersion(),
                  ip,
                  String.valueOf(consentMap.getOrDefault("ua", "api")));
            } else {
              return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                  .body(
                      Map.of(
                          "error", "Consent required",
                          "termsVersion", properties.getLegal().getTermsVersion(),
                          "riskVersion", properties.getLegal().getRiskVersion()));
            }
          } else {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                .body(
                    Map.of(
                        "error", "Consent details missing",
                        "termsVersion", properties.getLegal().getTermsVersion(),
                        "riskVersion", properties.getLegal().getRiskVersion()));
          }
        } else {
          return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
              .body(
                  Map.of(
                      "error", "Consent required",
                      "termsVersion", properties.getLegal().getTermsVersion(),
                      "riskVersion", properties.getLegal().getRiskVersion()));
        }
      }
    }
    if (payload != null && payload.containsKey("kpis") && "LIVE".equalsIgnoreCase(mode)) {
      Object kpiObj = payload.get("kpis");
      if (kpiObj instanceof Map<?, ?> map) {
        boolean promote =
            shadowPromoterService.shouldPromote(
                map.entrySet().stream()
                    .filter(e -> e.getValue() instanceof Number)
                    .collect(
                        java.util.stream.Collectors.toMap(
                            e -> e.getKey().toString(),
                            e -> new BigDecimal(e.getValue().toString()))));
        if (!promote) {
          return ResponseEntity.badRequest().body(Map.of("error", "Shadow KPIs below thresholds"));
        }
      }
    }
    notificationService.notify(tenantId, "mode-change", "Bot " + botId + " → " + mode);
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
  public ResponseEntity<Void> invoiceWebhook(
      @RequestBody Map<String, Object> payload,
      @RequestHeader(value = "X-Event-Id") String eventId,
      @RequestHeader(value = "X-Signature") String signature) throws Exception {
    UUID tenantId = UUID.fromString(payload.get("tenantId").toString());
    String status = payload.getOrDefault("status", "unknown").toString();
    billingService.processWebhook("invoice", tenantId, eventId, signature, payload);
    billingService.handleInvoiceStatus(tenantId, status);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/billing/webhook/subscription")
  public ResponseEntity<Void> subscriptionWebhook(
      @RequestBody Map<String, Object> payload,
      @RequestHeader(value = "X-Event-Id") String eventId,
      @RequestHeader(value = "X-Signature") String signature) throws Exception {
    UUID tenantId = UUID.fromString(payload.get("tenantId").toString());
    String status = payload.getOrDefault("status", "unknown").toString();
    String subId = (String) payload.getOrDefault("subscriptionId", "");
    billingService.processWebhook("subscription", tenantId, eventId, signature, payload);
    billingService.handleSubscriptionUpdate(tenantId, status, subId);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/tenant/account/delete")
  public ResponseEntity<Map<String, Object>> deleteAccount(@RequestBody Map<String, String> body) {
    TenantUserDetails user = currentUser();
    String password = body.get("password");
    String totp = body.get("totp");
    String confirm = body.get("confirm");
    if (!"YES".equalsIgnoreCase(confirm)) {
      return ResponseEntity.badRequest().body(Map.of("error", "Confirmación requerida"));
    }
    if (password == null || password.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Contraseña requerida"));
    }
    tenantAccountService.requestDeletion(user.getTenantId(), user.getId(), password, totp);
    return ResponseEntity.accepted().body(Map.of("status", "scheduled"));
  }

  @PostMapping("/tenant/account/export")
  public ResponseEntity<Map<String, Object>> requestExport(@RequestBody(required = false) Map<String, String> body) {
    TenantUserDetails user = currentUser();
    if (user.isMfaEnabled()) {
      String totp = body != null ? body.get("totp") : null;
      if (!tenantSecurityService.verifyTotp(user, totp)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "TOTP requerido"));
      }
    }
    String token = tenantAccountService.createExportToken(user.getTenantId(), user.getId());
    return ResponseEntity.ok(
        Map.of(
            "token", token,
            "expiresAt", Instant.now().plusSeconds(properties.getLegal().getExportTokenTtlMinutes() * 60L)));
  }

  @PostMapping("/tenant/risk/pause-all")
  public ResponseEntity<Map<String, Object>> pauseAll(@RequestBody Map<String, Object> body) {
    TenantUserDetails user = currentUser();
    boolean pause = Boolean.TRUE.equals(body.get("pause"));
    Object confirmation = body.get("confirm");
    if (!Boolean.TRUE.equals(confirmation) && !"YES".equalsIgnoreCase(String.valueOf(confirmation))) {
      return ResponseEntity.badRequest().body(Map.of("error", "Confirmación requerida"));
    }
    if (user.isMfaEnabled()) {
      String totp = body.get("totp") != null ? body.get("totp").toString() : null;
      if (!tenantSecurityService.verifyTotp(user, totp)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "TOTP inválido"));
      }
    }
    tenantAccountService.updatePause(user.getTenantId(), pause, user.getId());
    return ResponseEntity.ok(Map.of("paused", pause));
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

  private String toString(Object value) {
    return value != null ? value.toString() : null;
  }
}
