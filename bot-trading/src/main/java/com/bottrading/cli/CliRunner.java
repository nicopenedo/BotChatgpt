package com.bottrading.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
public class CliRunner implements CommandLineRunner {

  private final BotRootCommand rootCommand;
  private final CommandLine.IFactory factory;

  public CliRunner(BotRootCommand rootCommand, CommandLine.IFactory factory) {
    this.rootCommand = rootCommand;
    this.factory = factory;
  }

  @Override
  public void run(String... args) {
    if (args.length == 0) {
      return;
    }
    if (!rootCommand.handles(args[0])) {
      return;
    }
    CommandLine commandLine = new CommandLine(rootCommand, factory);
    int exitCode = commandLine.execute(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    } else {
      System.exit(0);
    }
  }
}
