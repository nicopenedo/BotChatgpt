package com.bottrading.bandit;

import com.bottrading.model.enums.OrderSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "bandit_pull")
public class BanditPullEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "arm_id")
  private BanditArmEntity arm;

  @Column(name = "ts")
  private Instant timestamp;

  @Column(name = "decision_id")
  private String decisionId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "context_json", columnDefinition = "jsonb")
  private Map<String, Object> context;

  private Double reward;

  @Column(name = "pnl_r")
  private Double pnlR;

  @Column(name = "slippage_bps")
  private Double slippageBps;

  @Column(name = "fees_bps")
  private Double feesBps;

  @Column(name = "symbol")
  private String symbol;

  @Column(name = "regime")
  private String regime;

  @Enumerated(EnumType.STRING)
  private OrderSide side;

  @Enumerated(EnumType.STRING)
  @Column(name = "role")
  private BanditArmRole role;

  @Column(name = "created_at")
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public BanditArmEntity getArm() {
    return arm;
  }

  public void setArm(BanditArmEntity arm) {
    this.arm = arm;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public void setDecisionId(String decisionId) {
    this.decisionId = decisionId;
  }

  public Map<String, Object> getContext() {
    return context;
  }

  public void setContext(Map<String, Object> context) {
    this.context = context;
  }

  public Double getReward() {
    return reward;
  }

  public void setReward(Double reward) {
    this.reward = reward;
  }

  public Double getPnlR() {
    return pnlR;
  }

  public void setPnlR(Double pnlR) {
    this.pnlR = pnlR;
  }

  public Double getSlippageBps() {
    return slippageBps;
  }

  public void setSlippageBps(Double slippageBps) {
    this.slippageBps = slippageBps;
  }

  public Double getFeesBps() {
    return feesBps;
  }

  public void setFeesBps(Double feesBps) {
    this.feesBps = feesBps;
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

  public OrderSide getSide() {
    return side;
  }

  public void setSide(OrderSide side) {
    this.side = side;
  }

  public BanditArmRole getRole() {
    return role;
  }

  public void setRole(BanditArmRole role) {
    this.role = role;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
