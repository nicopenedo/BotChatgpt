package com.bottrading.web;

import com.bottrading.chaos.ChaosRequest;
import com.bottrading.chaos.ChaosStatus;
import com.bottrading.chaos.ChaosSuite;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/chaos")
public class AdminChaosController {

  private final ChaosSuite chaosSuite;

  public AdminChaosController(ChaosSuite chaosSuite) {
    this.chaosSuite = chaosSuite;
  }

  @PostMapping("/start")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ChaosStatus> start(@RequestBody(required = false) ChaosRequest request) {
    return ResponseEntity.ok(chaosSuite.start(request));
  }

  @PostMapping("/stop")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ChaosStatus> stop() {
    return ResponseEntity.ok(chaosSuite.stop());
  }

  @GetMapping("/status")
  @PreAuthorize("hasRole('VIEWER')")
  public ResponseEntity<ChaosStatus> status() {
    return ResponseEntity.ok(chaosSuite.status());
  }
}
