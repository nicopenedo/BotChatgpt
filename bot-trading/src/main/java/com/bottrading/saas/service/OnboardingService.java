package com.bottrading.saas.service;

import com.bottrading.saas.config.SaasProperties;
import com.bottrading.saas.model.dto.SignupRequest;
import com.bottrading.saas.model.entity.TenantBillingEntity;
import com.bottrading.saas.model.entity.TenantEntity;
import com.bottrading.saas.model.entity.TenantLimitsEntity;
import com.bottrading.saas.model.entity.TenantPlan;
import com.bottrading.saas.model.entity.TenantSettingsEntity;
import com.bottrading.saas.model.entity.TenantStatus;
import com.bottrading.saas.model.entity.TenantUserEntity;
import com.bottrading.saas.model.entity.TenantUserRole;
import com.bottrading.saas.model.entity.TermsAcceptanceEntity;
import com.bottrading.saas.repository.TenantBillingRepository;
import com.bottrading.saas.repository.TenantLimitsRepository;
import com.bottrading.saas.repository.TenantRepository;
import com.bottrading.saas.repository.TenantSettingsRepository;
import com.bottrading.saas.repository.TenantUserRepository;
import com.bottrading.saas.repository.TermsAcceptanceRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {

  private final TenantRepository tenantRepository;
  private final TenantUserRepository tenantUserRepository;
  private final TenantSettingsRepository tenantSettingsRepository;
  private final TenantLimitsRepository tenantLimitsRepository;
  private final TermsAcceptanceRepository termsAcceptanceRepository;
  private final TenantBillingRepository tenantBillingRepository;
  private final PasswordEncoder passwordEncoder;
  private final SaasProperties saasProperties;
  private final AuditService auditService;

  public OnboardingService(
      TenantRepository tenantRepository,
      TenantUserRepository tenantUserRepository,
      TenantSettingsRepository tenantSettingsRepository,
      TenantLimitsRepository tenantLimitsRepository,
      TermsAcceptanceRepository termsAcceptanceRepository,
      TenantBillingRepository tenantBillingRepository,
      PasswordEncoder passwordEncoder,
      SaasProperties saasProperties,
      AuditService auditService) {
    this.tenantRepository = tenantRepository;
    this.tenantUserRepository = tenantUserRepository;
    this.tenantSettingsRepository = tenantSettingsRepository;
    this.tenantLimitsRepository = tenantLimitsRepository;
    this.termsAcceptanceRepository = termsAcceptanceRepository;
    this.tenantBillingRepository = tenantBillingRepository;
    this.passwordEncoder = passwordEncoder;
    this.saasProperties = saasProperties;
    this.auditService = auditService;
  }

  @Transactional
  public UUID signup(SignupRequest request) {
    if (!saasProperties.getLegal().getTermsVersion().equals(request.getTermsVersion())) {
      throw new IllegalArgumentException("Terms version mismatch");
    }
    TenantEntity tenant = new TenantEntity();
    tenant.setName(request.getTenantName());
    tenant.setEmailOwner(request.getEmail());
    tenant.setPlan(request.getPlan());
    tenant.setStatus(TenantStatus.PENDING);
    Instant now = Instant.now();
    tenant.setCreatedAt(now);
    tenant.setUpdatedAt(now);
    tenant = tenantRepository.save(tenant);

    TenantUserEntity owner = new TenantUserEntity();
    owner.setTenant(tenant);
    owner.setEmail(request.getEmail());
    owner.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    owner.setRole(TenantUserRole.OWNER);
    owner.setMfaEnabled(false);
    owner.setCreatedAt(now);
    owner.setUpdatedAt(now);
    owner = tenantUserRepository.save(owner);

    TenantSettingsEntity settings = new TenantSettingsEntity();
    settings.setTenantId(tenant.getId());
    settings.setRiskJson("{}");
    settings.setRouterJson("{}");
    settings.setBanditJson("{}");
    settings.setExecJson("{}");
    settings.setThrottleJson("{}");
    settings.setNotificationsJson("{}");
    settings.setFeatureFlagsJson("{}");
    settings.setUpdatedAt(now);
    tenantSettingsRepository.save(settings);

    TenantLimitsEntity limits = new TenantLimitsEntity();
    limits.setTenantId(tenant.getId());
    SaasProperties.Plan planConfig = getPlanConfig(request.getPlan());
    limits.setMaxBots(planConfig.getMaxBots());
    limits.setMaxSymbols(planConfig.getMaxSymbols());
    limits.setCanaryShareMax(planConfig.getCanaryShareMax());
    limits.setMaxTradesPerDay(Math.max(25, planConfig.getMaxBots() * 50));
    limits.setDataRetentionDays(planConfig.getDataRetentionDays());
    limits.setUpdatedAt(now);
    tenantLimitsRepository.save(limits);

    TermsAcceptanceEntity terms = new TermsAcceptanceEntity();
    terms.setTenantId(tenant.getId());
    terms.setUserId(owner.getId());
    terms.setVersion(request.getTermsVersion());
    terms.setAcceptedAt(now);
    terms.setIp(request.getIp());
    terms.setUa(request.getUserAgent());
    termsAcceptanceRepository.save(terms);

    TenantBillingEntity billing = new TenantBillingEntity();
    billing.setTenantId(tenant.getId());
    billing.setProvider(
        "mp".equalsIgnoreCase(saasProperties.getBilling().getProvider())
            ? TenantBillingEntity.Provider.MP
            : TenantBillingEntity.Provider.STRIPE);
    billing.setCustomerId("pending");
    billing.setPlan(request.getPlan().name());
    billing.setStatus("pending_checkout");
    billing.setHwmPnlNet(java.math.BigDecimal.ZERO);
    billing.setUpdatedAt(now);
    tenantBillingRepository.save(billing);

    auditService.record(
        tenant.getId(),
        owner.getId(),
        "tenant.signup",
        Map.of("plan", request.getPlan().name(), "terms", request.getTermsVersion()));
    return tenant.getId();
  }

  private SaasProperties.Plan getPlanConfig(TenantPlan plan) {
    return switch (plan) {
      case PRO -> saasProperties.getPlans().getPro();
      case STARTER -> saasProperties.getPlans().getStarter();
    };
  }
}
