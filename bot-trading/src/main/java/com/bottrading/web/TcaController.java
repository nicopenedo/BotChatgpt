package com.bottrading.web;

import com.bottrading.service.tca.TcaService;
import com.bottrading.service.tca.TcaService.AggregatedStats;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tca")
public class TcaController {

  private final TcaService tcaService;

  public TcaController(TcaService tcaService) {
    this.tcaService = tcaService;
  }

  @GetMapping("/slippage")
  @PreAuthorize("hasRole('VIEWER')")
  public ResponseEntity<AggregatedStats> slippage(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant to) {
    AggregatedStats stats = tcaService.aggregate(symbol, from, to);
    return ResponseEntity.ok(stats);
  }
}
