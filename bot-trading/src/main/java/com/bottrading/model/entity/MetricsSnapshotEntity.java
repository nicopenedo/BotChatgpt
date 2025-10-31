package com.bottrading.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "metrics_snapshots")
public class MetricsSnapshotEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Instant capturedAt;

  @Column(name = "equity_value")
  private BigDecimal equityValue;

  @Column(name = "drawdown_pct")
  private BigDecimal drawdownPct;

  @Column(name = "daily_pnl_pct")
  private BigDecimal dailyPnlPct;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Instant getCapturedAt() {
    return capturedAt;
  }

  public void setCapturedAt(Instant capturedAt) {
    this.capturedAt = capturedAt;
  }

  public BigDecimal getEquityValue() {
    return equityValue;
  }

  public void setEquityValue(BigDecimal equityValue) {
    this.equityValue = equityValue;
  }

  public BigDecimal getDrawdownPct() {
    return drawdownPct;
  }

  public void setDrawdownPct(BigDecimal drawdownPct) {
    this.drawdownPct = drawdownPct;
  }

  public BigDecimal getDailyPnlPct() {
    return dailyPnlPct;
  }

  public void setDailyPnlPct(BigDecimal dailyPnlPct) {
    this.dailyPnlPct = dailyPnlPct;
  }
}
