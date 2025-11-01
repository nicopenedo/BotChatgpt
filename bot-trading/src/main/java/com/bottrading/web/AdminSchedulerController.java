package com.bottrading.web;

import com.bottrading.executor.TradingScheduler;
import com.bottrading.executor.TradingScheduler.SchedulerStatus;
import com.bottrading.service.risk.RiskGuard;
import com.bottrading.service.risk.RiskMode;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/scheduler")
public class AdminSchedulerController {

  private final TradingScheduler scheduler;
  private final RiskGuard riskGuard;

  public AdminSchedulerController(TradingScheduler scheduler, RiskGuard riskGuard) {
    this.scheduler = scheduler;
    this.riskGuard = riskGuard;
  }

  @PostMapping("/enable")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Map<String, Object>> enable() {
    scheduler.enable();
    if (riskGuard.getState().mode().isPaused()) {
      riskGuard.setMode(RiskMode.SHADOW);
    }
    return ResponseEntity.ok(Map.of("enabled", true));
  }

  @PostMapping("/disable")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Map<String, Object>> disable() {
    scheduler.disable();
    return ResponseEntity.ok(Map.of("enabled", false));
  }

  @GetMapping("/status")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<SchedulerStatus> status() {
    return ResponseEntity.ok(scheduler.status());
  }
}
