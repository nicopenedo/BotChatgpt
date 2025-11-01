package com.bottrading.web.ui;

import com.bottrading.model.entity.PresetVersion;
import com.bottrading.model.enums.LeaderboardWindow;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PresetActivationMode;
import com.bottrading.research.regime.RegimeTrend;
import com.bottrading.service.leaderboard.LeaderboardService;
import com.bottrading.service.preset.PresetService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/ui/presets")
@PreAuthorize("hasRole('ADMIN')")
public class PresetUiController {

  private final PresetService presetService;
  private final LeaderboardService leaderboardService;

  public PresetUiController(PresetService presetService, LeaderboardService leaderboardService) {
    this.presetService = presetService;
    this.leaderboardService = leaderboardService;
  }

  @ModelAttribute("regimes")
  public RegimeTrend[] regimes() {
    return RegimeTrend.values();
  }

  @ModelAttribute("sides")
  public OrderSide[] sides() {
    return OrderSide.values();
  }

  @ModelAttribute("windows")
  public LeaderboardWindow[] windows() {
    return LeaderboardWindow.values();
  }

  @GetMapping("/leaderboard")
  public String leaderboard(
      @RequestParam(value = "regime", required = false) String regime,
      @RequestParam(value = "side", required = false) String side,
      @RequestParam(value = "window", defaultValue = "OOS_90D") String window,
      @RequestParam(value = "minTrades", required = false) Integer minTrades,
      @RequestParam(value = "maxDD", required = false) Double maxDd,
      Model model) {
    RegimeTrend regimeTrend = regime != null && !regime.isBlank() ? RegimeTrend.valueOf(regime) : null;
    OrderSide orderSide = side != null && !side.isBlank() ? OrderSide.valueOf(side) : null;
    LeaderboardWindow leaderboardWindow = LeaderboardWindow.valueOf(window);
    var entries =
        leaderboardService.leaderboard(regimeTrend, orderSide, leaderboardWindow, minTrades, maxDd);
    model.addAttribute("entries", entries);
    model.addAttribute("selectedRegime", regime);
    model.addAttribute("selectedSide", side);
    model.addAttribute("selectedWindow", window);
    model.addAttribute("minTrades", minTrades);
    model.addAttribute("maxDD", maxDd);
    return "leaderboard";
  }

  @GetMapping("/{id}")
  public String detail(@PathVariable("id") UUID id, Model model) {
    PresetVersion preset = presetService.getPreset(id);
    model.addAttribute("preset", preset);
    model.addAttribute("snapshots", presetService.snapshots(id));
    model.addAttribute("liveMetrics", presetService.liveMetrics(id));
    return "preset_detail";
  }

  @PostMapping("/{id}/activate")
  public String activate(
      @PathVariable("id") UUID id,
      @RequestParam(value = "mode", defaultValue = "full") String mode) {
    PresetActivationMode activationMode =
        "canary".equalsIgnoreCase(mode) ? PresetActivationMode.CANARY : PresetActivationMode.FULL;
    presetService.activatePreset(id, activationMode, "ui");
    return "redirect:/ui/presets/leaderboard";
  }

  @PostMapping("/{id}/retire")
  public String retire(@PathVariable("id") UUID id) {
    presetService.retirePreset(id, "ui");
    return "redirect:/ui/presets/leaderboard";
  }

  @PostMapping("/{id}/rollback")
  public String rollback(@PathVariable("id") UUID id) {
    PresetVersion preset = presetService.getPreset(id);
    presetService.rollback(preset.getRegime(), preset.getSide(), "ui");
    return "redirect:/ui/presets/leaderboard";
  }
}
