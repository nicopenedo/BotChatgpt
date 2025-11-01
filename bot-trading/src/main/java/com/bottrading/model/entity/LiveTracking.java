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
@Table(name = "live_tracking")
public class LiveTracking {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "preset_id")
  private PresetVersion preset;

  @Column(name = "ts_from")
  private Instant tsFrom;

  @Column(name = "ts_to")
  private Instant tsTo;

  @Column(name = "capital_risked")
  private Double capitalRisked;

  private Double pnl;

  private Double pf;

  @Column(name = "maxdd")
  private Double maxDrawdown;

  private Integer trades;

  @Column(name = "slippage_bps")
  private Double slippageBps;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "drift_flags", columnDefinition = "jsonb")
  private Map<String, Object> driftFlags;

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

  public Double getCapitalRisked() {
    return capitalRisked;
  }

  public void setCapitalRisked(Double capitalRisked) {
    this.capitalRisked = capitalRisked;
  }

  public Double getPnl() {
    return pnl;
  }

  public void setPnl(Double pnl) {
    this.pnl = pnl;
  }

  public Double getPf() {
    return pf;
  }

  public void setPf(Double pf) {
    this.pf = pf;
  }

  public Double getMaxDrawdown() {
    return maxDrawdown;
  }

  public void setMaxDrawdown(Double maxDrawdown) {
    this.maxDrawdown = maxDrawdown;
  }

  public Integer getTrades() {
    return trades;
  }

  public void setTrades(Integer trades) {
    this.trades = trades;
  }

  public Double getSlippageBps() {
    return slippageBps;
  }

  public void setSlippageBps(Double slippageBps) {
    this.slippageBps = slippageBps;
  }

  public Map<String, Object> getDriftFlags() {
    return driftFlags;
  }

  public void setDriftFlags(Map<String, Object> driftFlags) {
    this.driftFlags = driftFlags;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
