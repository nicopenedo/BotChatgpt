package com.bottrading.web;

// FIX: Confirm ResponseEntity generic type matches SignalResult payload.

import com.bottrading.service.StrategyService;
import com.bottrading.strategy.StrategyDecision;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/strategy")
public class StrategyController {

  private final StrategyService strategyService;

  public StrategyController(StrategyService strategyService) {
    this.strategyService = strategyService;
  }

  @GetMapping("/decide")
  public ResponseEntity<StrategyDecision> decide(@RequestParam(required = false) String symbol) {
    return ResponseEntity.ok(strategyService.decide(symbol));
  }
}
