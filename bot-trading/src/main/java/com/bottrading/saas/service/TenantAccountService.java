package com.bottrading.saas.service;

import com.bottrading.saas.config.SaasProperties;
import com.bottrading.saas.model.entity.TenantBillingEntity;
import com.bottrading.saas.model.entity.TenantEntity;
import com.bottrading.saas.model.entity.TenantExportTokenEntity;
import com.bottrading.saas.model.entity.TenantSettingsEntity;
import com.bottrading.saas.model.entity.TenantStatus;
import com.bottrading.saas.repository.TenantBillingRepository;
import com.bottrading.saas.repository.TenantExportTokenRepository;
import com.bottrading.saas.repository.TenantLimitsRepository;
import com.bottrading.saas.repository.TenantRepository;
import com.bottrading.saas.repository.TenantSettingsRepository;
import com.bottrading.saas.repository.TenantUserRepository;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class TenantAccountService {

  private final TenantRepository tenantRepository;
  private final TenantUserRepository tenantUserRepository;
  private final TenantSettingsRepository tenantSettingsRepository;
  private final TenantExportTokenRepository exportTokenRepository;
  private final TenantBillingRepository billingRepository;
  private final TenantLimitsRepository tenantLimitsRepository;
  private final PasswordEncoder passwordEncoder;
  private final TotpService totpService;
  private final SaasProperties properties;
  private final AuditService auditService;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  public TenantAccountService(
      TenantRepository tenantRepository,
      TenantUserRepository tenantUserRepository,
      TenantSettingsRepository tenantSettingsRepository,
      TenantExportTokenRepository exportTokenRepository,
      TenantBillingRepository billingRepository,
      TenantLimitsRepository tenantLimitsRepository,
      PasswordEncoder passwordEncoder,
      TotpService totpService,
      SaasProperties properties,
      AuditService auditService) {
    this(
        tenantRepository,
        tenantUserRepository,
        tenantSettingsRepository,
        exportTokenRepository,
        billingRepository,
        tenantLimitsRepository,
        passwordEncoder,
        totpService,
        properties,
        auditService,
        Clock.systemUTC());
  }

  TenantAccountService(
      TenantRepository tenantRepository,
      TenantUserRepository tenantUserRepository,
      TenantSettingsRepository tenantSettingsRepository,
      TenantExportTokenRepository exportTokenRepository,
      TenantBillingRepository billingRepository,
      TenantLimitsRepository tenantLimitsRepository,
      PasswordEncoder passwordEncoder,
      TotpService totpService,
      SaasProperties properties,
      AuditService auditService,
      Clock clock) {
    this.tenantRepository = tenantRepository;
    this.tenantUserRepository = tenantUserRepository;
    this.tenantSettingsRepository = tenantSettingsRepository;
    this.exportTokenRepository = exportTokenRepository;
    this.billingRepository = billingRepository;
    this.tenantLimitsRepository = tenantLimitsRepository;
    this.passwordEncoder = passwordEncoder;
    this.totpService = totpService;
    this.properties = properties;
    this.auditService = auditService;
    this.clock = clock;
  }

  @Transactional
  public void requestDeletion(UUID tenantId, UUID userId, String password, String totp) {
    var user =
        tenantUserRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new AccessDeniedException("Contraseña inválida");
    }
    if (user.isMfaEnabled()) {
      if (!totpService.verify(totpService.fromBase32(user.getMfaSecret()), totp)) {
        throw new AccessDeniedException("Código TOTP inválido");
      }
    }
    TenantEntity tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado"));
    Instant now = Instant.now(clock);
    int minHours = Math.max(1, properties.getLegal().getDeletionMinHours());
    int maxHours = Math.max(minHours, properties.getLegal().getDeletionMaxHours());
    int delay = minHours;
    if (maxHours > minHours) {
      delay = minHours + secureRandom.nextInt(maxHours - minHours + 1);
    }
    tenant.setDeletionRequestedAt(now);
    tenant.setPurgeAfter(now.plus(delay, ChronoUnit.HOURS));
    tenant.setStatus(TenantStatus.DELETION_PENDING);
    tenantRepository.save(tenant);
    auditService.record(
        tenantId,
        userId,
        "tenant.account.deletion.requested",
        java.util.Map.of("purgeAfter", tenant.getPurgeAfter()));
  }

  @Transactional
  public String createExportToken(UUID tenantId, UUID userId) {
    Instant now = Instant.now(clock);
    exportTokenRepository.purgeExpired(now);
    int ttlMinutes = Math.max(1, properties.getLegal().getExportTokenTtlMinutes());
    String token = DigestUtils.sha256Hex(UUID.randomUUID().toString() + now.toEpochMilli());
    TenantExportTokenEntity entity = new TenantExportTokenEntity();
    entity.setTenantId(tenantId);
    entity.setUserId(userId);
    entity.setToken(token);
    entity.setCreatedAt(now);
    entity.setExpiresAt(now.plus(ttlMinutes, ChronoUnit.MINUTES));
    exportTokenRepository.save(entity);
    auditService.record(
        tenantId,
        userId,
        "tenant.account.export.created",
        java.util.Map.of("expiresAt", entity.getExpiresAt()));
    return token;
  }

  @Transactional
  public Optional<TenantExportTokenEntity> consumeExportToken(String token) {
    exportTokenRepository.purgeExpired(Instant.now(clock));
    Optional<TenantExportTokenEntity> entity = exportTokenRepository.findByToken(token);
    entity.ifPresent(
        value -> {
          value.setDownloadedAt(Instant.now(clock));
          exportTokenRepository.save(value);
          auditService.record(
              value.getTenantId(),
              value.getUserId(),
              "tenant.account.export.downloaded",
              java.util.Map.of("token", token));
        });
    return entity;
  }

  @Transactional
  public void purgeExpiredTenants() {
    Instant now = Instant.now(clock);
    List<TenantEntity> tenants = tenantRepository.findAll();
    tenants.stream()
        .filter(t -> t.getPurgeAfter() != null && t.getPurgeAfter().isBefore(now))
        .forEach(this::purgeTenant);
  }

  private void purgeTenant(TenantEntity tenant) {
    UUID tenantId = tenant.getId();
    tenantUserRepository.deleteByTenantId(tenantId);
    tenantSettingsRepository.deleteById(tenantId);
    tenantLimitsRepository.deleteById(tenantId);
    billingRepository.deleteById(tenantId);
    exportTokenRepository
        .findAll()
        .stream()
        .filter(token -> tenantId.equals(token.getTenantId()))
        .forEach(exportTokenRepository::delete);
    tenant.setDeletedAt(Instant.now(clock));
    tenant.setStatus(TenantStatus.DELETED);
    tenantRepository.save(tenant);
    tenantRepository.delete(tenant);
    auditService.record(
        tenantId,
        null,
        "tenant.account.deletion.completed",
        java.util.Map.of("deletedAt", tenant.getDeletedAt()));
  }

  @Transactional
  public void updatePause(UUID tenantId, boolean paused, UUID userId) {
    TenantSettingsEntity settings =
        tenantSettingsRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant settings not found"));
    settings.setTradingPaused(paused);
    tenantSettingsRepository.save(settings);
    auditService.record(
        tenantId,
        userId,
        paused ? "tenant.trading.paused" : "tenant.trading.resumed",
        java.util.Map.of());
  }

  public boolean isTradingPaused(UUID tenantId) {
    return tenantSettingsRepository
        .findById(tenantId)
        .map(TenantSettingsEntity::isTradingPaused)
        .orElse(false);
  }

  public Optional<TenantBillingEntity> findBilling(UUID tenantId) {
    return billingRepository.findById(tenantId);
  }
}
