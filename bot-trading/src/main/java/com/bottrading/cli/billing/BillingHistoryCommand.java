package com.bottrading.cli.billing;

import com.bottrading.saas.model.entity.BillingWebhookEventEntity;
import com.bottrading.saas.repository.BillingWebhookEventRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "history", description = "Ver histórico de webhooks procesados")
public class BillingHistoryCommand implements Runnable {

  @CommandLine.Option(names = "--tenant", required = true, description = "Tenant ID")
  private UUID tenantId;

  private final BillingWebhookEventRepository repository;

  public BillingHistoryCommand(BillingWebhookEventRepository repository) {
    this.repository = repository;
  }

  @Override
  public void run() {
    List<BillingWebhookEventEntity> events = repository.findAll();
    events.stream()
        .filter(event -> tenantId.equals(event.getTenantId()))
        .sorted(java.util.Comparator.comparing(BillingWebhookEventEntity::getProcessedAt).reversed())
        .forEach(
            event ->
                System.out.printf(
                    "%s %s %s%n",
                    event.getProcessedAt(), event.getType(), event.getEventId()));
  }
}
