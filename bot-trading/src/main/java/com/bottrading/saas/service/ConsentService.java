package com.bottrading.saas.service;

import com.bottrading.saas.config.SaasProperties;
import com.bottrading.saas.model.entity.TermsAcceptanceEntity;
import com.bottrading.saas.repository.TermsAcceptanceRepository;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConsentService {

  private final TermsAcceptanceRepository termsAcceptanceRepository;
  private final SaasProperties properties;
  private final AuditService auditService;
  private final Clock clock;

  public ConsentService(
      TermsAcceptanceRepository termsAcceptanceRepository,
      SaasProperties properties,
      AuditService auditService) {
    this(termsAcceptanceRepository, properties, auditService, Clock.systemUTC());
  }

  ConsentService(
      TermsAcceptanceRepository termsAcceptanceRepository,
      SaasProperties properties,
      AuditService auditService,
      Clock clock) {
    this.termsAcceptanceRepository = termsAcceptanceRepository;
    this.properties = properties;
    this.auditService = auditService;
    this.clock = clock;
  }

  public boolean hasCurrentConsent(UUID tenantId) {
    String requiredTerms = properties.getLegal().getTermsVersion();
    String requiredRisk = properties.getLegal().getRiskVersion();
    List<TermsAcceptanceEntity> acceptances =
        termsAcceptanceRepository.findByTenantIdOrderByConsentedAtDesc(tenantId);
    return acceptances.stream()
        .filter(entity -> entity.getConsentedAt() != null)
        .anyMatch(
            entity ->
                requiredTerms.equals(entity.getTermsVersionHash())
                    && requiredRisk.equals(entity.getRiskVersionHash()));
  }

  @Transactional
  public TermsAcceptanceEntity recordConsent(
      UUID tenantId, UUID userId, String termsHash, String riskHash, String ip, String ua) {
    if (termsHash == null || riskHash == null) {
      throw new IllegalArgumentException("Faltan versiones de términos o riesgos");
    }
    TermsAcceptanceEntity entity = new TermsAcceptanceEntity();
    entity.setTenantId(tenantId);
    entity.setUserId(userId);
    entity.setTermsVersionHash(termsHash);
    entity.setRiskVersionHash(riskHash);
    entity.setConsentedAt(Instant.now(clock));
    entity.setIp(ip);
    entity.setUa(ua);
    TermsAcceptanceEntity saved = termsAcceptanceRepository.save(entity);
    auditService.record(
        tenantId,
        userId,
        "tenant.consent.recorded",
        Map.of("terms", termsHash, "risk", riskHash));
    return saved;
  }

  public Optional<TermsAcceptanceEntity> latest(UUID tenantId) {
    return termsAcceptanceRepository
        .findByTenantIdOrderByConsentedAtDesc(tenantId)
        .stream()
        .findFirst();
  }
}
