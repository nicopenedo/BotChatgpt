package com.bottrading.web;

import com.bottrading.config.TradingProps;
import com.bottrading.service.anomaly.AnomalyDetector;
import com.bottrading.service.health.HealthService;
import com.bottrading.service.health.HealthService.HealthStatus;
import com.bottrading.service.risk.IntradayVarService;
import com.bottrading.service.risk.IntradayVarService.VarStatus;
import com.bottrading.service.risk.RiskGuard;
import com.bottrading.service.risk.RiskState;
import com.bottrading.service.risk.drift.DriftWatchdog;
import com.bottrading.service.risk.drift.DriftWatchdog.Status;
import com.bottrading.service.trading.AllocatorService;
import com.bottrading.service.trading.AllocatorService.AllocationStatus;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/status")
public class StatusController {

  private final TradingProps tradingProps;
  private final RiskGuard riskGuard;
  private final AllocatorService allocatorService;
  private final DriftWatchdog driftWatchdog;
  private final HealthService healthService;
  private final IntradayVarService intradayVarService;
  private final AnomalyDetector anomalyDetector;

  public StatusController(
      TradingProps tradingProps,
      RiskGuard riskGuard,
      AllocatorService allocatorService,
      DriftWatchdog driftWatchdog,
      HealthService healthService,
      IntradayVarService intradayVarService,
      AnomalyDetector anomalyDetector) {
    this.tradingProps = tradingProps;
    this.riskGuard = riskGuard;
    this.allocatorService = allocatorService;
    this.driftWatchdog = driftWatchdog;
    this.healthService = healthService;
    this.intradayVarService = intradayVarService;
    this.anomalyDetector = anomalyDetector;
  }

  @GetMapping("/overview")
  @PreAuthorize("hasRole('VIEWER')")
  public ResponseEntity<Map<String, Object>> overview(
      @RequestParam(required = false) String symbol) {
    String effective = symbol != null && !symbol.isBlank() ? symbol : tradingProps.getSymbol();
    AllocationStatus allocation = allocatorService.status(effective);
    Status driftStatus = driftWatchdog.status();
    HealthStatus healthStatus = healthService.status();
    Map<String, Object> trading = new HashMap<>();
    RiskState riskState = riskGuard.getState();
    trading.put("mode", riskState.mode().name());
    trading.put("killSwitch", riskState.mode().isPaused());
    trading.put("liveEnabled", tradingProps.isLiveEnabled() && riskState.mode().isLive());
    trading.put("riskFlags", riskState.flags());
    trading.put("marketDataStale", riskState.marketDataStale());
    VarStatus varStatus = intradayVarService.status(effective);
    Map<String, Object> anomaly =
        anomalyDetector.snapshot(effective).map(AnomalyDetector.AnomalySnapshot::asMap).orElse(Map.of("active", false));
    Map<String, Object> var = new HashMap<>();
    var.put("cvar", varStatus.cvar());
    var.put("var", varStatus.var());
    var.put("qtyRatio", varStatus.qtyRatio());
    var.put("timestamp", varStatus.timestamp());
    var.put("exposure", riskState.varExposure());
    var.put("limit", riskState.varLimit());
    var.put("ratio", riskState.varRatio());
    return ResponseEntity.ok(
        Map.of(
            "symbol", effective,
            "allocator", allocation,
            "drift", driftStatus,
            "health", healthStatus,
            "trading", trading,
            "var", var,
            "anomaly", anomaly));
  }
}

