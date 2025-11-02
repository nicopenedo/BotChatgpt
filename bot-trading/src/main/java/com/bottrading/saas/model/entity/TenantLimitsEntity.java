package com.bottrading.saas.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_limits")
public class TenantLimitsEntity {

  @Id
  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "max_bots", nullable = false)
  private int maxBots;

  @Column(name = "max_symbols", nullable = false)
  private int maxSymbols;

  @Column(name = "canary_share_max", nullable = false)
  private BigDecimal canaryShareMax;

  @Column(name = "max_trades_per_day", nullable = false)
  private int maxTradesPerDay;

  @Column(name = "max_daily_drawdown_pct", nullable = false)
  private BigDecimal maxDailyDrawdownPct;

  @Column(name = "max_concurrent_positions", nullable = false)
  private int maxConcurrentPositions;

  @Column(name = "canary_pct", nullable = false)
  private BigDecimal canaryPct;

  @Column(name = "data_retention_days", nullable = false)
  private int dataRetentionDays;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }

  public int getMaxBots() {
    return maxBots;
  }

  public void setMaxBots(int maxBots) {
    this.maxBots = maxBots;
  }

  public int getMaxSymbols() {
    return maxSymbols;
  }

  public void setMaxSymbols(int maxSymbols) {
    this.maxSymbols = maxSymbols;
  }

  public BigDecimal getCanaryShareMax() {
    return canaryShareMax;
  }

  public void setCanaryShareMax(BigDecimal canaryShareMax) {
    this.canaryShareMax = canaryShareMax;
  }

  public int getMaxTradesPerDay() {
    return maxTradesPerDay;
  }

  public void setMaxTradesPerDay(int maxTradesPerDay) {
    this.maxTradesPerDay = maxTradesPerDay;
  }

  public BigDecimal getMaxDailyDrawdownPct() {
    return maxDailyDrawdownPct;
  }

  public void setMaxDailyDrawdownPct(BigDecimal maxDailyDrawdownPct) {
    this.maxDailyDrawdownPct = maxDailyDrawdownPct;
  }

  public int getMaxConcurrentPositions() {
    return maxConcurrentPositions;
  }

  public void setMaxConcurrentPositions(int maxConcurrentPositions) {
    this.maxConcurrentPositions = maxConcurrentPositions;
  }

  public BigDecimal getCanaryPct() {
    return canaryPct;
  }

  public void setCanaryPct(BigDecimal canaryPct) {
    this.canaryPct = canaryPct;
  }

  public int getDataRetentionDays() {
    return dataRetentionDays;
  }

  public void setDataRetentionDays(int dataRetentionDays) {
    this.dataRetentionDays = dataRetentionDays;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
