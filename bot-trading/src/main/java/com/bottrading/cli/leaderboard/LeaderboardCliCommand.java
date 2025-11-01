package com.bottrading.cli.leaderboard;

import com.bottrading.model.enums.LeaderboardWindow;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.research.regime.RegimeTrend;
import com.bottrading.service.leaderboard.LeaderboardService;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "leaderboard", description = "Print leaderboard")
public class LeaderboardCliCommand implements Runnable {

  @CommandLine.Option(names = "--regime", required = false)
  private String regime;

  @CommandLine.Option(names = "--side", required = false)
  private String side;

  @CommandLine.Option(names = "--window", defaultValue = "OOS_90D")
  private String window;

  @CommandLine.Option(names = "--min-trades", required = false)
  private Integer minTrades;

  @CommandLine.Option(names = "--maxdd", required = false)
  private Double maxDrawdown;

  private final LeaderboardService leaderboardService;

  public LeaderboardCliCommand(LeaderboardService leaderboardService) {
    this.leaderboardService = leaderboardService;
  }

  @Override
  public void run() {
    var entries =
        leaderboardService.leaderboard(
            regime != null ? RegimeTrend.valueOf(regime.toUpperCase()) : null,
            side != null ? OrderSide.valueOf(side.toUpperCase()) : null,
            LeaderboardWindow.valueOf(window.toUpperCase()),
            minTrades,
            maxDrawdown);
    entries.forEach(
        entry ->
            System.out.printf(
                "%s %s PF=%.2f MaxDD=%.2f Trades=%.0f Window=%s%n",
                entry.preset().getRegime(),
                entry.preset().getSide(),
                entry.profitFactor(),
                entry.maxDrawdown(),
                entry.trades(),
                entry.window()));
  }
}
