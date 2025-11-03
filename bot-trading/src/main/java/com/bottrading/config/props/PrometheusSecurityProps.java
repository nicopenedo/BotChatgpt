package com.bottrading.config.props;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "prometheus")
public class PrometheusSecurityProps {

  private static final List<String> DEFAULT_ALLOWLIST = List.of("127.0.0.1/32");

  private List<String> allowlist = new ArrayList<>(DEFAULT_ALLOWLIST);
  private String token = "";

  public List<String> getAllowlist() {
    return Collections.unmodifiableList(allowlist);
  }

  public void setAllowlist(List<String> allowlist) {
    if (allowlist == null) {
      this.allowlist = new ArrayList<>(DEFAULT_ALLOWLIST);
      return;
    }
    List<String> sanitized = new ArrayList<>();
    for (String cidr : allowlist) {
      if (StringUtils.hasText(cidr)) {
        sanitized.add(cidr.trim());
      }
    }
    this.allowlist = sanitized;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token == null ? "" : token.trim();
  }
}
