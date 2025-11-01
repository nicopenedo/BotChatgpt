package com.bottrading.research.ga.io;

import java.util.List;
import java.util.Map;

public record GenomeSection(
    double threshold,
    List<String> enabledSignals,
    Map<String, Double> weights,
    Map<String, Double> confidences,
    Map<String, Map<String, Double>> params) {

  public GenomeSection {
    enabledSignals = enabledSignals == null ? List.of() : List.copyOf(enabledSignals);
    weights = weights == null ? Map.of() : Map.copyOf(weights);
    confidences = confidences == null ? Map.of() : Map.copyOf(confidences);
    if (params == null) {
      params = Map.of();
    } else {
      params = params.entrySet().stream()
          .collect(
              java.util.stream.Collectors.toUnmodifiableMap(
                  Map.Entry::getKey,
                  e -> Map.copyOf(e.getValue())));
    }
  }

  public Map<String, Double> paramsFor(String signal) {
    return params.getOrDefault(signal, Map.of());
  }
}
