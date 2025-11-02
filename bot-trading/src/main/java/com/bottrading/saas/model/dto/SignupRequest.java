package com.bottrading.saas.model.dto;

import com.bottrading.saas.model.entity.TenantPlan;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SignupRequest {
  @NotBlank private String tenantName;
  @Email private String email;
  @NotBlank private String password;
  @NotNull private TenantPlan plan;
  @NotBlank private String termsVersion;
  @NotBlank private String riskVersion;
  @NotBlank private String ip;
  private String userAgent;

  public String getTenantName() {
    return tenantName;
  }

  public void setTenantName(String tenantName) {
    this.tenantName = tenantName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public TenantPlan getPlan() {
    return plan;
  }

  public void setPlan(TenantPlan plan) {
    this.plan = plan;
  }

  public String getTermsVersion() {
    return termsVersion;
  }

  public void setTermsVersion(String termsVersion) {
    this.termsVersion = termsVersion;
  }

  public String getRiskVersion() {
    return riskVersion;
  }

  public void setRiskVersion(String riskVersion) {
    this.riskVersion = riskVersion;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }
}
