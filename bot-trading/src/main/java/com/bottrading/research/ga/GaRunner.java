package com.bottrading.research.ga;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GaRunner {

  private final Evaluator evaluator;
  private final int populationSize;
  private final int generations;
  private final double mutationRate;
  private final int tournamentSize;
  private final int elitism;
  private final long seed;

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
    }
    return population.best();
  }
}
