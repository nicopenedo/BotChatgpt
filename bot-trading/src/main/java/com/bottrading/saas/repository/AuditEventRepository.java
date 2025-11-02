package com.bottrading.saas.repository;

import com.bottrading.saas.model.entity.AuditEventEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
  List<AuditEventEntity> findByTenantIdAndTimestampBetweenOrderByTimestampDesc(
      UUID tenantId, Instant from, Instant to);
}
