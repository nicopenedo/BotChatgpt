package com.bottrading.saas.service;

import com.bottrading.saas.config.SaasProperties;
import com.bottrading.saas.model.dto.ApiKeyRequest;
import com.bottrading.saas.model.entity.TenantApiKeyEntity;
import com.bottrading.saas.repository.TenantApiKeyRepository;
import com.bottrading.saas.security.TenantUserDetails;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantSecurityService {

  private final SaasProperties saasProperties;
  private final TenantApiKeyRepository apiKeyRepository;
  private final SecretEncryptionService encryptionService;
  private final AuditService auditService;
  private final TotpService totpService;

  public TenantSecurityService(
      SaasProperties saasProperties,
      TenantApiKeyRepository apiKeyRepository,
      SecretEncryptionService encryptionService,
      AuditService auditService,
      TotpService totpService) {
    this.saasProperties = saasProperties;
    this.apiKeyRepository = apiKeyRepository;
    this.encryptionService = encryptionService;
    this.auditService = auditService;
    this.totpService = totpService;
  }

  @Transactional
  public TenantApiKeyEntity storeApiKey(UUID tenantId, UUID userId, ApiKeyRequest request) {
    Objects.requireNonNull(request, "request");
    if (Boolean.TRUE.equals(request.getCanWithdraw())
        && saasProperties.getSecurity().isEnforceNoWithdrawal()) {
      throw new IllegalArgumentException("API key cannot have withdrawal permissions");
    }
    List<String> whitelist = request.getIpWhitelist();
    if (saasProperties.getSecurity().isRequireIpAllowlist()
        && (whitelist == null || whitelist.isEmpty())) {
      throw new IllegalArgumentException("IP allowlist required");
    }
    TenantApiKeyEntity entity =
        apiKeyRepository.findByTenantIdAndExchange(tenantId, request.getExchange()).orElseGet(TenantApiKeyEntity::new);
    entity.setTenantId(tenantId);
    entity.setExchange(request.getExchange());
    entity.setLabel(StringUtils.trimToNull(request.getLabel()));
    entity.setEncryptedApiKey(encryptionService.encrypt(request.getApiKey()));
    entity.setEncryptedSecret(encryptionService.encrypt(request.getSecret()));
    entity.setCanWithdraw(Boolean.TRUE.equals(request.getCanWithdraw()));
    if (whitelist != null) {
      entity.setIpWhitelist(whitelist.toArray(new String[0]));
    }
    if (entity.getCreatedAt() == null) {
      entity.setCreatedAt(Instant.now());
    } else {
      entity.setRotatedAt(Instant.now());
    }
    TenantApiKeyEntity saved = apiKeyRepository.save(entity);
    auditService.record(
        tenantId,
        userId,
        "tenant.api-key.updated",
        java.util.Map.of("exchange", request.getExchange(), "label", request.getLabel()));
    return saved;
  }

  public boolean verifyTotp(TenantUserDetails user, String totp) {
    if (user == null || !user.isMfaEnabled()) {
      return true;
    }
    return totpService.verify(user.getMfaSecret(), totp);
  }
}
