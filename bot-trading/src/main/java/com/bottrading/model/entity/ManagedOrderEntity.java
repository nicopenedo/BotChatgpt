package com.bottrading.model.entity;

import com.bottrading.model.enums.ManagedOrderType;
import com.bottrading.model.enums.OrderSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "managed_orders")
public class ManagedOrderEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "position_id")
  private PositionEntity position;

  @Column(name = "client_order_id")
  private String clientOrderId;

  @Enumerated(EnumType.STRING)
  private ManagedOrderType type;

  @Enumerated(EnumType.STRING)
  private OrderSide side;

  private BigDecimal price;
  private BigDecimal quantity;

  @Column(name = "filled_qty")
  private BigDecimal filledQuantity;

  private String status;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public PositionEntity getPosition() {
    return position;
  }

  public void setPosition(PositionEntity position) {
    this.position = position;
  }

  public String getClientOrderId() {
    return clientOrderId;
  }

  public void setClientOrderId(String clientOrderId) {
    this.clientOrderId = clientOrderId;
  }

  public ManagedOrderType getType() {
    return type;
  }

  public void setType(ManagedOrderType type) {
    this.type = type;
  }

  public OrderSide getSide() {
    return side;
  }

  public void setSide(OrderSide side) {
    this.side = side;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public void setQuantity(BigDecimal quantity) {
    this.quantity = quantity;
  }

  public BigDecimal getFilledQuantity() {
    return filledQuantity;
  }

  public void setFilledQuantity(BigDecimal filledQuantity) {
    this.filledQuantity = filledQuantity;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
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
