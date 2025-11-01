package com.bottrading.web;

import com.bottrading.config.StopProperties.StopSymbolProperties;
import com.bottrading.execution.StopEngine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stop")
public class StopController {

  private final StopEngine stopEngine;

  public StopController(StopEngine stopEngine) {
    this.stopEngine = stopEngine;
  }

  @PostMapping("/config")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> updateConfig(@Valid @RequestBody StopConfigRequest request) {
    StopSymbolProperties props = new StopSymbolProperties();
    props.setMode(request.mode());
    props.setSlPct(request.slPct());
    props.setTpPct(request.tpPct());
    props.setSlAtrMult(request.slAtrMult());
    props.setTpAtrMult(request.tpAtrMult());
    props.setTrailingEnabled(request.trailingEnabled());
    props.setTrailingPct(request.trailingPct());
    props.setTrailingAtrMult(request.trailingAtrMult());
    props.setBreakevenEnabled(request.breakevenEnabled());
    props.setBreakevenTriggerPct(request.breakevenTriggerPct());
    stopEngine.updateConfiguration(request.symbol(), props);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/status")
  public StopEngine.StopStatus status(@RequestParam @NotBlank String symbol) {
    return stopEngine.status(symbol);
  }

  public record StopConfigRequest(
      @NotBlank String symbol,
      com.bottrading.config.StopProperties.Mode mode,
      @DecimalMin("0.0") BigDecimal slPct,
      @DecimalMin("0.0") BigDecimal tpPct,
      @DecimalMin("0.0") BigDecimal slAtrMult,
      @DecimalMin("0.0") BigDecimal tpAtrMult,
      Boolean trailingEnabled,
      @DecimalMin("0.0") BigDecimal trailingPct,
      @DecimalMin("0.0") BigDecimal trailingAtrMult,
      Boolean breakevenEnabled,
      @DecimalMin("0.0") BigDecimal breakevenTriggerPct) {}
}
