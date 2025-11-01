package com.bottrading.bandit;

import com.bottrading.model.enums.OrderSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "bandit_arm")
public class BanditArmEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private String symbol;
  private String regime;

  @Enumerated(EnumType.STRING)
  private OrderSide side;

  @Column(name = "preset_id")
  private UUID presetId;

  @Enumerated(EnumType.STRING)
  private BanditArmStatus status = BanditArmStatus.ELIGIBLE;

  @Enumerated(EnumType.STRING)
  private BanditArmRole role = BanditArmRole.CANDIDATE;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "stats_json", columnDefinition = "jsonb")
  private BanditArmStats stats = new BanditArmStats();

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
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

  public OrderSide getSide() {
    return side;
  }

  public void setSide(OrderSide side) {
    this.side = side;
  }

  public UUID getPresetId() {
    return presetId;
  }

  public void setPresetId(UUID presetId) {
    this.presetId = presetId;
  }

  public BanditArmStatus getStatus() {
    return status;
  }

  public void setStatus(BanditArmStatus status) {
    this.status = status;
  }

  public BanditArmRole getRole() {
    return role;
  }

  public void setRole(BanditArmRole role) {
    this.role = role;
  }

  public BanditArmStats getStats() {
    return stats;
  }

  public void setStats(BanditArmStats stats) {
    this.stats = stats;
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
