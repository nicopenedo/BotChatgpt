package com.bottrading.research.ga;

import com.bottrading.research.ga.io.GenomeSection;
import com.bottrading.strategy.CompositeStrategy;
import com.bottrading.strategy.Signal;
import com.bottrading.strategy.signals.BollingerBandsSignal;
import com.bottrading.strategy.signals.EmaCrossoverSignal;
import com.bottrading.strategy.signals.MacdSignal;
import com.bottrading.strategy.signals.RsiSignal;
import com.bottrading.strategy.signals.SmaCrossoverSignal;
import com.bottrading.strategy.signals.SupertrendSignal;
import com.bottrading.research.backtest.MetricsSummary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Genome {

  private static final String[] SIGNAL_TYPES = {
    "SMA_CROSS", "EMA_CROSS", "MACD", "RSI", "BOLLINGER", "SUPERTREND"
  };

  private final Map<String, Gene> genes = new HashMap<>();
  private double buyThreshold;
  private double sellThreshold;
  private double fitness;
  private MetricsSummary metrics;

  public Genome(Random random) {
    for (String type : SIGNAL_TYPES) {
      genes.put(type, randomGene(type, random));
    }
    this.buyThreshold = 1.0 + random.nextDouble();
    this.sellThreshold = 1.0 + random.nextDouble();
  }

  private Genome(Map<String, Gene> genes, double buyThreshold, double sellThreshold) {
    this.genes.putAll(genes);
    this.buyThreshold = buyThreshold;
    this.sellThreshold = sellThreshold;
  }

  static Genome fromGenes(Map<String, Gene> genes, double buyThreshold, double sellThreshold) {
    return new Genome(genes, buyThreshold, sellThreshold);
  }

  public CompositeStrategy toStrategy() {
    CompositeStrategy strategy = new CompositeStrategy().thresholds(buyThreshold, sellThreshold);
    for (Map.Entry<String, Gene> entry : genes.entrySet()) {
      Gene gene = entry.getValue();
      if (!gene.enabled) {
        continue;
      }
      Signal signal = createSignal(entry.getKey(), gene);
      if (signal != null) {
        strategy.addSignal(signal, gene.weight);
      }
    }
    return strategy;
  }

  public Map<String, Gene> genes() {
    return genes;
  }

  public GenomeSection toBuySection() {
    return toSection(buyThreshold);
  }

  public GenomeSection toSellSection() {
    return toSection(sellThreshold);
  }

  public double buyThreshold() {
    return buyThreshold;
  }

  public double sellThreshold() {
    return sellThreshold;
  }

  public double fitness() {
    return fitness;
  }

  public void fitness(double fitness) {
    this.fitness = fitness;
  }

  public MetricsSummary metrics() {
    return metrics;
  }

  public void metrics(MetricsSummary metrics) {
    this.metrics = metrics;
  }

  public Genome copy() {
    Map<String, Gene> copy = new HashMap<>();
    for (Map.Entry<String, Gene> entry : genes.entrySet()) {
      copy.put(entry.getKey(), entry.getValue().copy());
    }
    return new Genome(copy, buyThreshold, sellThreshold);
  }

  public int activeSignals() {
    int count = 0;
    for (Gene gene : genes.values()) {
      if (gene.enabled) {
        count++;
      }
    }
    return count;
  }

  public void mutate(Random random, double mutationRate) {
    for (Gene gene : genes.values()) {
      if (random.nextDouble() < mutationRate) {
        gene.enabled = !gene.enabled;
      }
      if (random.nextDouble() < mutationRate) {
        gene.weight = clamp(gene.weight + random.nextGaussian() * 0.1, 0.2, 1.5);
      }
      if (random.nextDouble() < mutationRate) {
        gene.confidence = clamp(gene.confidence + random.nextGaussian() * 0.1, 0.3, 1.0);
      }
      if (random.nextDouble() < mutationRate) {
        tweakParameters(gene, random);
      }
    }
    if (random.nextDouble() < mutationRate) {
      buyThreshold = clamp(buyThreshold + random.nextGaussian() * 0.2, 0.5, 3.0);
    }
    if (random.nextDouble() < mutationRate) {
      sellThreshold = clamp(sellThreshold + random.nextGaussian() * 0.2, 0.5, 3.0);
    }
  }

  private void tweakParameters(Gene gene, Random random) {
    gene.params.replaceAll((k, v) -> v + random.nextGaussian());
  }

  private GenomeSection toSection(double threshold) {
    Map<String, Double> weights = new LinkedHashMap<>();
    Map<String, Double> confidences = new LinkedHashMap<>();
    Map<String, Map<String, Double>> params = new LinkedHashMap<>();
    java.util.List<String> enabled = new ArrayList<>();
    for (Map.Entry<String, Gene> entry : genes.entrySet()) {
      String type = entry.getKey();
      Gene gene = entry.getValue();
      weights.put(type, gene.weight);
      confidences.put(type, gene.confidence);
      params.put(type, new LinkedHashMap<>(gene.params));
      if (gene.enabled) {
        enabled.add(type);
      }
    }
    return new GenomeSection(threshold, enabled, weights, confidences, params);
  }

  private Signal createSignal(String type, Gene gene) {
    return switch (type) {
      case "SMA_CROSS" ->
          new SmaCrossoverSignal(
              (int) Math.round(gene.params.getOrDefault("fast", 9.0)),
              (int) Math.round(gene.params.getOrDefault("slow", 21.0)),
              gene.confidence);
      case "EMA_CROSS" ->
          new EmaCrossoverSignal(
              (int) Math.round(gene.params.getOrDefault("fast", 12.0)),
              (int) Math.round(gene.params.getOrDefault("slow", 26.0)),
              gene.confidence);
      case "MACD" ->
          new MacdSignal(
              (int) Math.round(gene.params.getOrDefault("fast", 12.0)),
              (int) Math.round(gene.params.getOrDefault("slow", 26.0)),
              (int) Math.round(gene.params.getOrDefault("signal", 9.0)),
              gene.confidence);
      case "RSI" ->
          new RsiSignal(
              (int) Math.round(gene.params.getOrDefault("period", 14.0)),
              gene.params.getOrDefault("lower", 30.0),
              gene.params.getOrDefault("upper", 70.0),
              (int) Math.round(gene.params.getOrDefault("trendSma", 50.0)),
              gene.confidence);
      case "BOLLINGER" ->
          new BollingerBandsSignal(
              (int) Math.round(gene.params.getOrDefault("period", 20.0)),
              gene.params.getOrDefault("stdDevs", 2.0),
              gene.confidence);
      case "SUPERTREND" ->
          new SupertrendSignal(
              (int) Math.round(gene.params.getOrDefault("atrPeriod", 10.0)),
              gene.params.getOrDefault("multiplier", 3.0),
              gene.confidence);
      default -> null;
    };
  }

  private Gene randomGene(String type, Random random) {
    Gene gene = new Gene();
    gene.enabled = random.nextBoolean();
    gene.weight = 0.5 + random.nextDouble();
    gene.confidence = 0.5 + random.nextDouble() * 0.5;
    switch (type) {
      case "SMA_CROSS" -> {
        gene.params.put("fast", 5.0 + random.nextInt(10));
        gene.params.put("slow", 15.0 + random.nextInt(30));
      }
      case "EMA_CROSS" -> {
        gene.params.put("fast", 5.0 + random.nextInt(10));
        gene.params.put("slow", 15.0 + random.nextInt(30));
      }
      case "MACD" -> {
        gene.params.put("fast", 8.0 + random.nextInt(6));
        gene.params.put("slow", 20.0 + random.nextInt(10));
        gene.params.put("signal", 5.0 + random.nextInt(5));
      }
      case "RSI" -> {
        gene.params.put("period", 7.0 + random.nextInt(10));
        gene.params.put("lower", 25.0 + random.nextDouble() * 10);
        gene.params.put("upper", 60.0 + random.nextDouble() * 10);
        gene.params.put("trendSma", 30.0 + random.nextInt(40));
      }
      case "BOLLINGER" -> {
        gene.params.put("period", 10.0 + random.nextInt(10));
        gene.params.put("stdDevs", 1.5 + random.nextDouble());
      }
      case "SUPERTREND" -> {
        gene.params.put("atrPeriod", 7.0 + random.nextInt(7));
        gene.params.put("multiplier", 2.0 + random.nextDouble() * 2);
      }
      default -> {
      }
    }
    return gene;
  }

  private double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}
