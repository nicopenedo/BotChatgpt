package com.bottrading.cli.preset;

import com.bottrading.service.preset.PresetService;
import java.util.UUID;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "retire", description = "Retire a preset")
public class PresetsRetireCommand implements Runnable {

  @CommandLine.Option(names = "--preset-id", required = true)
  private UUID presetId;

  private final PresetService presetService;

  public PresetsRetireCommand(PresetService presetService) {
    this.presetService = presetService;
  }

  @Override
  public void run() {
    var preset = presetService.retirePreset(presetId, "cli");
    System.out.printf("Retired preset %s\n", preset.getId());
  }
}
