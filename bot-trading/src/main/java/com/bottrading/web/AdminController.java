package com.bottrading.web;

import com.bottrading.config.TradingProps;
import com.bottrading.service.risk.TradingState;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

  private final TradingState tradingState;
  private final TradingProps tradingProperties;

  public AdminController(TradingState tradingState, TradingProps tradingProperties) {
    this.tradingState = tradingState;
    this.tradingProperties = tradingProperties;
  }

  @PostMapping("/kill-switch")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Map<String, Object>> activateKillSwitch() {
    tradingState.activateKillSwitch();
    return ResponseEntity.ok(Map.of("killSwitch", true));
  }

  @PostMapping("/resume")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Map<String, Object>> resume() {
    tradingState.deactivateKillSwitch();
    return ResponseEntity.ok(Map.of("killSwitch", false));
  }

  @GetMapping("/live-enabled")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Map<String, Object>> liveEnabled() {
    boolean enabled = tradingState.isLiveEnabled() && tradingProperties.isLiveEnabled();
    return ResponseEntity.ok(Map.of("liveEnabled", enabled));
  }
}
