package com.bottrading.web;

import com.bottrading.executor.TradingScheduler;
import com.bottrading.executor.TradingScheduler.SchedulerStatus;
import com.bottrading.service.risk.TradingState;
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
  private final TradingState tradingState;

  public AdminSchedulerController(TradingScheduler scheduler, TradingState tradingState) {
    this.scheduler = scheduler;
    this.tradingState = tradingState;
  }

  @PostMapping("/enable")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Map<String, Object>> enable() {
    scheduler.enable();
    tradingState.deactivateKillSwitch();
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
