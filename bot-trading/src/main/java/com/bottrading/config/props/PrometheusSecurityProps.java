package com.bottrading.config.props;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "observability.prometheus")
@Validated
public record PrometheusSecurityProps(
    @NotNull boolean enabled,
    List<String> allowlistCidrs,
    List<String> trustedProxiesCidrs,
    String token) {

  private static final List<String> EMPTY = List.of();

  public PrometheusSecurityProps {
    allowlistCidrs = sanitizeList(allowlistCidrs);
    trustedProxiesCidrs = sanitizeList(trustedProxiesCidrs);
    token = token == null ? "" : token.trim();
  }

  private static List<String> sanitizeList(List<String> cidrs) {
    if (cidrs == null || cidrs.isEmpty()) {
      return EMPTY;
    }
    List<String> sanitized = new ArrayList<>();
    for (String entry : cidrs) {
      if (!StringUtils.hasText(entry)) {
        continue;
      }
      for (String candidate : entry.split(",")) {
        if (StringUtils.hasText(candidate)) {
          sanitized.add(candidate.trim());
        }
      }
    }
    return List.copyOf(sanitized);
  }
}
