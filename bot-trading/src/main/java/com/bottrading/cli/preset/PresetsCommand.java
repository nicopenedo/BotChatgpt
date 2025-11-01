package com.bottrading.cli.preset;

import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(
    name = "presets",
    description = "Preset lifecycle commands",
    mixinStandardHelpOptions = true,
    subcommands = {
      PresetsImportCommand.class,
      PresetsPromoteCommand.class,
      PresetsRetireCommand.class,
      PresetsRollbackCommand.class,
      PresetsSnapshotCommand.class
    })
public class PresetsCommand implements Runnable {

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }
}
