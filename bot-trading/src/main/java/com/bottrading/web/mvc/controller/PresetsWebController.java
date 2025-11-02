package com.bottrading.web.mvc.controller;

import com.bottrading.web.mvc.service.TenantViewService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/tenant/presets")
public class PresetsWebController {

  private final TenantViewService tenantViewService;

  public PresetsWebController(TenantViewService tenantViewService) {
    this.tenantViewService = tenantViewService;
  }

  @GetMapping
  public String presets(@RequestParam(name = "regime", required = false) String regime, Model model) {
    UUID tenantId = currentTenantId();
    model.addAttribute("pageTitle", "Presets");
    model.addAttribute("activePage", "presets");
    model.addAttribute("tenantSwitcher", tenantViewService.tenantSwitcher(tenantId));
    model.addAttribute("notifications", tenantViewService.notifications());
    model.addAttribute("regimeSummary", tenantViewService.presetRegimes());
    model.addAttribute("presets", tenantViewService.presetsTable());
    model.addAttribute("regimeFilter", tenantViewService.presetRegimeOptions(regime));
    return "tenant/presets";
  }

  @GetMapping("/{id}")
  public String presetDetail(@PathVariable("id") String presetId, Model model) {
    UUID tenantId = currentTenantId();
    model.addAttribute("pageTitle", "Detalle preset");
    model.addAttribute("activePage", "presets");
    model.addAttribute("tenantSwitcher", tenantViewService.tenantSwitcher(tenantId));
    model.addAttribute("notifications", tenantViewService.notifications());
    var preset = tenantViewService.presetDetail(presetId);
    model.addAttribute("preset", preset);
    model.addAttribute("performanceChart", preset.performanceChartJson());
    model.addAttribute("snapshots", tenantViewService.presetSnapshots());
    return "tenant/preset_detail";
  }

  private UUID currentTenantId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getName() != null) {
      return UUID.nameUUIDFromBytes(authentication.getName().getBytes());
    }
    return UUID.randomUUID();
  }
}
