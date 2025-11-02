package com.bottrading.config;

import com.bottrading.saas.model.entity.TenantEntity;
import com.bottrading.saas.model.entity.TenantPlan;
import com.bottrading.saas.model.entity.TenantUserEntity;
import com.bottrading.saas.model.entity.TenantUserRole;
import com.bottrading.saas.security.TenantUserDetails;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

final class TestTenantUsers {

  private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

  private TestTenantUsers() {}

  static TenantUserDetails tenantUser(String email, boolean mfaEnabled) {
    TenantEntity tenant = new TenantEntity();
    tenant.setId(UUID.randomUUID());
    tenant.setName("Test Tenant");
    tenant.setEmailOwner("owner@demo.local");
    tenant.setPlan(TenantPlan.STARTER);
    tenant.setCreatedAt(Instant.now());
    tenant.setUpdatedAt(Instant.now());

    TenantUserEntity user = new TenantUserEntity();
    user.setId(UUID.randomUUID());
    user.setTenant(tenant);
    user.setEmail(email);
    user.setPasswordHash(ENCODER.encode("password"));
    user.setRole(TenantUserRole.ADMIN);
    user.setMfaEnabled(mfaEnabled);
    user.setMfaSecret("JBSWY3DPEHPK3PXP");
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    return new TenantUserDetails(user);
  }
}
