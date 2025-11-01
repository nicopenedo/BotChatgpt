package com.bottrading.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "backtest_runs")
public class BacktestRun {

  @Id
  @Column(name = "run_id", length = 120)
  private String runId;

  private String symbol;
  private String interval;

  @Column(name = "ts_from")
  private Instant tsFrom;

  @Column(name = "ts_to")
  private Instant tsTo;

  @Column(name = "regime_mask")
  private String regimeMask;

  @Column(name = "ga_pop")
  private Integer gaPopulation;

  @Column(name = "ga_gens")
  private Integer gaGenerations;

  @Column(name = "fitness_def")
  private String fitnessDefinition;

  private Long seed;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "oos_metrics_json", columnDefinition = "jsonb")
  private Map<String, Object> oosMetricsJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "per_split_metrics_json", columnDefinition = "jsonb")
  private Map<String, Object> perSplitMetricsJson;

  @Column(name = "code_sha")
  private String codeSha;

  @Column(name = "data_hash")
  private String dataHash;

  @Column(name = "labels_hash")
  private String labelsHash;

  @Column(name = "created_at")
  private Instant createdAt;

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

  public Map<String, Object> getOosMetricsJson() {
    return oosMetricsJson;
  }

  public void setOosMetricsJson(Map<String, Object> oosMetricsJson) {
    this.oosMetricsJson = oosMetricsJson;
  }

  public Map<String, Object> getPerSplitMetricsJson() {
    return perSplitMetricsJson;
  }

  public void setPerSplitMetricsJson(Map<String, Object> perSplitMetricsJson) {
    this.perSplitMetricsJson = perSplitMetricsJson;
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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
