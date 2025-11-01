package com.bottrading.web;

import com.bottrading.model.entity.RiskVarSnapshotEntity;
import com.bottrading.service.risk.IntradayVarService;
import com.bottrading.service.risk.IntradayVarService.VarStatus;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/var")
public class VarController {

  private final IntradayVarService intradayVarService;

  public VarController(IntradayVarService intradayVarService) {
    this.intradayVarService = intradayVarService;
  }

  @GetMapping("/status")
  @PreAuthorize("hasRole('VIEWER')")
  public ResponseEntity<VarStatus> status(@RequestParam(required = false) String symbol) {
    return ResponseEntity.ok(intradayVarService.status(symbol));
  }

  @GetMapping("/snapshots")
  @PreAuthorize("hasRole('VIEWER')")
  public ResponseEntity<List<SnapshotResponse>> snapshots(
      @RequestParam String symbol,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from) {
    List<RiskVarSnapshotEntity> snapshots = intradayVarService.history(symbol, from);
    return ResponseEntity.ok(snapshots.stream().map(SnapshotResponse::from).collect(Collectors.toList()));
  }

  public record SnapshotResponse(
      Instant timestamp,
      String symbol,
      String regime,
      String presetKey,
      String presetId,
      String reasonsJson,
      String var,
      String cvar,
      String qtyRatio) {

    static SnapshotResponse from(RiskVarSnapshotEntity entity) {
      return new SnapshotResponse(
          entity.getTimestamp(),
          entity.getSymbol(),
          entity.getRegime(),
          entity.getPresetKey(),
          entity.getPresetId() != null ? entity.getPresetId().toString() : null,
          entity.getReasonsJson(),
          entity.getVar() != null ? entity.getVar().toPlainString() : null,
          entity.getCvar() != null ? entity.getCvar().toPlainString() : null,
          entity.getQtyRatio() != null ? entity.getQtyRatio().toPlainString() : null);
    }
  }
}
