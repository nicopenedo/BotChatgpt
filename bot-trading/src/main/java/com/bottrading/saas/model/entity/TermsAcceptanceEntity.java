package com.bottrading.saas.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "terms_acceptance")
public class TermsAcceptanceEntity {

  @Id @GeneratedValue private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "terms_version_hash", nullable = false)
  private String termsVersionHash;

  @Column(name = "risk_version_hash", nullable = false)
  private String riskVersionHash;

  @Column(name = "consented_at", nullable = false)
  private Instant consentedAt;

  private String ip;

  private String ua;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getTermsVersionHash() {
    return termsVersionHash;
  }

  public void setTermsVersionHash(String termsVersionHash) {
    this.termsVersionHash = termsVersionHash;
  }

  public String getRiskVersionHash() {
    return riskVersionHash;
  }

  public void setRiskVersionHash(String riskVersionHash) {
    this.riskVersionHash = riskVersionHash;
  }

  public Instant getConsentedAt() {
    return consentedAt;
  }

  public void setConsentedAt(Instant consentedAt) {
    this.consentedAt = consentedAt;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public String getUa() {
    return ua;
  }

  public void setUa(String ua) {
    this.ua = ua;
  }
}
