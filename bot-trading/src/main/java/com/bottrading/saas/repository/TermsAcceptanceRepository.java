package com.bottrading.saas.repository;

import com.bottrading.saas.model.entity.TermsAcceptanceEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsAcceptanceRepository extends JpaRepository<TermsAcceptanceEntity, UUID> {
  List<TermsAcceptanceEntity> findByTenantIdOrderByAcceptedAtDesc(UUID tenantId);
}
