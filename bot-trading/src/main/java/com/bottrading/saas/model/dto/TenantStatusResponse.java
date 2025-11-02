package com.bottrading.saas.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public class TenantStatusResponse {
  private String plan;
  private String status;
  private Instant createdAt;
  private Map<String, Object> kpis;
  private Map<String, Boolean> featureFlags;

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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Map<String, Object> getKpis() {
    return kpis;
  }

  public void setKpis(Map<String, Object> kpis) {
    this.kpis = kpis;
  }

  public Map<String, Boolean> getFeatureFlags() {
    return featureFlags;
  }

  public void setFeatureFlags(Map<String, Boolean> featureFlags) {
    this.featureFlags = featureFlags;
  }
}
