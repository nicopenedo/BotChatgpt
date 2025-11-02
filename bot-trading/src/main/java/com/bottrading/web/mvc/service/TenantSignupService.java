package com.bottrading.web.mvc.service;

import com.bottrading.saas.model.entity.TenantEntity;
import com.bottrading.saas.model.entity.TenantPlan;
import com.bottrading.saas.model.entity.TenantStatus;
import com.bottrading.saas.model.entity.TenantUserEntity;
import com.bottrading.saas.model.entity.TenantUserRole;
import com.bottrading.saas.repository.TenantRepository;
import com.bottrading.saas.repository.TenantUserRepository;
import com.bottrading.web.mvc.model.form.SignupForm;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantSignupService {

  private static final Logger log = LoggerFactory.getLogger(TenantSignupService.class);

  private final TenantRepository tenantRepository;
  private final TenantUserRepository tenantUserRepository;
  private final PasswordEncoder passwordEncoder;

  public TenantSignupService(
      TenantRepository tenantRepository,
      TenantUserRepository tenantUserRepository,
      PasswordEncoder passwordEncoder) {
    this.tenantRepository = tenantRepository;
    this.tenantUserRepository = tenantUserRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public void registerTenant(SignupForm form) {
    TenantEntity tenant = new TenantEntity();
    tenant.setName(form.getTenantName());
    tenant.setEmailOwner(form.getEmail());
    tenant.setPlan(form.getPlan() != null ? form.getPlan() : TenantPlan.STARTER);
    tenant.setStatus(TenantStatus.ACTIVE);
    tenant.setCreatedAt(Instant.now());
    tenant.setUpdatedAt(Instant.now());
    tenant = tenantRepository.save(tenant);

    TenantUserEntity user = new TenantUserEntity();
    user.setTenant(tenant);
    user.setEmail(form.getEmail());
    user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
    user.setRole(TenantUserRole.OWNER);
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    user.setMfaEnabled(false);
    tenantUserRepository.save(user);

    log.info("Demo tenant {} registrado para {}", tenant.getId(), form.getEmail());
  }
}
