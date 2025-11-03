package com.bottrading.config.security;

import com.bottrading.config.props.PrometheusSecurityProps;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

@Component
public class PrometheusScrapeSecurityFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(PrometheusScrapeSecurityFilter.class);
  private static final String PROMETHEUS_PATH = "/actuator/prometheus";
  public static final String ACCESS_GRANTED_ATTRIBUTE =
      PrometheusScrapeSecurityFilter.class.getName() + ".ACCESS_GRANTED";
  private static final String PROMETHEUS_TOKEN_HEADER = "X-Prometheus-Token";
  private static final String X_FORWARDED_FOR = "X-Forwarded-For";
  private static final String X_REAL_IP = "X-Real-IP";

  private final PrometheusSecurityProps properties;
  private final boolean prometheusExportEnabled;
  private final boolean prometheusEndpointExposed;
  private final List<IpAddressMatcher> allowlistMatchers;
  private final List<IpAddressMatcher> trustedProxyMatchers;

  public PrometheusScrapeSecurityFilter(
      PrometheusSecurityProps properties,
      @Value("${management.metrics.export.prometheus.enabled:true}")
          boolean prometheusExportEnabled,
      WebEndpointProperties webEndpointProperties) {
    this.properties = properties;
    this.prometheusExportEnabled = prometheusExportEnabled;
    this.prometheusEndpointExposed = isPrometheusEndpointExposed(webEndpointProperties);
    this.allowlistMatchers = compileCidrs(properties.allowlistCidrs(), "allowlist");
    this.trustedProxyMatchers = compileCidrs(properties.trustedProxiesCidrs(), "trusted proxy");
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
    String requestUri = request.getRequestURI();
    if (!PROMETHEUS_PATH.equals(requestUri)) {
      return true;
    }
    if (!prometheusExportEnabled) {
      return true;
    }
    return !prometheusEndpointExposed;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!HttpMethod.GET.matches(request.getMethod())) {
      reject(response, HttpStatus.METHOD_NOT_ALLOWED);
      return;
    }

    setNoStore(response);

    boolean protectionEnabled = resolveEnabled();

    if (!protectionEnabled && !properties.hasSecurityPolicies()) {
      log.warn(
          "Prometheus expuesto pero sin token ni allowlist. Acceso denegado por fail-safe; configure observability.prometheus.token o allowlist-cidrs.");
      reject(response, HttpStatus.FORBIDDEN);
      return;
    }

    if (!properties.hasSecurityPolicies()) {
      log.warn(
          "Prometheus expuesto pero sin token ni allowlist. Acceso denegado por fail-safe; configure observability.prometheus.token o allowlist-cidrs.");
      reject(response, HttpStatus.FORBIDDEN);
      return;
    }

    if (!validateToken(request)) {
      reject(response, HttpStatus.UNAUTHORIZED);
      return;
    }

    if (!validateAllowlist(request)) {
      reject(response, HttpStatus.FORBIDDEN);
      return;
    }

    request.setAttribute(ACCESS_GRANTED_ATTRIBUTE, Boolean.TRUE);
    filterChain.doFilter(request, response);
  }

  private boolean resolveEnabled() {
    Boolean configured = properties.enabled();
    if (configured == null) {
      return prometheusEndpointExposed;
    }
    if (!configured && properties.hasSecurityPolicies()) {
      return true;
    }
    return configured;
  }

  private boolean validateToken(HttpServletRequest request) {
    String configuredToken = properties.token();
    if (!StringUtils.hasText(configuredToken)) {
      return true;
    }
    String providedToken = request.getHeader(PROMETHEUS_TOKEN_HEADER);
    return configuredToken.equals(providedToken);
  }

  private boolean validateAllowlist(HttpServletRequest request) {
    if (allowlistMatchers.isEmpty()) {
      return true;
    }
    Optional<String> clientIp = resolveClientIp(request);
    if (clientIp.isEmpty()) {
      return false;
    }
    for (IpAddressMatcher matcher : allowlistMatchers) {
      if (matcher.matches(clientIp.get())) {
        return true;
      }
    }
    return false;
  }

  private Optional<String> resolveClientIp(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    if (!StringUtils.hasText(remoteAddr)) {
      return Optional.empty();
    }

    if (trustedProxyMatchers.isEmpty()) {
      return Optional.of(remoteAddr);
    }

    if (!matches(remoteAddr, trustedProxyMatchers)) {
      return Optional.of(remoteAddr);
    }

    ForwardedHeaderResult forwardedCandidate = resolveFromForwardedHeaders(request);
    if (forwardedCandidate.invalid()) {
      return Optional.empty();
    }
    if (forwardedCandidate.clientIp().isPresent()) {
      return forwardedCandidate.clientIp();
    }

    return Optional.of(remoteAddr);
  }

  private ForwardedHeaderResult resolveFromForwardedHeaders(HttpServletRequest request) {
    String header = request.getHeader(X_FORWARDED_FOR);
    if (StringUtils.hasText(header)) {
      String[] parts = header.split(",");
      for (String part : parts) {
        String candidate = part.trim();
        if (!isValidIp(candidate)) {
          return ForwardedHeaderResult.invalid();
        }
        if (!matches(candidate, trustedProxyMatchers)) {
          return ForwardedHeaderResult.resolved(candidate);
        }
      }
    }

    String realIp = request.getHeader(X_REAL_IP);
    if (StringUtils.hasText(realIp)) {
      String candidate = realIp.trim();
      if (!isValidIp(candidate)) {
        return ForwardedHeaderResult.invalid();
      }
      if (!matches(candidate, trustedProxyMatchers)) {
        return ForwardedHeaderResult.resolved(candidate);
      }
    }

    return ForwardedHeaderResult.none();
  }

  private boolean matches(String candidate, List<IpAddressMatcher> matchers) {
    for (IpAddressMatcher matcher : matchers) {
      if (matcher.matches(candidate)) {
        return true;
      }
    }
    return false;
  }

  private boolean isValidIp(String candidate) {
    if (!StringUtils.hasText(candidate) || candidate.contains("/") || candidate.contains("%")) {
      return false;
    }
    if (candidate.contains(":")) {
      try {
        InetAddress address = InetAddress.getByName(candidate);
        return address instanceof Inet6Address;
      } catch (UnknownHostException ex) {
        return false;
      }
    }
    if (!candidate.chars().allMatch(ch -> Character.isDigit(ch) || ch == '.')) {
      return false;
    }
    String[] segments = candidate.split("\\.");
    if (segments.length != 4) {
      return false;
    }
    for (String segment : segments) {
      if (segment.isEmpty() || segment.length() > 3) {
        return false;
      }
      try {
        int value = Integer.parseInt(segment);
        if (value < 0 || value > 255) {
          return false;
        }
      } catch (NumberFormatException ex) {
        return false;
      }
    }
    return true;
  }

  private List<IpAddressMatcher> compileCidrs(List<String> cidrs, String kind) {
    List<IpAddressMatcher> matchers = new ArrayList<>();
    for (String cidr : cidrs) {
      try {
        matchers.add(new IpAddressMatcher(cidr));
      } catch (IllegalArgumentException ex) {
        log.warn("Ignorando entrada {} inválida para Prometheus: {}", kind, cidr);
      }
    }
    return List.copyOf(matchers);
  }

  private void setNoStore(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
  }

  private boolean isPrometheusEndpointExposed(WebEndpointProperties properties) {
    WebEndpointProperties.Exposure exposure = properties.getExposure();
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

  private record ForwardedHeaderResult(Optional<String> clientIp, boolean invalid) {
    private static ForwardedHeaderResult resolved(String ip) {
      return new ForwardedHeaderResult(Optional.of(ip), false);
    }

    private static ForwardedHeaderResult invalid() {
      return new ForwardedHeaderResult(Optional.empty(), true);
    }

    private static ForwardedHeaderResult none() {
      return new ForwardedHeaderResult(Optional.empty(), false);
    }
  }

  private void reject(HttpServletResponse response, HttpStatus status) throws IOException {
    setNoStore(response);
    response.setStatus(status.value());
    response.setContentType("text/plain");
    response.getWriter().write(status.getReasonPhrase());
  }
}
