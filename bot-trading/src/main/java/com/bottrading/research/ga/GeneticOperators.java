package com.bottrading.research.ga;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class GeneticOperators {

  private GeneticOperators() {}

  public static Genome crossover(Genome a, Genome b, Random random) {
    Map<String, Gene> genes = new HashMap<>();
    for (String type : a.genes().keySet()) {
      Gene gene = random.nextBoolean() ? a.genes().get(type).copy() : b.genes().get(type).copy();
      genes.put(type, gene);
    }
    double buy = random.nextBoolean() ? a.buyThreshold() : b.buyThreshold();
    double sell = random.nextBoolean() ? a.sellThreshold() : b.sellThreshold();
    return Genome.fromGenes(genes, buy, sell);
  }

  public static void mutate(Genome genome, Random random, double mutationRate) {
    genome.mutate(random, mutationRate);
  }

  public static Genome tournamentSelection(Population population, Random random, int tournamentSize) {
    Genome best = null;
    for (int i = 0; i < tournamentSize; i++) {
      Genome candidate = population.genomes().get(random.nextInt(population.genomes().size()));
      if (best == null || candidate.fitness() > best.fitness()) {
        best = candidate;
      }
    }
    return best;
  }
}
