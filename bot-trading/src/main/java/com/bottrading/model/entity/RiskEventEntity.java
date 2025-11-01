package com.bottrading.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "risk_event")
public class RiskEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 64)
  private String type;

  @Column(columnDefinition = "text")
  private String detail;

  @Column(name = "ts", nullable = false)
  private Instant timestamp = Instant.now();

  public RiskEventEntity() {}

  public RiskEventEntity(String type, String detail, Instant timestamp) {
    this.type = type;
    this.detail = detail;
    this.timestamp = timestamp;
  }

  public Long getId() {
    return id;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }
}
