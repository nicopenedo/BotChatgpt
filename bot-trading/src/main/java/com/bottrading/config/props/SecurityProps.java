package com.bottrading.config.props;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
public class SecurityProps {

  @NotBlank private String secret = "change_me_dev_only";

  @NotBlank private String totpIssuer = "BotTradingSaaS";

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public String getTotpIssuer() {
    return totpIssuer;
  }

  public void setTotpIssuer(String totpIssuer) {
    this.totpIssuer = totpIssuer;
  }
}
