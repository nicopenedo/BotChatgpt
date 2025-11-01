package com.bottrading.cli.preset;

import com.bottrading.model.enums.OrderSide;
import com.bottrading.service.preset.PresetService;
import com.bottrading.service.preset.PresetService.BacktestMetadata;
import com.bottrading.service.preset.PresetService.PresetImportRequest;
import com.bottrading.research.regime.RegimeTrend;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "import", description = "Import preset artifacts")
public class PresetsImportCommand implements Runnable {

  @CommandLine.Option(names = "--run-id", required = false)
  private String runId;

  @CommandLine.Option(names = "--regime", required = true)
  private String regime;

  @CommandLine.Option(names = "--side", required = true)
  private String side;

  @CommandLine.Option(names = "--params", required = true)
  private Path params;

  @CommandLine.Option(names = "--signals", required = false)
  private Path signals;

  @CommandLine.Option(names = "--metrics", required = false)
  private Path metrics;

  @CommandLine.Option(names = "--symbol", required = false)
  private String symbol;

  @CommandLine.Option(names = "--interval", required = false)
  private String interval;

  @CommandLine.Option(names = "--ts-from", required = false)
  private String tsFrom;

  @CommandLine.Option(names = "--ts-to", required = false)
  private String tsTo;

  @CommandLine.Option(names = "--regime-mask", required = false)
  private String regimeMask;

  @CommandLine.Option(names = "--ga-pop", required = false)
  private Integer gaPop;

  @CommandLine.Option(names = "--ga-gens", required = false)
  private Integer gaGens;

  @CommandLine.Option(names = "--fitness", required = false)
  private String fitness;

  @CommandLine.Option(names = "--seed", required = false)
  private Long seed;

  @CommandLine.Option(names = "--code-sha", required = false)
  private String codeSha;

  @CommandLine.Option(names = "--data-hash", required = false)
  private String dataHash;

  @CommandLine.Option(names = "--labels-hash", required = false)
  private String labelsHash;

  private final PresetService presetService;
  private final ObjectMapper jsonMapper = new ObjectMapper();
  private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

  public PresetsImportCommand(PresetService presetService) {
    this.presetService = presetService;
  }

  @Override
  public void run() {
    try {
      Map<String, Object> paramsJson = readStructured(params);
      Map<String, Object> signalsJson = signals != null ? readStructured(signals) : Map.of();
      Map<String, Object> oosMetrics = metrics != null ? readStructured(metrics) : Map.of();
      BacktestMetadata backtest = null;
      if (runId != null) {
        backtest =
            new BacktestMetadata(
                runId,
                symbol,
                interval,
                parseInstant(tsFrom),
                parseInstant(tsTo),
                regimeMask,
                gaPop,
                gaGens,
                fitness,
                seed,
                Map.of(),
                codeSha,
                dataHash,
                labelsHash);
      }
      PresetImportRequest request =
          new PresetImportRequest(
              RegimeTrend.valueOf(regime.toUpperCase()),
              OrderSide.valueOf(side.toUpperCase()),
              paramsJson,
              signalsJson,
              oosMetrics,
              backtest,
              codeSha,
              dataHash,
              labelsHash);
      var preset = presetService.importPreset(request);
      System.out.printf("Imported preset %s\n", preset.getId());
    } catch (IOException ex) {
      throw new CommandLine.ExecutionException(new CommandLine(this), "Failed to read files", ex);
    }
  }

  private Map<String, Object> readStructured(Path path) throws IOException {
    if (path == null) {
      return Map.of();
    }
    ObjectMapper mapper = isYaml(path) ? yamlMapper : jsonMapper;
    try (InputStream inputStream = Files.newInputStream(path)) {
      return mapper.readValue(inputStream, new TypeReference<Map<String, Object>>() {});
    }
  }

  private boolean isYaml(Path path) {
    String filename = path.getFileName().toString().toLowerCase();
    return filename.endsWith(".yml") || filename.endsWith(".yaml");
  }

  private Instant parseInstant(String value) {
    return value != null ? Instant.parse(value) : null;
  }
}
