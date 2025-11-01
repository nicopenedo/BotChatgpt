package com.bottrading.cli.preset;

import com.bottrading.model.enums.OrderSide;
import com.bottrading.research.regime.RegimeTrend;
import com.bottrading.service.preset.PresetService;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "rollback", description = "Rollback to previous active preset")
public class PresetsRollbackCommand implements Runnable {

  @CommandLine.Option(names = "--regime", required = true)
  private String regime;

  @CommandLine.Option(names = "--side", required = true)
  private String side;

  private final PresetService presetService;

  public PresetsRollbackCommand(PresetService presetService) {
    this.presetService = presetService;
  }

  @Override
  public void run() {
    var preset =
        presetService.rollback(
            RegimeTrend.valueOf(regime.toUpperCase()), OrderSide.valueOf(side.toUpperCase()), "cli");
    System.out.printf("Rolled back to preset %s\n", preset.getId());
  }
}
