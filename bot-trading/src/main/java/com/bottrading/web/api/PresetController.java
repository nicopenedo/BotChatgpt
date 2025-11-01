package com.bottrading.web.api;

import com.bottrading.model.entity.EvaluationSnapshot;
import com.bottrading.model.entity.PresetVersion;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PresetActivationMode;
import com.bottrading.model.enums.PresetStatus;
import com.bottrading.model.enums.SnapshotWindow;
import com.bottrading.service.preset.PresetService;
import com.bottrading.service.preset.PresetService.BacktestMetadata;
import com.bottrading.service.preset.PresetService.PresetImportRequest;
import com.bottrading.service.snapshot.SnapshotService;
import com.bottrading.research.regime.RegimeTrend;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/presets")
@PreAuthorize("hasRole('ADMIN')")
public class PresetController {

  private final PresetService presetService;
  private final SnapshotService snapshotService;
  private final ObjectMapper jsonMapper;
  private final ObjectMapper yamlMapper;

  public PresetController(PresetService presetService, SnapshotService snapshotService) {
    this.presetService = presetService;
    this.snapshotService = snapshotService;
    this.jsonMapper = new ObjectMapper();
    this.yamlMapper = new ObjectMapper(new YAMLFactory());
  }

  @GetMapping
  public List<PresetVersion> list(
      @RequestParam(value = "regime", required = false) String regime,
      @RequestParam(value = "side", required = false) String side,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "page", required = false) Integer page,
      @RequestParam(value = "size", required = false) Integer size) {
    RegimeTrend regimeTrend = regime != null ? RegimeTrend.valueOf(regime.toUpperCase()) : null;
    OrderSide orderSide = side != null ? OrderSide.valueOf(side.toUpperCase()) : null;
    PresetStatus presetStatus = status != null ? PresetStatus.valueOf(status.toUpperCase()) : null;
    List<PresetVersion> presets = presetService.listPresets(regimeTrend, orderSide, presetStatus);
    if (page != null && size != null && size > 0) {
      int from = Math.min(page * size, presets.size());
      int to = Math.min(from + size, presets.size());
      return presets.subList(from, to);
    }
    return presets;
  }

  @GetMapping("/{id}")
  public PresetVersion get(@PathVariable("id") UUID id) {
    return presetService.getPreset(id);
  }

  @GetMapping("/{id}/snapshots")
  public List<EvaluationSnapshot> snapshots(@PathVariable("id") UUID id) {
    return presetService.snapshots(id);
  }

  @PostMapping(
      value = "/import",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public PresetVersion importPreset(
      Authentication authentication,
      @RequestParam("regime") String regime,
      @RequestParam("side") String side,
      @RequestParam("params") MultipartFile params,
      @RequestParam(value = "signals", required = false) MultipartFile signals,
      @RequestParam(value = "metrics", required = false) MultipartFile metrics,
      @RequestParam(value = "runId", required = false) String runId,
      @RequestParam(value = "symbol", required = false) String symbol,
      @RequestParam(value = "interval", required = false) String interval,
      @RequestParam(value = "tsFrom", required = false) String tsFrom,
      @RequestParam(value = "tsTo", required = false) String tsTo,
      @RequestParam(value = "regimeMask", required = false) String regimeMask,
      @RequestParam(value = "gaPop", required = false) Integer gaPop,
      @RequestParam(value = "gaGens", required = false) Integer gaGens,
      @RequestParam(value = "fitness", required = false) String fitness,
      @RequestParam(value = "seed", required = false) Long seed,
      @RequestParam(value = "codeSha", required = false) String codeSha,
      @RequestParam(value = "dataHash", required = false) String dataHash,
      @RequestParam(value = "labelsHash", required = false) String labelsHash)
      throws IOException {
    Map<String, Object> paramsJson = readStructuredFile(params).orElse(Map.of());
    Map<String, Object> signalsJson =
        signals != null ? readStructuredFile(signals).orElse(Map.of()) : Map.of();
    Map<String, Object> oosMetrics = metrics != null ? readStructuredFile(metrics).orElse(Map.of()) : Map.of();
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
    return presetService.importPreset(request);
  }

  @PostMapping("/{id}/activate")
  public PresetVersion activate(
      Authentication authentication,
      @PathVariable("id") UUID id,
      @RequestParam(value = "mode", defaultValue = "full") String mode) {
    PresetActivationMode activationMode =
        "canary".equalsIgnoreCase(mode) ? PresetActivationMode.CANARY : PresetActivationMode.FULL;
    return presetService.activatePreset(id, activationMode, authentication.getName());
  }

  @PostMapping("/{id}/retire")
  public PresetVersion retire(Authentication authentication, @PathVariable("id") UUID id) {
    return presetService.retirePreset(id, authentication.getName());
  }

  @PostMapping("/{id}/rollback")
  public PresetVersion rollback(Authentication authentication, @PathVariable("id") UUID id) {
    PresetVersion preset = presetService.getPreset(id);
    return presetService.rollback(preset.getRegime(), preset.getSide(), authentication.getName());
  }

  @PostMapping("/{id}/snapshot-live")
  public EvaluationSnapshot createSnapshot(
      @PathVariable("id") UUID id,
      @RequestParam("window") String window,
      @RequestBody SnapshotPayload payload) {
    SnapshotWindow snapshotWindow = SnapshotWindow.fromLabel(window);
    return snapshotService.createSnapshot(
        id,
        snapshotWindow,
        payload != null && payload.oosMetrics() != null ? payload.oosMetrics() : Map.of(),
        payload != null && payload.shadowMetrics() != null ? payload.shadowMetrics() : Map.of(),
        payload != null && payload.liveMetrics() != null ? payload.liveMetrics() : Map.of());
  }

  private Optional<Map<String, Object>> readStructuredFile(MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) {
      return Optional.empty();
    }
    ObjectMapper mapper = isYaml(file.getOriginalFilename()) ? yamlMapper : jsonMapper;
    try (InputStream inputStream = file.getInputStream()) {
      return Optional.ofNullable(mapper.readValue(inputStream, new TypeReference<Map<String, Object>>() {}));
    }
  }

  private boolean isYaml(String filename) {
    if (filename == null) {
      return false;
    }
    String lower = filename.toLowerCase();
    return lower.endsWith(".yml") || lower.endsWith(".yaml");
  }

  private Instant parseInstant(String value) {
    return value != null ? Instant.parse(value) : null;
  }

  public record SnapshotPayload(
      Map<String, Object> oosMetrics,
      Map<String, Object> shadowMetrics,
      Map<String, Object> liveMetrics) {}
}
