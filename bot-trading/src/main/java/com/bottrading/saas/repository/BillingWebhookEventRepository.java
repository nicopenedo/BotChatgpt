package com.bottrading.saas.repository;

import com.bottrading.saas.model.entity.BillingWebhookEventEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingWebhookEventRepository
    extends JpaRepository<BillingWebhookEventEntity, UUID> {
  Optional<BillingWebhookEventEntity> findByEventId(String eventId);
}
