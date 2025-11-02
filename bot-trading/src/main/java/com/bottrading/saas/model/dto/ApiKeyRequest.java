package com.bottrading.saas.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class ApiKeyRequest {
  @NotBlank private String exchange;
  @NotBlank private String apiKey;
  @NotBlank private String secret;
  private String label;
  private List<String> ipWhitelist;
  @NotNull private Boolean canWithdraw;

  public String getExchange() {
    return exchange;
  }

  public void setExchange(String exchange) {
    this.exchange = exchange;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public List<String> getIpWhitelist() {
    return ipWhitelist;
  }

  public void setIpWhitelist(List<String> ipWhitelist) {
    this.ipWhitelist = ipWhitelist;
  }

  public Boolean getCanWithdraw() {
    return canWithdraw;
  }

  public void setCanWithdraw(Boolean canWithdraw) {
    this.canWithdraw = canWithdraw;
  }
}
