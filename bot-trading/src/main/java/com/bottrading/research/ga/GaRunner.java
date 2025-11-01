package com.bottrading.research.ga;

import com.bottrading.research.backtest.MetricsSummary;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class GaRunner {

  private final Evaluator evaluator;
  private final int populationSize;
  private final int generations;
  private final double mutationRate;
  private final int tournamentSize;
  private final int elitism;
  private final long seed;
  private final List<GaListener> listeners = new ArrayList<>();

  public GaRunner(
      Evaluator evaluator,
      int populationSize,
      int generations,
      double mutationRate,
      int tournamentSize,
      int elitism,
      long seed) {
    this.evaluator = evaluator;
    this.populationSize = populationSize;
    this.generations = generations;
    this.mutationRate = mutationRate;
    this.tournamentSize = tournamentSize;
    this.elitism = elitism;
    this.seed = seed;
  }

  public Genome run() throws InterruptedException {
    Random random = new Random(seed);
    Population population = new Population();
    for (int i = 0; i < populationSize; i++) {
      population.add(new Genome(random));
    }
    evaluator.evaluate(population.genomes());
    population.sort();
    notifyListeners(0, population);

    for (int gen = 0; gen < generations; gen++) {
      List<Genome> nextGen = new ArrayList<>();
      for (int i = 0; i < Math.min(elitism, population.genomes().size()); i++) {
        nextGen.add(population.genomes().get(i).copy());
      }
      while (nextGen.size() < populationSize) {
        Genome parentA = GeneticOperators.tournamentSelection(population, random, tournamentSize);
        Genome parentB = GeneticOperators.tournamentSelection(population, random, tournamentSize);
        Genome child = GeneticOperators.crossover(parentA, parentB, random);
        GeneticOperators.mutate(child, random, mutationRate);
        nextGen.add(child);
      }
      population.genomes().clear();
      population.genomes().addAll(nextGen);
      evaluator.evaluate(population.genomes());
      population.sort();
      notifyListeners(gen + 1, population);
    }
    return population.best();
  }

  public void addListener(GaListener listener) {
    if (listener != null) {
      listeners.add(listener);
    }
  }

  private void notifyListeners(int generation, Population population) {
    if (listeners.isEmpty()) {
      return;
    }
    double max = population.best().fitness();
    double min = population.genomes().get(population.genomes().size() - 1).fitness();
    double avg = population.genomes().stream().mapToDouble(Genome::fitness).average().orElse(0);
    double diversity = computeDiversity(population.genomes());
    ProfitRiskAggregate aggregate = computeProfitRisk(population.genomes());
    String summary = summarize(population.best());
    GenStats stats =
        new GenStats(
            generation,
            max,
            avg,
            min,
            diversity,
            aggregate.max,
            aggregate.avg,
            summary);
    for (GaListener listener : listeners) {
      listener.onGeneration(stats);
    }
  }

  private double computeDiversity(List<Genome> genomes) {
    if (genomes.size() < 2) {
      return 0;
    }
    double total = 0;
    int pairs = 0;
    for (int i = 0; i < genomes.size(); i++) {
      for (int j = i + 1; j < genomes.size(); j++) {
        total += genomeDistance(genomes.get(i), genomes.get(j));
        pairs++;
      }
    }
    return pairs == 0 ? 0 : total / pairs;
  }

  private double genomeDistance(Genome a, Genome b) {
    double diff =
        Math.abs(a.buyThreshold() - b.buyThreshold()) + Math.abs(a.sellThreshold() - b.sellThreshold());
    Set<String> keys = new HashSet<>();
    keys.addAll(a.genes().keySet());
    keys.addAll(b.genes().keySet());
    for (String key : keys) {
      Gene ga = a.genes().get(key);
      Gene gb = b.genes().get(key);
      if (ga == null || gb == null) {
        diff += 1;
        continue;
      }
      diff += Math.abs(ga.weight() - gb.weight());
      diff += Math.abs(ga.confidence() - gb.confidence());
      diff += ga.enabled() == gb.enabled() ? 0 : 1;
      diff += parameterDistance(ga.params(), gb.params());
    }
    return diff;
  }

  private double parameterDistance(Map<String, Double> a, Map<String, Double> b) {
    Set<String> keys = new HashSet<>();
    keys.addAll(a.keySet());
    keys.addAll(b.keySet());
    double diff = 0;
    for (String key : keys) {
      diff += Math.abs(a.getOrDefault(key, 0.0) - b.getOrDefault(key, 0.0));
    }
    return diff;
  }

  private ProfitRiskAggregate computeProfitRisk(List<Genome> genomes) {
    double sum = 0;
    double max = Double.NEGATIVE_INFINITY;
    int count = 0;
    for (Genome genome : genomes) {
      MetricsSummary metrics = genome.metrics();
      if (metrics == null
          || metrics.expectancy() == null
          || metrics.maxDrawdown() == null
          || metrics.maxDrawdown().doubleValue() == 0) {
        continue;
      }
      double ratio =
          metrics.expectancy().doubleValue()
              / Math.abs(metrics.maxDrawdown().doubleValue());
      sum += ratio;
      count++;
      if (ratio > max) {
        max = ratio;
      }
    }
    double avg = count == 0 ? 0 : sum / count;
    if (count == 0) {
      max = 0;
    }
    return new ProfitRiskAggregate(avg, max);
  }

  private String summarize(Genome genome) {
    String signals =
        genome.genes().entrySet().stream()
            .filter(e -> e.getValue().enabled())
            .map(e -> e.getKey() + String.format("(%.2f)", e.getValue().weight()))
            .collect(Collectors.joining(","));
    return String.format(
        "thrB=%.2f thrS=%.2f signals=[%s]", genome.buyThreshold(), genome.sellThreshold(), signals);
  }

  private record ProfitRiskAggregate(double avg, double max) {}
}
