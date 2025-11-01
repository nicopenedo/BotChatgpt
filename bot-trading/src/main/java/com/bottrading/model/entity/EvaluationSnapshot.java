package com.bottrading.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "evaluation_snapshots")
public class EvaluationSnapshot {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "preset_id")
  private PresetVersion preset;

  private String window;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "oos_metrics_json", columnDefinition = "jsonb")
  private Map<String, Object> oosMetricsJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "shadow_metrics_json", columnDefinition = "jsonb")
  private Map<String, Object> shadowMetricsJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "live_metrics_json", columnDefinition = "jsonb")
  private Map<String, Object> liveMetricsJson;

  @Column(name = "created_at")
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public PresetVersion getPreset() {
    return preset;
  }

  public void setPreset(PresetVersion preset) {
    this.preset = preset;
  }

  public String getWindow() {
    return window;
  }

  public void setWindow(String window) {
    this.window = window;
  }

  public Map<String, Object> getOosMetricsJson() {
    return oosMetricsJson;
  }

  public void setOosMetricsJson(Map<String, Object> oosMetricsJson) {
    this.oosMetricsJson = oosMetricsJson;
  }

  public Map<String, Object> getShadowMetricsJson() {
    return shadowMetricsJson;
  }

  public void setShadowMetricsJson(Map<String, Object> shadowMetricsJson) {
    this.shadowMetricsJson = shadowMetricsJson;
  }

  public Map<String, Object> getLiveMetricsJson() {
    return liveMetricsJson;
  }

  public void setLiveMetricsJson(Map<String, Object> liveMetricsJson) {
    this.liveMetricsJson = liveMetricsJson;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
