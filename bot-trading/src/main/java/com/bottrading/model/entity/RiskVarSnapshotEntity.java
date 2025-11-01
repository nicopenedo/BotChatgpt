package com.bottrading.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_var_snapshot")
public class RiskVarSnapshotEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String symbol;

  private String regime;

  @Column(name = "regime_trend")
  private String regimeTrend;

  @Column(name = "regime_volatility")
  private String regimeVolatility;

  @Column(name = "preset_id")
  private UUID presetId;

  @Column(name = "preset_key")
  private String presetKey;

  @ManyToOne
  @JoinColumn(name = "position_id")
  private PositionEntity position;

  @Column(name = "ts")
  private Instant timestamp;

  @Column(name = "var_q")
  private BigDecimal var;

  @Column(name = "cvar_q")
  private BigDecimal cvar;

  @Column(name = "qty_ratio")
  private BigDecimal qtyRatio;

  @Column(name = "reasons_json")
  private String reasonsJson;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public String getRegime() {
    return regime;
  }

  public void setRegime(String regime) {
    this.regime = regime;
  }

  public String getRegimeTrend() {
    return regimeTrend;
  }

  public void setRegimeTrend(String regimeTrend) {
    this.regimeTrend = regimeTrend;
  }

  public String getRegimeVolatility() {
    return regimeVolatility;
  }

  public void setRegimeVolatility(String regimeVolatility) {
    this.regimeVolatility = regimeVolatility;
  }

  public UUID getPresetId() {
    return presetId;
  }

  public void setPresetId(UUID presetId) {
    this.presetId = presetId;
  }

  public String getPresetKey() {
    return presetKey;
  }

  public void setPresetKey(String presetKey) {
    this.presetKey = presetKey;
  }

  public PositionEntity getPosition() {
    return position;
  }

  public void setPosition(PositionEntity position) {
    this.position = position;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }

  public BigDecimal getVar() {
    return var;
  }

  public void setVar(BigDecimal var) {
    this.var = var;
  }

  public BigDecimal getCvar() {
    return cvar;
  }

  public void setCvar(BigDecimal cvar) {
    this.cvar = cvar;
  }

  public BigDecimal getQtyRatio() {
    return qtyRatio;
  }

  public void setQtyRatio(BigDecimal qtyRatio) {
    this.qtyRatio = qtyRatio;
  }

  public String getReasonsJson() {
    return reasonsJson;
  }

  public void setReasonsJson(String reasonsJson) {
    this.reasonsJson = reasonsJson;
  }
}
