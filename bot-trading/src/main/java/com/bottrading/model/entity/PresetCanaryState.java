package com.bottrading.model.entity;

import com.bottrading.model.enums.CanaryStatus;
import com.bottrading.research.regime.RegimeTrend;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "preset_canary_state")
public class PresetCanaryState {

  @Id
  @Column(name = "preset_id")
  private UUID presetId;

  @Column(name = "symbol", length = 40, nullable = false)
  private String symbol;

  @Enumerated(EnumType.STRING)
  private RegimeTrend regime;

  @Enumerated(EnumType.STRING)
  private CanaryStatus status = CanaryStatus.ELIGIBLE;

  @Column(name = "stage_index")
  private int stageIndex;

  @Column(name = "current_multiplier")
  private double currentMultiplier;

  @Column(name = "oos_pf")
  private Double oosPf;

  @Column(name = "oos_trades")
  private Integer oosTrades;

  @Column(name = "shadow_pf")
  private Double shadowPf;

  @Column(name = "shadow_trades_baseline")
  private Integer shadowTradesBaseline;

  @Column(name = "last_shadow_evaluation")
  private Instant lastShadowEvaluation;

  @Column(name = "run_id", length = 120)
  private String runId;

  @Column(name = "notes", columnDefinition = "text")
  private String notes;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getPresetId() {
    return presetId;
  }

  public void setPresetId(UUID presetId) {
    this.presetId = presetId;
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public RegimeTrend getRegime() {
    return regime;
  }

  public void setRegime(RegimeTrend regime) {
    this.regime = regime;
  }

  public CanaryStatus getStatus() {
    return status;
  }

  public void setStatus(CanaryStatus status) {
    this.status = status;
  }

  public int getStageIndex() {
    return stageIndex;
  }

  public void setStageIndex(int stageIndex) {
    this.stageIndex = stageIndex;
  }

  public double getCurrentMultiplier() {
    return currentMultiplier;
  }

  public void setCurrentMultiplier(double currentMultiplier) {
    this.currentMultiplier = currentMultiplier;
  }

  public Double getOosPf() {
    return oosPf;
  }

  public void setOosPf(Double oosPf) {
    this.oosPf = oosPf;
  }

  public Integer getOosTrades() {
    return oosTrades;
  }

  public void setOosTrades(Integer oosTrades) {
    this.oosTrades = oosTrades;
  }

  public Double getShadowPf() {
    return shadowPf;
  }

  public void setShadowPf(Double shadowPf) {
    this.shadowPf = shadowPf;
  }

  public Integer getShadowTradesBaseline() {
    return shadowTradesBaseline;
  }

  public void setShadowTradesBaseline(Integer shadowTradesBaseline) {
    this.shadowTradesBaseline = shadowTradesBaseline;
  }

  public Instant getLastShadowEvaluation() {
    return lastShadowEvaluation;
  }

  public void setLastShadowEvaluation(Instant lastShadowEvaluation) {
    this.lastShadowEvaluation = lastShadowEvaluation;
  }

  public String getRunId() {
    return runId;
  }

  public void setRunId(String runId) {
    this.runId = runId;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
