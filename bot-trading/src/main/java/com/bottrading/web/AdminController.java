package com.bottrading.web;

import com.bottrading.config.TradingProps;
import com.bottrading.service.health.HealthService;
import com.bottrading.service.risk.TradingState;
import com.bottrading.service.risk.TradingState.Mode;
import com.bottrading.service.risk.drift.DriftWatchdog;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

  private final TradingState tradingState;
  private final TradingProps tradingProperties;
  private final DriftWatchdog driftWatchdog;
  private final HealthService healthService;

  public AdminController(
      TradingState tradingState,
      TradingProps tradingProperties,
      DriftWatchdog driftWatchdog,
      HealthService healthService) {
    this.tradingState = tradingState;
    this.tradingProperties = tradingProperties;
    this.driftWatchdog = driftWatchdog;
    this.healthService = healthService;
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

  @GetMapping("/drift/status")
  @PreAuthorize("hasRole('VIEWER')")
  public ResponseEntity<DriftWatchdog.Status> driftStatus() {
    return ResponseEntity.ok(driftWatchdog.status());
  }

  @PostMapping("/drift/reset")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> resetDrift() {
    driftWatchdog.reset();
    return ResponseEntity.ok().build();
  }

  @PostMapping("/mode/{mode}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Map<String, Object>> setMode(@PathVariable String mode) {
    Mode target;
    try {
      target = Mode.valueOf(mode.toUpperCase());
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid mode"));
    }
    tradingState.setMode(target);
    if (target == Mode.PAUSED) {
      tradingState.activateKillSwitch();
    } else if (target == Mode.LIVE) {
      tradingState.deactivateKillSwitch();
    }
    return ResponseEntity.ok(Map.of("mode", target.name().toLowerCase()));
  }

  @PostMapping("/health/reset")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> resetHealth() {
    healthService.reset();
    return ResponseEntity.ok().build();
  }
}
