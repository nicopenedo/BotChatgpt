package com.bottrading.saas.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_api_key")
public class TenantApiKeyEntity {

  @Id @GeneratedValue private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false)
  private String exchange;

  private String label;

  @Column(name = "enc_api_key", nullable = false)
  private byte[] encryptedApiKey;

  @Column(name = "enc_secret", nullable = false)
  private byte[] encryptedSecret;

  @Column(name = "ip_whitelist", columnDefinition = "text[]")
  private String[] ipWhitelist;

  @Column(name = "can_withdraw", nullable = false)
  private boolean canWithdraw;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "rotated_at")
  private Instant rotatedAt;

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

  public String getExchange() {
    return exchange;
  }

  public void setExchange(String exchange) {
    this.exchange = exchange;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public byte[] getEncryptedApiKey() {
    return encryptedApiKey;
  }

  public void setEncryptedApiKey(byte[] encryptedApiKey) {
    this.encryptedApiKey = encryptedApiKey;
  }

  public byte[] getEncryptedSecret() {
    return encryptedSecret;
  }

  public void setEncryptedSecret(byte[] encryptedSecret) {
    this.encryptedSecret = encryptedSecret;
  }

  public boolean isCanWithdraw() {
    return canWithdraw;
  }

  public void setCanWithdraw(boolean canWithdraw) {
    this.canWithdraw = canWithdraw;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getRotatedAt() {
    return rotatedAt;
  }

  public void setRotatedAt(Instant rotatedAt) {
    this.rotatedAt = rotatedAt;
  }

  public String[] getIpWhitelist() {
    return ipWhitelist;
  }

  public void setIpWhitelist(String[] ipWhitelist) {
    this.ipWhitelist = ipWhitelist;
  }
}
