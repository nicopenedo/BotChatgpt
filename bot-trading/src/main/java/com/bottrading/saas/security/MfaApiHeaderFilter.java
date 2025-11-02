package com.bottrading.saas.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MfaApiHeaderFilter extends OncePerRequestFilter {

  private final TotpService totpService;

  public MfaApiHeaderFilter(TotpService totpService) {
    this.totpService = totpService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.isAuthenticated()) {
      Object principal = authentication.getPrincipal();
      if (principal instanceof TenantUserDetails details && details.isMfaEnabled()) {
        String totp = request.getHeader("X-TOTP");
        if (!totpService.verify(details.getMfaSecret(), totp)) {
          response.setStatus(HttpStatus.UNAUTHORIZED.value());
          response.getWriter().write("Invalid or missing MFA token");
          return;
        }
      }
    }
    filterChain.doFilter(request, response);
  }
}
