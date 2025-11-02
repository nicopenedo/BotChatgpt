package com.bottrading.saas.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MfaUiSessionFilter extends OncePerRequestFilter {

  private static final Set<String> EXCLUDED_PREFIXES =
      Set.of(
          "/login",
          "/mfa",
          "/css/",
          "/js/",
          "/img/",
          "/images/",
          "/static/",
          "/webjars/",
          "/webhooks/",
          "/actuator/");

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith)
        || "GET".equalsIgnoreCase(request.getMethod()) && path.equals("/logout");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (path.startsWith("/tenant/")) {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null
          && authentication.isAuthenticated()
          && authentication.getPrincipal() instanceof TenantUserDetails details
          && details.isMfaEnabled()) {
        var session = request.getSession(false);
        boolean verified = session != null && Boolean.TRUE.equals(session.getAttribute("mfaVerified"));
        if (!verified) {
          response.sendRedirect("/mfa");
          return;
        }
      }
    }
    filterChain.doFilter(request, response);
  }
}
