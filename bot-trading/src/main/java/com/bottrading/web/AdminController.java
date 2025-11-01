package com.bottrading.web;

import com.bottrading.config.TradingProps;
import com.bottrading.service.health.HealthService;
import com.bottrading.service.risk.RiskGuard;
import com.bottrading.service.risk.RiskMode;
import com.bottrading.service.risk.RiskState;
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

  private final RiskGuard riskGuard;
  private final TradingProps tradingProperties;
  private final DriftWatchdog driftWatchdog;
  private final HealthService healthService;

  public AdminController(
      RiskGuard riskGuard,
      TradingProps tradingProperties,
      DriftWatchdog driftWatchdog,
      HealthService healthService) {
    this.riskGuard = riskGuard;
    this.tradingProperties = tradingProperties;
    this.driftWatchdog = driftWatchdog;
    this.healthService = healthService;
  }

  @PostMapping("/kill-switch")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Map<String, Object>> activateKillSwitch() {
    riskGuard.setMode(RiskMode.PAUSED);
    return ResponseEntity.ok(Map.of("mode", "paused"));
  }

  @PostMapping("/resume")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Map<String, Object>> resume() {
    riskGuard.setMode(RiskMode.LIVE);
    return ResponseEntity.ok(Map.of("mode", "live"));
  }

  @GetMapping("/live-enabled")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Map<String, Object>> liveEnabled() {
    boolean enabled = tradingProperties.isLiveEnabled() && riskGuard.getState().mode().isLive();
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
    RiskMode target;
    try {
      target = RiskMode.valueOf(mode.toUpperCase());
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid mode"));
    }
    riskGuard.setMode(target);
    return ResponseEntity.ok(Map.of("mode", target.name().toLowerCase()));
  }

  @GetMapping("/risk/status")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<RiskState> riskStatus() {
    return ResponseEntity.ok(riskGuard.getState());
  }

  @PostMapping("/risk/ack")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> acknowledgeRisk() {
    riskGuard.acknowledge();
    return ResponseEntity.ok().build();
  }

  @PostMapping("/health/reset")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> resetHealth() {
    healthService.reset();
    return ResponseEntity.ok().build();
  }
}
