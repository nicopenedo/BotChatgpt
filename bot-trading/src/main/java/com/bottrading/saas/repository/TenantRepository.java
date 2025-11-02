package com.bottrading.saas.repository;

import com.bottrading.saas.model.entity.TenantEntity;
import com.bottrading.saas.model.entity.TenantPlan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {
  Optional<TenantEntity> findByEmailOwner(String emailOwner);

  long countByPlan(TenantPlan plan);
}
