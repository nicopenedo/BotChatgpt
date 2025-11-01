package com.bottrading.research.ga;

public record GenStats(
    int generation,
    double maxFitness,
    double avgFitness,
    double minFitness,
    double diversity,
    double maxProfitRiskRatio,
    double averageProfitRiskRatio,
    String bestGenomeSummary) {}
