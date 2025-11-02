package com.bottrading.saas.repository;

import com.bottrading.saas.model.entity.ReferralEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralRepository extends JpaRepository<ReferralEntity, UUID> {
  List<ReferralEntity> findByReferrerTenantId(UUID tenantId);

  List<ReferralEntity> findByReferredTenantId(UUID tenantId);
}
