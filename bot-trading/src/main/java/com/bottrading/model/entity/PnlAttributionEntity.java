package com.bottrading.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pnl_attr")
public class PnlAttributionEntity {

  @Id
  @Column(name = "trade_id")
  private Long tradeId;

  private String symbol;

  private String signal;

  private String regime;

  private String preset;

  @Column(name = "pnl_gross")
  private BigDecimal pnlGross;

  @Column(name = "signal_edge")
  private BigDecimal signalEdge;

  @Column(name = "fees_cost")
  private BigDecimal feesCost;

  @Column(name = "fees_bps")
  private BigDecimal feesBps;

  @Column(name = "slippage_cost")
  private BigDecimal slippageCost;

  @Column(name = "slippage_bps")
  private BigDecimal slippageBps;

  @Column(name = "timing_cost")
  private BigDecimal timingCost;

  @Column(name = "timing_bps")
  private BigDecimal timingBps;

  @Column(name = "pnl_net")
  private BigDecimal pnlNet;

  private BigDecimal notional;

  @Column(name = "ts")
  private Instant timestamp;

  public Long getTradeId() {
    return tradeId;
  }

  public void setTradeId(Long tradeId) {
    this.tradeId = tradeId;
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public String getSignal() {
    return signal;
  }

  public void setSignal(String signal) {
    this.signal = signal;
  }

  public String getRegime() {
    return regime;
  }

  public void setRegime(String regime) {
    this.regime = regime;
  }

  public String getPreset() {
    return preset;
  }

  public void setPreset(String preset) {
    this.preset = preset;
  }

  public BigDecimal getPnlGross() {
    return pnlGross;
  }

  public void setPnlGross(BigDecimal pnlGross) {
    this.pnlGross = pnlGross;
  }

  public BigDecimal getSignalEdge() {
    return signalEdge;
  }

  public void setSignalEdge(BigDecimal signalEdge) {
    this.signalEdge = signalEdge;
  }

  public BigDecimal getFeesCost() {
    return feesCost;
  }

  public void setFeesCost(BigDecimal feesCost) {
    this.feesCost = feesCost;
  }

  public BigDecimal getFeesBps() {
    return feesBps;
  }

  public void setFeesBps(BigDecimal feesBps) {
    this.feesBps = feesBps;
  }

  public BigDecimal getSlippageCost() {
    return slippageCost;
  }

  public void setSlippageCost(BigDecimal slippageCost) {
    this.slippageCost = slippageCost;
  }

  public BigDecimal getSlippageBps() {
    return slippageBps;
  }

  public void setSlippageBps(BigDecimal slippageBps) {
    this.slippageBps = slippageBps;
  }

  public BigDecimal getTimingCost() {
    return timingCost;
  }

  public void setTimingCost(BigDecimal timingCost) {
    this.timingCost = timingCost;
  }

  public BigDecimal getTimingBps() {
    return timingBps;
  }

  public void setTimingBps(BigDecimal timingBps) {
    this.timingBps = timingBps;
  }

  public BigDecimal getPnlNet() {
    return pnlNet;
  }

  public void setPnlNet(BigDecimal pnlNet) {
    this.pnlNet = pnlNet;
  }

  public BigDecimal getNotional() {
    return notional;
  }

  public void setNotional(BigDecimal notional) {
    this.notional = notional;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }
}
