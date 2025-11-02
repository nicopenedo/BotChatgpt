package com.bottrading.saas.service;

import com.bottrading.saas.model.entity.ReferralEntity;
import com.bottrading.saas.repository.ReferralRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReferralService {

  private final ReferralRepository referralRepository;
  private final AuditService auditService;

  public ReferralService(ReferralRepository referralRepository, AuditService auditService) {
    this.referralRepository = referralRepository;
    this.auditService = auditService;
  }

  public UUID registerReferral(UUID referrerTenantId, UUID referredTenantId) {
    ReferralEntity entity = new ReferralEntity();
    entity.setReferrerTenantId(referrerTenantId);
    entity.setReferredTenantId(referredTenantId);
    entity.setRewardState("pending");
    entity.setCreatedAt(Instant.now());
    ReferralEntity saved = referralRepository.save(entity);
    auditService.record(
        referrerTenantId,
        null,
        "referral.created",
        java.util.Map.of("referred", referredTenantId));
    return saved.getId();
  }

  public void grantReward(UUID referralId) {
    Optional<ReferralEntity> referral = referralRepository.findById(referralId);
    referral.ifPresent(
        entity -> {
          entity.setRewardState("granted");
          referralRepository.save(entity);
          auditService.record(
              entity.getReferrerTenantId(),
              null,
              "referral.granted",
              java.util.Map.of("referred", entity.getReferredTenantId()));
        });
  }
}
