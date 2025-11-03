package com.bottrading.config.security;

import com.bottrading.config.props.PrometheusSecurityProps;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

@Component
public class PrometheusScrapeSecurityFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(PrometheusScrapeSecurityFilter.class);
  private static final String PROMETHEUS_PATH = "/actuator/prometheus";
  private static final String PROMETHEUS_TOKEN_HEADER = "X-Prometheus-Token";

  private final PrometheusSecurityProps properties;
  private final boolean prometheusExportEnabled;

  public PrometheusScrapeSecurityFilter(
      PrometheusSecurityProps properties,
      @Value("${management.metrics.export.prometheus.enabled:true}")
          boolean prometheusExportEnabled) {
    this.properties = properties;
    this.prometheusExportEnabled = prometheusExportEnabled;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
    String requestUri = request.getRequestURI();
    return !prometheusExportEnabled || !PROMETHEUS_PATH.equals(requestUri);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!StringUtils.hasText(properties.getToken()) && properties.getAllowlist().isEmpty()) {
      reject(response, HttpStatus.UNAUTHORIZED);
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

    filterChain.doFilter(request, response);
  }

  private boolean validateToken(HttpServletRequest request) {
    String configuredToken = properties.getToken();
    if (!StringUtils.hasText(configuredToken)) {
      return true;
    }
    String providedToken = request.getHeader(PROMETHEUS_TOKEN_HEADER);
    return configuredToken.equals(providedToken);
  }

  private boolean validateAllowlist(HttpServletRequest request) {
    List<IpAddressMatcher> matchers = new ArrayList<>();
    for (String cidr : properties.getAllowlist()) {
      try {
        matchers.add(new IpAddressMatcher(cidr));
      } catch (IllegalArgumentException ex) {
        log.warn("Ignorando entrada de allowlist inválida para Prometheus: {}", cidr);
      }
    }
    if (matchers.isEmpty()) {
      return true;
    }
    String candidate = extractClientIp(request);
    if (!StringUtils.hasText(candidate)) {
      return false;
    }
    for (IpAddressMatcher matcher : matchers) {
      if (matcher.matches(candidate)) {
        return true;
      }
    }
    return false;
  }

  private String extractClientIp(HttpServletRequest request) {
    String header = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(header)) {
      return header.split(",")[0].trim();
    }
    header = request.getHeader("X-Real-IP");
    if (StringUtils.hasText(header)) {
      return header.trim();
    }
    return request.getRemoteAddr();
  }

  private void reject(HttpServletResponse response, HttpStatus status) throws IOException {
    response.setStatus(status.value());
    response.setContentType("text/plain");
    response.getWriter().write(status.getReasonPhrase());
  }
}
