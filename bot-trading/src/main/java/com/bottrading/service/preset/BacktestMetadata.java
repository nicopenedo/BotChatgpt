package com.bottrading.service.preset;

import java.time.Instant;
import java.util.Map;

public class BacktestMetadata {
  private String runId;
  private String symbol;
  private String interval;
  private Instant from;
  private Instant to;
  private long trades;
  private double sharpe;
  private double maxDrawdown;
  private double profitFactor;
  private Instant tsFrom;
  private Instant tsTo;
  private String regimeMask;
  private Integer gaPopulation;
  private Integer gaGenerations;
  private String fitnessDefinition;
  private Long seed;
  private Map<String, Object> perSplitMetrics;
  private String codeSha;
  private String dataHash;
  private String labelsHash;

  public BacktestMetadata() {}

  public BacktestMetadata(
      String runId,
      String symbol,
      String interval,
      Instant tsFrom,
      Instant tsTo,
      String regimeMask,
      Integer gaPopulation,
      Integer gaGenerations,
      String fitnessDefinition,
      Long seed,
      Map<String, Object> perSplitMetrics,
      String codeSha,
      String dataHash,
      String labelsHash) {
    this.runId = runId;
    this.symbol = symbol;
    this.interval = interval;
    this.tsFrom = tsFrom;
    this.tsTo = tsTo;
    this.regimeMask = regimeMask;
    this.gaPopulation = gaPopulation;
    this.gaGenerations = gaGenerations;
    this.fitnessDefinition = fitnessDefinition;
    this.seed = seed;
    this.perSplitMetrics = perSplitMetrics;
    this.codeSha = codeSha;
    this.dataHash = dataHash;
    this.labelsHash = labelsHash;
  }

  public String getRunId() {
    return runId;
  }

  public void setRunId(String runId) {
    this.runId = runId;
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public String getInterval() {
    return interval;
  }

  public void setInterval(String interval) {
    this.interval = interval;
  }

  public Instant getFrom() {
    return from;
  }

  public void setFrom(Instant from) {
    this.from = from;
  }

  public Instant getTo() {
    return to;
  }

  public void setTo(Instant to) {
    this.to = to;
  }

  public long getTrades() {
    return trades;
  }

  public void setTrades(long trades) {
    this.trades = trades;
  }

  public double getSharpe() {
    return sharpe;
  }

  public void setSharpe(double sharpe) {
    this.sharpe = sharpe;
  }

  public double getMaxDrawdown() {
    return maxDrawdown;
  }

  public void setMaxDrawdown(double maxDrawdown) {
    this.maxDrawdown = maxDrawdown;
  }

  public double getProfitFactor() {
    return profitFactor;
  }

  public void setProfitFactor(double profitFactor) {
    this.profitFactor = profitFactor;
  }

  public Instant getTsFrom() {
    return tsFrom;
  }

  public void setTsFrom(Instant tsFrom) {
    this.tsFrom = tsFrom;
  }

  public Instant getTsTo() {
    return tsTo;
  }

  public void setTsTo(Instant tsTo) {
    this.tsTo = tsTo;
  }

  public String getRegimeMask() {
    return regimeMask;
  }

  public void setRegimeMask(String regimeMask) {
    this.regimeMask = regimeMask;
  }

  public Integer getGaPopulation() {
    return gaPopulation;
  }

  public void setGaPopulation(Integer gaPopulation) {
    this.gaPopulation = gaPopulation;
  }

  public Integer getGaGenerations() {
    return gaGenerations;
  }

  public void setGaGenerations(Integer gaGenerations) {
    this.gaGenerations = gaGenerations;
  }

  public String getFitnessDefinition() {
    return fitnessDefinition;
  }

  public void setFitnessDefinition(String fitnessDefinition) {
    this.fitnessDefinition = fitnessDefinition;
  }

  public Long getSeed() {
    return seed;
  }

  public void setSeed(Long seed) {
    this.seed = seed;
  }

  public Map<String, Object> getPerSplitMetrics() {
    return perSplitMetrics;
  }

  public void setPerSplitMetrics(Map<String, Object> perSplitMetrics) {
    this.perSplitMetrics = perSplitMetrics;
  }

  public String getCodeSha() {
    return codeSha;
  }

  public void setCodeSha(String codeSha) {
    this.codeSha = codeSha;
  }

  public String getDataHash() {
    return dataHash;
  }

  public void setDataHash(String dataHash) {
    this.dataHash = dataHash;
  }

  public String getLabelsHash() {
    return labelsHash;
  }

  public void setLabelsHash(String labelsHash) {
    this.labelsHash = labelsHash;
  }
}
