package com.bottrading.model.entity;

import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PresetStatus;
import com.bottrading.research.regime.RegimeTrend;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "preset_versions")
public class PresetVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  private RegimeTrend regime;

  @Enumerated(EnumType.STRING)
  private OrderSide side;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "params_json", columnDefinition = "jsonb")
  private Map<String, Object> paramsJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "signals_json", columnDefinition = "jsonb")
  private Map<String, Object> signalsJson;

  @Column(name = "source_run_id")
  private String sourceRunId;

  @Enumerated(EnumType.STRING)
  private PresetStatus status;

  @Column(name = "code_sha")
  private String codeSha;

  @Column(name = "data_hash")
  private String dataHash;

  @Column(name = "labels_hash")
  private String labelsHash;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "activated_at")
  private Instant activatedAt;

  @Column(name = "retired_at")
  private Instant retiredAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    if (status == null) {
      status = PresetStatus.CANDIDATE;
    }
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public RegimeTrend getRegime() {
    return regime;
  }

  public void setRegime(RegimeTrend regime) {
    this.regime = regime;
  }

  public OrderSide getSide() {
    return side;
  }

  public void setSide(OrderSide side) {
    this.side = side;
  }

  public Map<String, Object> getParamsJson() {
    return paramsJson;
  }

  public void setParamsJson(Map<String, Object> paramsJson) {
    this.paramsJson = paramsJson;
  }

  public Map<String, Object> getSignalsJson() {
    return signalsJson;
  }

  public void setSignalsJson(Map<String, Object> signalsJson) {
    this.signalsJson = signalsJson;
  }

  public String getSourceRunId() {
    return sourceRunId;
  }

  public void setSourceRunId(String sourceRunId) {
    this.sourceRunId = sourceRunId;
  }

  public PresetStatus getStatus() {
    return status;
  }

  public void setStatus(PresetStatus status) {
    this.status = status;
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

  public Instant getActivatedAt() {
    return activatedAt;
  }

  public void setActivatedAt(Instant activatedAt) {
    this.activatedAt = activatedAt;
  }

  public Instant getRetiredAt() {
    return retiredAt;
  }

  public void setRetiredAt(Instant retiredAt) {
    this.retiredAt = retiredAt;
  }
}
