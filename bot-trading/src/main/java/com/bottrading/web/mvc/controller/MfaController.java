package com.bottrading.web.mvc.controller;

import com.bottrading.saas.security.TenantUserDetails;
import com.bottrading.saas.security.TotpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MfaController {

  private final TotpService totpService;

  public MfaController(TotpService totpService) {
    this.totpService = totpService;
  }

  @GetMapping("/mfa")
  public String mfaPage() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof TenantUserDetails details)) {
      return "redirect:/login";
    }
    if (!details.isMfaEnabled()) {
      return "redirect:/tenant/dashboard";
    }
    return "mfa";
  }

  @PostMapping("/mfa")
  public String verify(
      @RequestParam("code") String code, HttpServletRequest request, Model model) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof TenantUserDetails details)) {
      return "redirect:/login";
    }
    if (totpService.verify(details.getMfaSecret(), code)) {
      request.getSession(true).setAttribute("mfaVerified", Boolean.TRUE);
      return "redirect:/tenant/dashboard";
    }
    model.addAttribute("error", "Código TOTP inválido");
    return "mfa";
  }
}
