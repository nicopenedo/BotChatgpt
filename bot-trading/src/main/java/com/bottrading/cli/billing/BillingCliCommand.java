package com.bottrading.cli.billing;

import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(
    name = "billing",
    description = "Herramientas de billing",
    subcommands = {
      BillingForceStateCommand.class,
      BillingHistoryCommand.class,
      BillingReplayWebhookCommand.class
    })
public class BillingCliCommand implements Runnable {

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }
}
