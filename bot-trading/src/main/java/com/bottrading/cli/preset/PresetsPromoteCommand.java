package com.bottrading.cli.preset;

import com.bottrading.model.enums.PresetActivationMode;
import com.bottrading.service.preset.PresetService;
import java.util.UUID;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "promote", description = "Promote a preset to active")
public class PresetsPromoteCommand implements Runnable {

  @CommandLine.Option(names = "--preset-id", required = true)
  private UUID presetId;

  @CommandLine.Option(names = "--mode", defaultValue = "full")
  private String mode;

  private final PresetService presetService;

  public PresetsPromoteCommand(PresetService presetService) {
    this.presetService = presetService;
  }

  @Override
  public void run() {
    PresetActivationMode activationMode =
        "canary".equalsIgnoreCase(mode) ? PresetActivationMode.CANARY : PresetActivationMode.FULL;
    var preset = presetService.activatePreset(presetId, activationMode, "cli");
    System.out.printf("Activated preset %s (%s)\n", preset.getId(), activationMode);
  }
}
