package com.bottrading.config.props;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "observability.prometheus")
@Validated
public class ObservabilityPrometheusProps {

  private Boolean enabled;
  private boolean enabledConfigured;
  private List<String> allowlistCidrs;
  private boolean allowlistConfigured;
  private List<String> trustedProxiesCidrs;
  private boolean trustedProxiesConfigured;
  private String token;
  private boolean tokenConfigured;

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
    this.enabledConfigured = true;
  }

  public boolean isEnabledConfigured() {
    return enabledConfigured;
  }

  public List<String> getAllowlistCidrs() {
    return allowlistCidrs;
  }

  public void setAllowlistCidrs(List<String> allowlistCidrs) {
    this.allowlistCidrs = allowlistCidrs;
    this.allowlistConfigured = true;
  }

  public boolean isAllowlistConfigured() {
    return allowlistConfigured;
  }

  public List<String> getTrustedProxiesCidrs() {
    return trustedProxiesCidrs;
  }

  public void setTrustedProxiesCidrs(List<String> trustedProxiesCidrs) {
    this.trustedProxiesCidrs = trustedProxiesCidrs;
    this.trustedProxiesConfigured = true;
  }

  public boolean isTrustedProxiesConfigured() {
    return trustedProxiesConfigured;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
    this.tokenConfigured = true;
  }

  public boolean isTokenConfigured() {
    return tokenConfigured;
  }
}
