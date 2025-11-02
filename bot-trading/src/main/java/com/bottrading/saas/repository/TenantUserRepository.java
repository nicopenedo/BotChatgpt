package com.bottrading.saas.repository;

import com.bottrading.saas.model.entity.TenantUserEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantUserRepository extends JpaRepository<TenantUserEntity, UUID> {
  Optional<TenantUserEntity> findByTenantIdAndEmail(UUID tenantId, String email);

  List<TenantUserEntity> findByTenantId(UUID tenantId);

  Optional<TenantUserEntity> findByEmail(String email);

  void deleteByTenantId(UUID tenantId);
}
