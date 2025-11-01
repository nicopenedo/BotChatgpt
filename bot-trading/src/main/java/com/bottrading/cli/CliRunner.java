package com.bottrading.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.spring.boot.PicocliCommandLineFactory;

@Component
public class CliRunner implements CommandLineRunner {

  private final BotRootCommand rootCommand;
  private final PicocliCommandLineFactory commandLineFactory;

  public CliRunner(BotRootCommand rootCommand, PicocliCommandLineFactory commandLineFactory) {
    this.rootCommand = rootCommand;
    this.commandLineFactory = commandLineFactory;
  }

  @Override
  public void run(String... args) {
    if (args.length == 0) {
      return;
    }
    if (!rootCommand.handles(args[0])) {
      return;
    }
    CommandLine commandLine = commandLineFactory.create(rootCommand);
    int exitCode = commandLine.execute(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    } else {
      System.exit(0);
    }
  }
}
