package com.bottrading.web.api;

import com.bottrading.service.leaderboard.LeaderboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/presets")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPresetController {

  private final LeaderboardService leaderboardService;

  public AdminPresetController(LeaderboardService leaderboardService) {
    this.leaderboardService = leaderboardService;
  }

  @PostMapping("/recompute-leaderboard")
  public ResponseEntity<String> recompute() {
    // Leaderboard is computed on demand, this endpoint exists for compatibility.
    leaderboardService.leaderboard(null, null, null, null, null);
    return ResponseEntity.ok("Leaderboard recomputed");
  }
}
