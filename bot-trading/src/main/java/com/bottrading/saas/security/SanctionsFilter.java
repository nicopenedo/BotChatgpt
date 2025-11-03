package com.bottrading.saas.security;

import com.bottrading.saas.config.SaasProperties;
import com.bottrading.saas.model.entity.TenantEntity;
import com.bottrading.saas.repository.TenantRepository;
import com.bottrading.saas.service.AuditService;
import com.bottrading.saas.service.GeoLocationService;
import com.bottrading.util.JsonUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SanctionsFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(SanctionsFilter.class);

  private final GeoLocationService geoLocationService;
  private final SaasProperties properties;
  private final TenantRepository tenantRepository;
  private final AuditService auditService;

  public SanctionsFilter(
      GeoLocationService geoLocationService,
      SaasProperties properties,
      TenantRepository tenantRepository,
      AuditService auditService) {
    this.geoLocationService = geoLocationService;
    this.properties = properties;
    this.tenantRepository = tenantRepository;
    this.auditService = auditService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    List<String> sanctioned = properties.getLegal().getSanctionedCountries();
    if (sanctioned == null || sanctioned.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }
    Optional<String> ipCountry = geoLocationService.resolveCountry(request);
    UUID tenantId = TenantContext.getTenantId();
    String billingCountry = null;
    if (tenantId != null) {
      billingCountry = tenantRepository.findById(tenantId).map(TenantEntity::getBillingCountry).orElse(null);
    }
    boolean blocked =
        ipCountry.map(value -> sanctioned.contains(value.toUpperCase(Locale.ROOT))).orElse(false)
            || (billingCountry != null
                && sanctioned.contains(billingCountry.toUpperCase(Locale.ROOT)));
    if (blocked) {
      log.warn("Blocked request from sanctioned country: {} tenant={} path={}", ipCountry.orElse(billingCountry), tenantId, request.getRequestURI());
      if (tenantId != null) {
        auditService.record(tenantId, null, "geo.blocked", java.util.Map.of("country", ipCountry.orElse(billingCountry)));
      }
      if (request.getRequestURI().startsWith("/blocked")) {
        filterChain.doFilter(request, response);
        return;
      }
      if (request.getRequestURI().startsWith("/api/")) {
        response.setStatus(451);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JsonUtils.toJson(java.util.Map.of("error", "geo_blocked")));
      } else {
        response.sendRedirect("/blocked");
      }
      return;
    }
    filterChain.doFilter(request, response);
  }
}
