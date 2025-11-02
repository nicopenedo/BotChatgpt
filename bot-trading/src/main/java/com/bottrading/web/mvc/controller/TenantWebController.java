package com.bottrading.web.mvc.controller;

import com.bottrading.web.mvc.model.BotDetailView;
import com.bottrading.web.mvc.service.TenantViewService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/tenant")
public class TenantWebController {

  private final TenantViewService tenantViewService;

  public TenantWebController(TenantViewService tenantViewService) {
    this.tenantViewService = tenantViewService;
  }

  @GetMapping("/dashboard")
  public String dashboard(Model model) {
    UUID tenantId = currentTenantId();
    model.addAttribute("pageTitle", "Dashboard");
    model.addAttribute("activePage", "dashboard");
    model.addAttribute("tenantSwitcher", tenantViewService.tenantSwitcher(tenantId));
    model.addAttribute("notifications", tenantViewService.notifications());
    var dashboard = tenantViewService.dashboard(tenantId);
    model.addAttribute("kpis", dashboard.kpis());
    model.addAttribute("equityChart", dashboard.equityChartJson());
    model.addAttribute("pnlChart", dashboard.pnlChartJson());
    model.addAttribute("heatmapChart", dashboard.heatmapChartJson());
    model.addAttribute("recentActivity", dashboard.recentActivity());
    model.addAttribute("riskCards", dashboard.riskCards());
    return "tenant/dashboard";
  }

  @GetMapping("/bots")
  public String bots(Model model) {
    UUID tenantId = currentTenantId();
    model.addAttribute("pageTitle", "Bots");
    model.addAttribute("activePage", "bots");
    model.addAttribute("tenantSwitcher", tenantViewService.tenantSwitcher(tenantId));
    model.addAttribute("notifications", tenantViewService.notifications());
    model.addAttribute("bots", tenantViewService.bots(tenantId));
    model.addAttribute("pagination", tenantViewService.botsPagination());
    model.addAttribute("regimes", tenantViewService.regimes());
    return "tenant/bots";
  }

  @GetMapping("/bots/{id}")
  public String botDetail(@PathVariable("id") String botId, Model model) {
    UUID tenantId = currentTenantId();
    model.addAttribute("pageTitle", "Detalle bot");
    model.addAttribute("activePage", "bots");
    model.addAttribute("tenantSwitcher", tenantViewService.tenantSwitcher(tenantId));
    model.addAttribute("notifications", tenantViewService.notifications());
    BotDetailView botDetail = tenantViewService.botDetail(tenantId, botId);
    model.addAttribute("bot", botDetail);
    model.addAttribute("equityChart", botDetail.equityChartJson());
    model.addAttribute("symbolChart", botDetail.symbolChartJson());
    model.addAttribute("slippageChart", botDetail.slippageChartJson());
    return "tenant/bot_detail";
  }

  @GetMapping("/leaderboard")
  public String leaderboard(Model model) {
    UUID tenantId = currentTenantId();
    model.addAttribute("pageTitle", "Leaderboard");
    model.addAttribute("activePage", "leaderboard");
    model.addAttribute("tenantSwitcher", tenantViewService.tenantSwitcher(tenantId));
    model.addAttribute("notifications", tenantViewService.notifications());
    model.addAttribute("entries", tenantViewService.leaderboardEntries());
    model.addAttribute("entityOptions", tenantViewService.leaderboardEntities("bots"));
    model.addAttribute("windowOptions", tenantViewService.leaderboardWindows("30d"));
    return "tenant/leaderboard";
  }

  private UUID currentTenantId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getName() != null) {
      // Demo tenant id derivado del email para ambientes mock
      return UUID.nameUUIDFromBytes(authentication.getName().getBytes());
    }
    return UUID.randomUUID();
  }
}
