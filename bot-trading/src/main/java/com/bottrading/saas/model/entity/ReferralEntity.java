package com.bottrading.saas.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "referral")
public class ReferralEntity {

  @Id @GeneratedValue private UUID id;

  @Column(name = "referrer_tenant_id")
  private UUID referrerTenantId;

  @Column(name = "referred_tenant_id")
  private UUID referredTenantId;

  @Column(name = "reward_state", nullable = false)
  private String rewardState;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getReferrerTenantId() {
    return referrerTenantId;
  }

  public void setReferrerTenantId(UUID referrerTenantId) {
    this.referrerTenantId = referrerTenantId;
  }

  public UUID getReferredTenantId() {
    return referredTenantId;
  }

  public void setReferredTenantId(UUID referredTenantId) {
    this.referredTenantId = referredTenantId;
  }

  public String getRewardState() {
    return rewardState;
  }

  public void setRewardState(String rewardState) {
    this.rewardState = rewardState;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
