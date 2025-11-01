package com.bottrading.cli;

import com.bottrading.cli.preset.PresetsCommand;
import com.bottrading.cli.preset.PresetsImportCommand;
import com.bottrading.cli.preset.PresetsPromoteCommand;
import com.bottrading.cli.preset.PresetsRetireCommand;
import com.bottrading.cli.preset.PresetsRollbackCommand;
import com.bottrading.cli.preset.PresetsSnapshotCommand;
import com.bottrading.cli.leaderboard.LeaderboardCliCommand;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(
    name = "bot",
    subcommands = {PresetsCommand.class, LeaderboardCliCommand.class},
    mixinStandardHelpOptions = true,
    description = "Bot management CLI")
public class BotRootCommand implements Runnable {

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  public boolean handles(String arg) {
    return switch (arg) {
      case "presets", "leaderboard" -> true;
      default -> false;
    };
  }
}
