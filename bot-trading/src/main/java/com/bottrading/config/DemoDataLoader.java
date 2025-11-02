package com.bottrading.config;

import com.bottrading.saas.model.entity.TenantEntity;
import com.bottrading.saas.model.entity.TenantPlan;
import com.bottrading.saas.model.entity.TenantStatus;
import com.bottrading.saas.model.entity.TenantUserEntity;
import com.bottrading.saas.model.entity.TenantUserRole;
import com.bottrading.saas.repository.TenantRepository;
import com.bottrading.saas.repository.TenantUserRepository;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DemoDataLoader implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

  private final TenantRepository tenantRepository;
  private final TenantUserRepository tenantUserRepository;
  private final PasswordEncoder passwordEncoder;

  public DemoDataLoader(
      TenantRepository tenantRepository,
      TenantUserRepository tenantUserRepository,
      PasswordEncoder passwordEncoder) {
    this.tenantRepository = tenantRepository;
    this.tenantUserRepository = tenantUserRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(String... args) {
    Optional<TenantUserEntity> existing = tenantUserRepository.findByEmail("demo@local");
    if (existing.isPresent()) {
      return;
    }

    TenantEntity tenant = new TenantEntity();
    tenant.setName("DEMO Capital");
    tenant.setEmailOwner("demo@local");
    tenant.setPlan(TenantPlan.STARTER);
    tenant.setStatus(TenantStatus.ACTIVE);
    tenant.setCreatedAt(Instant.now());
    tenant.setUpdatedAt(Instant.now());
    tenant = tenantRepository.save(tenant);

    TenantUserEntity user = new TenantUserEntity();
    user.setTenant(tenant);
    user.setEmail("demo@local");
    user.setPasswordHash(passwordEncoder.encode("demo123"));
    user.setRole(TenantUserRole.OWNER);
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    user.setMfaEnabled(false);
    tenantUserRepository.save(user);

    log.info("Usuario demo cargado: demo@local / demo123");
  }
}
