package com.bottrading.saas.repository;

import com.bottrading.saas.model.entity.TenantLimitsEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantLimitsRepository extends JpaRepository<TenantLimitsEntity, UUID> {}
