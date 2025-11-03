package com.bottrading.config.props;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties({ObservabilityPrometheusProps.class, LegacyPrometheusProps.class})
public class PrometheusSecurityPropsConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(PrometheusSecurityPropsConfiguration.class);

  private final WebEndpointProperties webEndpointProperties;

  public PrometheusSecurityPropsConfiguration(WebEndpointProperties webEndpointProperties) {
    this.webEndpointProperties = webEndpointProperties;
  }

  @Bean
  public PrometheusSecurityProps prometheusSecurityProps(
      ObservabilityPrometheusProps observabilityProps, LegacyPrometheusProps legacyProps) {
    MergeResult merge = merge(observabilityProps, legacyProps);
    PrometheusSecurityProps props =
        new PrometheusSecurityProps(
            merge.enabled(), merge.allowlist(), merge.trustedProxies(), merge.token());

    if (merge.legacyApplied()) {
      log.info(
          "Detectada configuración legacy prometheus.*; se recomienda migrar a observability.prometheus.*");
    }

    if (isPrometheusEndpointExposed() && !props.hasSecurityPolicies()) {
      log.warn(
          "Prometheus expuesto pero sin token ni allowlist. Acceso denegado por fail-safe; configure observability.prometheus.token o allowlist-cidrs.");
    }

    return props;
  }

  private MergeResult merge(
      ObservabilityPrometheusProps observabilityProps, LegacyPrometheusProps legacyProps) {
    boolean legacyApplied = false;

    Boolean enabled = null;
    if (observabilityProps.isEnabledConfigured()) {
      enabled = observabilityProps.getEnabled();
    } else if (legacyProps.isEnabledConfigured()) {
      enabled = legacyProps.getEnabled();
      legacyApplied = true;
    }

    ListHolder allowlistHolder =
        selectList(
            observabilityProps.isAllowlistConfigured(),
            observabilityProps.getAllowlistCidrs(),
            legacyProps.isAllowlistConfigured(),
            legacyProps.getAllowlistCidrs());
    if (allowlistHolder.usingLegacy()) {
      legacyApplied = true;
    }

    ListHolder trustedProxiesHolder =
        selectList(
            observabilityProps.isTrustedProxiesConfigured(),
            observabilityProps.getTrustedProxiesCidrs(),
            legacyProps.isTrustedProxiesConfigured(),
            legacyProps.getTrustedProxiesCidrs());
    if (trustedProxiesHolder.usingLegacy()) {
      legacyApplied = true;
    }

    ValueHolder tokenHolder =
        selectValue(
            observabilityProps.isTokenConfigured(),
            observabilityProps.getToken(),
            legacyProps.isTokenConfigured(),
            legacyProps.getToken());
    if (tokenHolder.usingLegacy()) {
      legacyApplied = true;
    }

    return new MergeResult(
        enabled,
        PrometheusSecurityProps.sanitizeList(allowlistHolder.value()),
        PrometheusSecurityProps.sanitizeList(trustedProxiesHolder.value()),
        tokenHolder.value(),
        legacyApplied);
  }

  private ListHolder selectList(
      boolean primaryConfigured,
      List<String> primary,
      boolean legacyConfigured,
      List<String> legacy) {
    if (primaryConfigured) {
      return new ListHolder(primary, false);
    }
    if (legacyConfigured) {
      return new ListHolder(legacy, true);
    }
    return new ListHolder(null, false);
  }

  private ValueHolder selectValue(
      boolean primaryConfigured, String primary, boolean legacyConfigured, String legacy) {
    if (primaryConfigured) {
      return new ValueHolder(primary, false);
    }
    if (legacyConfigured) {
      return new ValueHolder(legacy, true);
    }
    return new ValueHolder(null, false);
  }

  private boolean isPrometheusEndpointExposed() {
    WebEndpointProperties.Exposure exposure = webEndpointProperties.getExposure();
    Set<String> include = normalize(exposure.getInclude());
    Set<String> exclude = normalize(exposure.getExclude());

    if (exclude.contains("*") || exclude.contains("prometheus")) {
      return false;
    }

    if (include.isEmpty()) {
      return false;
    }

    return include.contains("*") || include.contains("prometheus");
  }

  private Set<String> normalize(Collection<String> values) {
    if (values == null || values.isEmpty()) {
      return Set.of();
    }
    return values.stream()
        .filter(StringUtils::hasText)
        .map(value -> value.trim().toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }

  private record MergeResult(
      Boolean enabled,
      List<String> allowlist,
      List<String> trustedProxies,
      String token,
      boolean legacyApplied) {}

  private record ListHolder(List<String> value, boolean usingLegacy) {}

  private record ValueHolder(String value, boolean usingLegacy) {}
}
