package com.bottrading.saas.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_settings")
public class TenantSettingsEntity {

  @Id
  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "risk_json", columnDefinition = "jsonb")
  private String riskJson;

  @Column(name = "router_json", columnDefinition = "jsonb")
  private String routerJson;

  @Column(name = "bandit_json", columnDefinition = "jsonb")
  private String banditJson;

  @Column(name = "exec_json", columnDefinition = "jsonb")
  private String execJson;

  @Column(name = "throttle_json", columnDefinition = "jsonb")
  private String throttleJson;

  @Column(name = "notifications_json", columnDefinition = "jsonb")
  private String notificationsJson;

  @Column(name = "feature_flags_json", columnDefinition = "jsonb")
  private String featureFlagsJson;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "trading_paused", nullable = false)
  private boolean tradingPaused = false;

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }

  public String getRiskJson() {
    return riskJson;
  }

  public void setRiskJson(String riskJson) {
    this.riskJson = riskJson;
  }

  public String getRouterJson() {
    return routerJson;
  }

  public void setRouterJson(String routerJson) {
    this.routerJson = routerJson;
  }

  public String getBanditJson() {
    return banditJson;
  }

  public void setBanditJson(String banditJson) {
    this.banditJson = banditJson;
  }

  public String getExecJson() {
    return execJson;
  }

  public void setExecJson(String execJson) {
    this.execJson = execJson;
  }

  public String getThrottleJson() {
    return throttleJson;
  }

  public void setThrottleJson(String throttleJson) {
    this.throttleJson = throttleJson;
  }

  public String getNotificationsJson() {
    return notificationsJson;
  }

  public void setNotificationsJson(String notificationsJson) {
    this.notificationsJson = notificationsJson;
  }

  public String getFeatureFlagsJson() {
    return featureFlagsJson;
  }

  public void setFeatureFlagsJson(String featureFlagsJson) {
    this.featureFlagsJson = featureFlagsJson;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public boolean isTradingPaused() {
    return tradingPaused;
  }

  public void setTradingPaused(boolean tradingPaused) {
    this.tradingPaused = tradingPaused;
  }
}
