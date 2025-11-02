package com.bottrading.saas.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_billing")
public class TenantBillingEntity {

  public enum Provider {
    STRIPE,
    MP
  }

  public enum BillingState {
    ACTIVE,
    GRACE,
    PAST_DUE,
    DOWNGRADED
  }

  @Id
  @Column(name = "tenant_id")
  private UUID tenantId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Provider provider;

  @Column(name = "customer_id", nullable = false)
  private String customerId;

  @Column(name = "subscription_id")
  private String subscriptionId;

  @Column(nullable = false)
  private String plan;

  @Column(nullable = false, name = "provider_status")
  private String status;

  @Enumerated(EnumType.STRING)
  @Column(name = "billing_state", nullable = false)
  private BillingState billingState = BillingState.ACTIVE;

  @Column(name = "grace_until")
  private Instant graceUntil;

  @Column(name = "hwm_pnl_net", nullable = false)
  private BigDecimal hwmPnlNet;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }

  public Provider getProvider() {
    return provider;
  }

  public void setProvider(Provider provider) {
    this.provider = provider;
  }

  public String getCustomerId() {
    return customerId;
  }

  public void setCustomerId(String customerId) {
    this.customerId = customerId;
  }

  public String getSubscriptionId() {
    return subscriptionId;
  }

  public void setSubscriptionId(String subscriptionId) {
    this.subscriptionId = subscriptionId;
  }

  public String getPlan() {
    return plan;
  }

  public void setPlan(String plan) {
    this.plan = plan;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public BillingState getBillingState() {
    return billingState;
  }

  public void setBillingState(BillingState billingState) {
    this.billingState = billingState;
  }

  public Instant getGraceUntil() {
    return graceUntil;
  }

  public void setGraceUntil(Instant graceUntil) {
    this.graceUntil = graceUntil;
  }

  public BigDecimal getHwmPnlNet() {
    return hwmPnlNet;
  }

  public void setHwmPnlNet(BigDecimal hwmPnlNet) {
    this.hwmPnlNet = hwmPnlNet;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
