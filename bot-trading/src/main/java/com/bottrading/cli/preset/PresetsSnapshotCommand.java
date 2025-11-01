package com.bottrading.cli.preset;

import com.bottrading.model.enums.SnapshotWindow;
import com.bottrading.service.snapshot.SnapshotService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "snapshot-live", description = "Create live snapshot")
public class PresetsSnapshotCommand implements Runnable {

  @CommandLine.Option(names = "--preset-id", required = true)
  private UUID presetId;

  @CommandLine.Option(names = "--window", required = true)
  private String window;

  @CommandLine.Option(names = "--live", required = false)
  private Path live;

  @CommandLine.Option(names = "--shadow", required = false)
  private Path shadow;

  @CommandLine.Option(names = "--oos", required = false)
  private Path oos;

  private final SnapshotService snapshotService;
  private final ObjectMapper mapper = new ObjectMapper();

  public PresetsSnapshotCommand(SnapshotService snapshotService) {
    this.snapshotService = snapshotService;
  }

  @Override
  public void run() {
    try {
      var snapshot =
          snapshotService.createSnapshot(
              presetId,
              SnapshotWindow.fromLabel(window),
              read(oos),
              read(shadow),
              read(live));
      System.out.printf("Snapshot %s created\n", snapshot.getId());
    } catch (IOException ex) {
      throw new CommandLine.ExecutionException(new CommandLine(this), "Failed to read metrics", ex);
    }
  }

  private Map<String, Object> read(Path path) throws IOException {
    if (path == null) {
      return Map.of();
    }
    try (InputStream inputStream = Files.newInputStream(path)) {
      return mapper.readValue(inputStream, new TypeReference<Map<String, Object>>() {});
    }
  }
}
