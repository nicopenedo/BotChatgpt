package com.bottrading.web.mvc.model.form;

import jakarta.validation.constraints.NotBlank;

public class OnboardingApiKeysForm {

  @NotBlank(message = "Ingresá tu API key")
  private String apiKey;

  @NotBlank(message = "Ingresá tu API secret")
  private String apiSecret;

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getApiSecret() {
    return apiSecret;
  }

  public void setApiSecret(String apiSecret) {
    this.apiSecret = apiSecret;
  }
}
