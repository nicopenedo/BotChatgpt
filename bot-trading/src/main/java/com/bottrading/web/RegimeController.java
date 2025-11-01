package com.bottrading.web;

import com.bottrading.config.TradingProps;
import com.bottrading.research.regime.RegimeEngine;
import com.bottrading.research.regime.RegimeEngine.RegimeStatus;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/regime")
public class RegimeController {

  private final RegimeEngine regimeEngine;
  private final TradingProps tradingProps;

  public RegimeController(RegimeEngine regimeEngine, TradingProps tradingProps) {
    this.regimeEngine = regimeEngine;
    this.tradingProps = tradingProps;
  }

  @GetMapping("/status")
  @PreAuthorize("hasRole('VIEWER')")
  public ResponseEntity<Map<String, Object>> status(@RequestParam(required = false) String symbol) {
    String effective = symbol != null && !symbol.isBlank() ? symbol : tradingProps.getSymbol();
    RegimeStatus status = regimeEngine.status(effective);
    List<RegimeStatus> allStatuses =
        tradingProps.getSymbols().stream().map(regimeEngine::status).collect(Collectors.toList());
    return ResponseEntity.ok(Map.of("symbol", effective, "status", status, "all", allStatuses));
  }
}
