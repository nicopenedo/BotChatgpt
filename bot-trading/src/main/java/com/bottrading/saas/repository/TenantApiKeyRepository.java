package com.bottrading.saas.repository;

import com.bottrading.saas.model.entity.TenantApiKeyEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantApiKeyRepository extends JpaRepository<TenantApiKeyEntity, UUID> {
  List<TenantApiKeyEntity> findByTenantId(UUID tenantId);

  Optional<TenantApiKeyEntity> findByTenantIdAndExchange(UUID tenantId, String exchange);
}
