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
public class MfaFilter extends OncePerRequestFilter {

  private final TotpValidator totpValidator;

  public MfaFilter(TotpValidator totpValidator) {
    this.totpValidator = totpValidator;
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
        if (!totpValidator.isValid(details.getMfaSecret(), totp)) {
          response.setStatus(HttpStatus.UNAUTHORIZED.value());
          response.getWriter().write("Invalid or missing MFA token");
          return;
        }
      }
    }
    filterChain.doFilter(request, response);
  }
}
