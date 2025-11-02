package com.bottrading.saas.repository;

import com.bottrading.saas.model.entity.TenantSettingsEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSettingsRepository extends JpaRepository<TenantSettingsEntity, UUID> {}
