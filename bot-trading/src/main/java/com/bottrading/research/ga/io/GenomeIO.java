package com.bottrading.research.ga.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.yaml.snakeyaml.Yaml;

public final class GenomeIO {

  private GenomeIO() {}

  public static GenomeFile read(Path path) throws IOException {
    Objects.requireNonNull(path, "path");
    if (!Files.exists(path)) {
      throw new IOException("Genome file not found: " + path);
    }
    try (InputStream input = Files.newInputStream(path)) {
      Yaml yaml = new Yaml();
      Object root = yaml.load(input);
      if (!(root instanceof Map<?, ?> rootMap)) {
        throw new IOException("Invalid genome file structure");
      }
      Map<String, Object> genomes = map(rootMap.get("genomes"));
      GenomeSection buy = parseSection(map(genomes.get("buy")));
      GenomeSection sell = parseSection(map(genomes.get("sell")));
      return new GenomeFile(buy, sell);
    }
  }

  public static void write(Path path, GenomeFile file) throws IOException {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(file, "file");
    if (path.getParent() != null && !Files.exists(path.getParent())) {
      Files.createDirectories(path.getParent());
    }
    Map<String, Object> root = new LinkedHashMap<>();
    Map<String, Object> genomes = new LinkedHashMap<>();
    root.put("genomes", genomes);
    genomes.put("buy", sectionToMap(file.buy()));
    genomes.put("sell", sectionToMap(file.sell()));
    Yaml yaml = new Yaml();
    try (OutputStream output = Files.newOutputStream(path)) {
      yaml.dump(root, new java.io.OutputStreamWriter(output));
    }
  }

  private static Map<String, Object> sectionToMap(GenomeSection section) {
    Map<String, Object> map = new LinkedHashMap<>();
    if (section == null) {
      return map;
    }
    map.put("threshold", section.threshold());
    map.put("enabled_signals", new ArrayList<>(section.enabledSignals()));
    map.put("weights", new LinkedHashMap<>(section.weights()));
    map.put("confidences", new LinkedHashMap<>(section.confidences()));
    Map<String, Object> params = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, Double>> entry : section.params().entrySet()) {
      for (Map.Entry<String, Double> param : entry.getValue().entrySet()) {
        String key = entry.getKey().toUpperCase(Locale.ROOT) + "_" + param.getKey();
        params.put(key, param.getValue());
      }
    }
    map.put("params", params);
    return map;
  }

  private static GenomeSection parseSection(Map<String, Object> sectionMap) {
    if (sectionMap == null) {
      return new GenomeSection(1.0, List.of(), Map.of(), Map.of(), Map.of());
    }
    double threshold = toDouble(sectionMap.getOrDefault("threshold", 1.0));
    List<String> enabledSignals = list(sectionMap.get("enabled_signals"));
    Map<String, Double> weights = doublesMap(sectionMap.get("weights"));
    Map<String, Double> confidences = doublesMap(sectionMap.get("confidences"));
    Map<String, Map<String, Double>> params = parseParams(sectionMap.get("params"));
    return new GenomeSection(threshold, enabledSignals, weights, confidences, params);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Map<String, Double>> parseParams(Object value) {
    Map<String, Map<String, Double>> result = new LinkedHashMap<>();
    if (!(value instanceof Map<?, ?> map)) {
      return result;
    }
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String rawKey = String.valueOf(entry.getKey());
      String[] parts = rawKey.split("_", 2);
      if (parts.length != 2) {
        continue;
      }
      String signal = parts[0].toUpperCase(Locale.ROOT);
      String param = parts[1];
      double parsed = toDouble(entry.getValue());
      result.computeIfAbsent(signal, k -> new LinkedHashMap<>()).put(param, parsed);
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Double> doublesMap(Object value) {
    Map<String, Double> map = new LinkedHashMap<>();
    if (value instanceof Map<?, ?> raw) {
      for (Map.Entry<?, ?> entry : raw.entrySet()) {
        map.put(String.valueOf(entry.getKey()).toUpperCase(Locale.ROOT), toDouble(entry.getValue()));
      }
    }
    return map;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    return Map.of();
  }

  @SuppressWarnings("unchecked")
  private static List<String> list(Object value) {
    if (value instanceof List<?> list) {
      List<String> out = new ArrayList<>();
      for (Object item : list) {
        out.add(String.valueOf(item).toUpperCase(Locale.ROOT));
      }
      return out;
    }
    return List.of();
  }

  private static double toDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value instanceof String str) {
      try {
        return Double.parseDouble(str);
      } catch (NumberFormatException ignore) {
        return 0.0;
      }
    }
    return 0.0;
  }
}
