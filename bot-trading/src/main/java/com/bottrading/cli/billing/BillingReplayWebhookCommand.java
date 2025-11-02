package com.bottrading.cli.billing;

import com.bottrading.saas.model.entity.BillingWebhookEventEntity;
import com.bottrading.saas.repository.BillingWebhookEventRepository;
import com.bottrading.saas.service.BillingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "replay-webhook", description = "Reprocesar un webhook almacenado")
public class BillingReplayWebhookCommand implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(BillingReplayWebhookCommand.class);

  @CommandLine.Option(names = "--event", required = true, description = "ID del evento")
  private String eventId;

  private final BillingWebhookEventRepository repository;
  private final BillingService billingService;
  private final ObjectMapper objectMapper;

  public BillingReplayWebhookCommand(
      BillingWebhookEventRepository repository, BillingService billingService, ObjectMapper objectMapper) {
    this.repository = repository;
    this.billingService = billingService;
    this.objectMapper = objectMapper;
  }

  @Override
  public void run() {
    BillingWebhookEventEntity event =
        repository
            .findByEventId(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));
    try {
      Map<String, Object> payload = objectMapper.readValue(event.getPayloadJson(), new TypeReference<>() {});
      UUID tenantId = event.getTenantId();
      switch (event.getType()) {
        case "invoice" -> {
          String status = String.valueOf(payload.getOrDefault("status", "unknown"));
          billingService.handleInvoiceStatus(tenantId, status);
        }
        case "subscription" -> {
          String status = String.valueOf(payload.getOrDefault("status", "unknown"));
          String subscriptionId = String.valueOf(payload.getOrDefault("subscriptionId", ""));
          billingService.handleSubscriptionUpdate(tenantId, status, subscriptionId);
        }
        default -> log.warn("Tipo de evento desconocido: {}", event.getType());
      }
    } catch (Exception e) {
      throw new IllegalStateException("Error al reprocesar webhook", e);
    }
  }
}
