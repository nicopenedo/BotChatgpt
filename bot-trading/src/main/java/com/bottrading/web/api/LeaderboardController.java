package com.bottrading.web.api;

import com.bottrading.model.enums.LeaderboardWindow;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.research.regime.RegimeTrend;
import com.bottrading.service.leaderboard.LeaderboardService;
import com.bottrading.service.leaderboard.LeaderboardService.LeaderboardEntry;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
@PreAuthorize("hasRole('ADMIN')")
public class LeaderboardController {

  private final LeaderboardService leaderboardService;

  public LeaderboardController(LeaderboardService leaderboardService) {
    this.leaderboardService = leaderboardService;
  }

  @GetMapping
  public List<LeaderboardEntry> leaderboard(
      @RequestParam(value = "regime", required = false) String regime,
      @RequestParam(value = "side", required = false) String side,
      @RequestParam(value = "window", required = false) String window,
      @RequestParam(value = "minTrades", required = false) Integer minTrades,
      @RequestParam(value = "maxDD", required = false) Double maxDrawdown) {
    RegimeTrend regimeTrend = regime != null ? RegimeTrend.valueOf(regime.toUpperCase()) : null;
    OrderSide orderSide = side != null ? OrderSide.valueOf(side.toUpperCase()) : null;
    LeaderboardWindow leaderboardWindow =
        window != null ? LeaderboardWindow.valueOf(window.toUpperCase()) : LeaderboardWindow.OOS_90D;
    return leaderboardService.leaderboard(
        regimeTrend, orderSide, leaderboardWindow, minTrades, maxDrawdown);
  }
}
