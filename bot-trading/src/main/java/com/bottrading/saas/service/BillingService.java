package com.bottrading.saas.service;

import com.bottrading.saas.config.SaasProperties;
import com.bottrading.saas.model.entity.BillingWebhookEventEntity;
import com.bottrading.saas.model.entity.TenantBillingEntity;
import com.bottrading.saas.model.entity.TenantBillingEntity.BillingState;
import com.bottrading.saas.model.entity.TenantEntity;
import com.bottrading.saas.model.entity.TenantPlan;
import com.bottrading.saas.model.entity.TenantStatus;
import com.bottrading.saas.repository.BillingWebhookEventRepository;
import com.bottrading.saas.repository.TenantBillingRepository;
import com.bottrading.saas.repository.TenantRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {

  private static final Logger log = LoggerFactory.getLogger(BillingService.class);
  private final TenantBillingRepository billingRepository;
  private final TenantRepository tenantRepository;
  private final AuditService auditService;
  private final BillingWebhookEventRepository webhookEventRepository;
  private final SaasProperties properties;
  private final ObjectMapper objectMapper;

  public BillingService(
      TenantBillingRepository billingRepository,
      TenantRepository tenantRepository,
      AuditService auditService,
      BillingWebhookEventRepository webhookEventRepository,
      SaasProperties properties,
      ObjectMapper objectMapper) {
    this.billingRepository = billingRepository;
    this.tenantRepository = tenantRepository;
    this.auditService = auditService;
    this.webhookEventRepository = webhookEventRepository;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void handleInvoiceStatus(UUID tenantId, String invoiceStatus) {
    TenantBillingEntity billing = billingRepository.findById(tenantId).orElse(null);
    if (billing == null) {
      log.warn("Tenant {} invoice event without billing record", tenantId);
      return;
    }
    billing.setStatus(invoiceStatus);
    billing.setUpdatedAt(Instant.now());
    BillingState previous = billing.getBillingState();
    if ("paid".equalsIgnoreCase(invoiceStatus)) {
      billing.setBillingState(BillingState.ACTIVE);
      billing.setGraceUntil(null);
      activateTenant(tenantId, billing.getPlan());
    } else if ("open".equalsIgnoreCase(invoiceStatus) || "draft".equalsIgnoreCase(invoiceStatus)) {
      billing.setBillingState(previous);
    } else {
      int graceDays = Math.max(1, properties.getBilling().getGraceDays());
      billing.setBillingState(BillingState.GRACE);
      billing.setGraceUntil(Instant.now().plusSeconds(graceDays * 86400L));
      tenantRepository
          .findById(tenantId)
          .ifPresent(
              tenant -> {
                tenant.setStatus(TenantStatus.GRACE);
                tenantRepository.save(tenant);
              });
    }
    billingRepository.save(billing);
    auditService.record(
        tenantId,
        null,
        "billing.invoice",
        Map.of("status", invoiceStatus, "state", billing.getBillingState().name()));
  }

  @Transactional
  public void handleSubscriptionUpdate(UUID tenantId, String status, String subscriptionId) {
    TenantBillingEntity billing = billingRepository.findById(tenantId).orElse(null);
    if (billing == null) {
      return;
    }
    billing.setStatus(status);
    billing.setSubscriptionId(subscriptionId);
    billing.setUpdatedAt(Instant.now());
    billingRepository.save(billing);
    auditService.record(
        tenantId,
        null,
        "billing.subscription",
        Map.of("status", status, "subscription", subscriptionId));
    if ("canceled".equalsIgnoreCase(status) || "unpaid".equalsIgnoreCase(status)) {
      moveToPastDue(tenantId, billing);
    }
  }

  @Transactional
  public BigDecimal applySuccessFee(UUID tenantId, BigDecimal pnlNet, BigDecimal rate) {
    TenantBillingEntity billing = billingRepository.findById(tenantId).orElse(null);
    if (billing == null) {
      throw new IllegalStateException("Tenant billing not found");
    }
    BigDecimal hwm = Optional.ofNullable(billing.getHwmPnlNet()).orElse(BigDecimal.ZERO);
    if (pnlNet.compareTo(hwm) <= 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal profitAbove = pnlNet.subtract(hwm);
    BigDecimal fee = profitAbove.multiply(rate);
    billing.setHwmPnlNet(pnlNet);
    billing.setUpdatedAt(Instant.now());
    billingRepository.save(billing);
    auditService.record(
        tenantId,
        null,
        "billing.success-fee",
        Map.of("pnl", pnlNet, "fee", fee, "rate", rate));
    return fee;
  }

  @Transactional
  public void processWebhook(
      String type, UUID tenantId, String eventId, String signature, Map<String, Object> payload)
      throws JsonProcessingException {
    if (webhookEventRepository.findByEventId(eventId).isPresent()) {
      log.info("Webhook {} already processed", eventId);
      return;
    }
    String json = objectMapper.writeValueAsString(payload);
    BillingWebhookEventEntity entity = new BillingWebhookEventEntity();
    entity.setTenantId(tenantId);
    entity.setEventId(eventId);
    entity.setSignature(signature);
    entity.setPayloadJson(json);
    entity.setProcessedAt(Instant.now());
    entity.setType(type);
    webhookEventRepository.save(entity);
    auditService.record(
        tenantId,
        null,
        "billing.webhook.received",
        Map.of("eventId", eventId, "type", type));
  }

  @Transactional
  public void evaluateGracePeriods() {
    Instant now = Instant.now();
    billingRepository
        .findAll()
        .forEach(
            billing -> {
              if (billing.getBillingState() == BillingState.GRACE
                  && billing.getGraceUntil() != null
                  && billing.getGraceUntil().isBefore(now)) {
                moveToPastDue(billing.getTenantId(), billing);
              }
            });
  }

  private void moveToPastDue(UUID tenantId, TenantBillingEntity billing) {
    billing.setBillingState(BillingState.PAST_DUE);
    billingRepository.save(billing);
    tenantRepository
        .findById(tenantId)
        .ifPresent(
            tenant -> {
              tenant.setStatus(TenantStatus.PAST_DUE);
              tenantRepository.save(tenant);
            });
    auditService.record(
        tenantId,
        null,
        "billing.state.past-due",
        Map.of("state", billing.getBillingState().name()));
  }

  @Transactional
  public void downgradeIfNeeded(UUID tenantId) {
    billingRepository
        .findById(tenantId)
        .ifPresent(
            billing -> {
              if (billing.getBillingState() == BillingState.PAST_DUE
                  || billing.getBillingState() == BillingState.DOWNGRADED) {
                tenantRepository
                    .findById(tenantId)
                    .ifPresent(
                        tenant -> {
                          tenant.setPlan(TenantPlan.STARTER);
                          tenant.setStatus(TenantStatus.DOWNGRADED);
                          tenantRepository.save(tenant);
                        });
                billing.setBillingState(BillingState.DOWNGRADED);
                billingRepository.save(billing);
                auditService.record(
                    tenantId,
                    null,
                    "billing.state.downgraded",
                    Map.of("plan", TenantPlan.STARTER.name()));
              }
            });
  }

  private void activateTenant(UUID tenantId, String planName) {
    tenantRepository
        .findById(tenantId)
        .ifPresent(
            tenant -> {
              tenant.setStatus(TenantStatus.ACTIVE);
              tenant.setUpdatedAt(Instant.now());
              tenantRepository.save(tenant);
              auditService.record(
                  tenantId,
                  null,
                  "tenant.activated",
                  Map.of("plan", planName));
            });
  }
}
