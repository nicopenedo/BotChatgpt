package com.bottrading.saas.repository;

import com.bottrading.saas.model.entity.TenantBillingEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantBillingRepository extends JpaRepository<TenantBillingEntity, UUID> {}
