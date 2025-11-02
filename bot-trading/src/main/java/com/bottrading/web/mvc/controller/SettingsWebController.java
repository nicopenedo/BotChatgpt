package com.bottrading.web.mvc.controller;

import com.bottrading.web.mvc.service.TenantViewService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/tenant/settings")
public class SettingsWebController {

  private static final Logger log = LoggerFactory.getLogger(SettingsWebController.class);

  private final TenantViewService tenantViewService;

  public SettingsWebController(TenantViewService tenantViewService) {
    this.tenantViewService = tenantViewService;
  }

  @GetMapping
  public String settings(
      @RequestParam(name = "saved", required = false) String saved, Model model) {
    UUID tenantId = currentTenantId();
    model.addAttribute("pageTitle", "Settings");
    model.addAttribute("activePage", "settings");
    model.addAttribute("tenantSwitcher", tenantViewService.tenantSwitcher(tenantId));
    model.addAttribute("notifications", tenantViewService.notifications());
    model.addAttribute("settings", tenantViewService.settings(tenantId));
    model.addAttribute("saved", saved);
    return "tenant/settings";
  }

  @PostMapping("/notifications")
  public String updateNotifications(
      @RequestParam(name = "email", defaultValue = "false") boolean email,
      @RequestParam(name = "telegram", defaultValue = "false") boolean telegram,
      RedirectAttributes redirectAttributes) {
    log.info("Notificaciones actualizadas email={}, telegram={}", email, telegram);
    redirectAttributes.addAttribute("saved", "notifications");
    return "redirect:/tenant/settings";
  }

  @PostMapping("/limits")
  public String updateLimits(
      @RequestParam double maxDailyLoss,
      @RequestParam double maxTradeLoss,
      @RequestParam double maxExposure,
      @RequestParam String timezone,
      @RequestParam(name = "darkMode", defaultValue = "false") boolean darkMode,
      RedirectAttributes redirectAttributes) {
    log.info(
        "Límites actualizados daily={} trade={} exposure={} tz={} darkMode={}",
        maxDailyLoss,
        maxTradeLoss,
        maxExposure,
        timezone,
        darkMode);
    redirectAttributes.addAttribute("saved", "limits");
    return "redirect:/tenant/settings";
  }

  private UUID currentTenantId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getName() != null) {
      return UUID.nameUUIDFromBytes(authentication.getName().getBytes());
    }
    return UUID.randomUUID();
  }
}
