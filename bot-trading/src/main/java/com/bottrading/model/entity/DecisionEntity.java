package com.bottrading.model.entity;

import com.bottrading.strategy.SignalSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "decisions")
public class DecisionEntity {

  @Id
  @Column(name = "decision_key", length = 120)
  private String decisionKey;

  private String symbol;
  private String interval;

  @Column(name = "close_time")
  private Instant closeTime;

  @Enumerated(EnumType.STRING)
  private SignalSide side;

  private double confidence;
  private String reason;

  @Column(name = "decided_at")
  private Instant decidedAt;

  private boolean executed;

  @Column(name = "order_id")
  private String orderId;

  public String getDecisionKey() {
    return decisionKey;
  }

  public void setDecisionKey(String decisionKey) {
    this.decisionKey = decisionKey;
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

  public Instant getCloseTime() {
    return closeTime;
  }

  public void setCloseTime(Instant closeTime) {
    this.closeTime = closeTime;
  }

  public SignalSide getSide() {
    return side;
  }

  public void setSide(SignalSide side) {
    this.side = side;
  }

  public double getConfidence() {
    return confidence;
  }

  public void setConfidence(double confidence) {
    this.confidence = confidence;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public void setDecidedAt(Instant decidedAt) {
    this.decidedAt = decidedAt;
  }

  public boolean isExecuted() {
    return executed;
  }

  public void setExecuted(boolean executed) {
    this.executed = executed;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }
}
