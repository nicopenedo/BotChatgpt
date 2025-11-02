package com.bottrading.saas.repository;

import com.bottrading.saas.model.entity.TenantExportTokenEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantExportTokenRepository
    extends JpaRepository<TenantExportTokenEntity, UUID> {

  Optional<TenantExportTokenEntity> findByToken(String token);

  @Modifying
  @Query(
      "delete from TenantExportTokenEntity t where t.expiresAt < :now or t.downloadedAt is not null")
  void purgeExpired(@Param("now") Instant now);
}
