package com.bottrading.research.ga;

// FIX: Replace method reference causing type inference issues on Java 21.

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Population {

  private final List<Genome> genomes = new ArrayList<>();

  public void add(Genome genome) {
    genomes.add(genome);
  }

  public List<Genome> genomes() {
    return genomes;
  }

  public void sort() {
    genomes.sort((g1, g2) -> Double.compare(g2.fitness(), g1.fitness()));
  }

  public Genome best() {
    return genomes.isEmpty() ? null : genomes.get(0);
  }
}
