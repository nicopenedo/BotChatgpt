package com.bottrading.research.ga;

import java.util.HashMap;
import java.util.Map;

class Gene {
  boolean enabled;
  double weight;
  double confidence;
  Map<String, Double> params = new HashMap<>();

  Gene copy() {
    Gene gene = new Gene();
    gene.enabled = this.enabled;
    gene.weight = this.weight;
    gene.confidence = this.confidence;
    gene.params = new HashMap<>(this.params);
    return gene;
  }
}
