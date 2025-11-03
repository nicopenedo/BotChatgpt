package com.bottrading.model.entity;

import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id")
  private String orderId;

  @Column(name = "client_order_id")
  private String clientOrderId;

  private String symbol;

  @Enumerated(EnumType.STRING)
  private OrderSide side;

  @Enumerated(EnumType.STRING)
  private OrderType type;

  private BigDecimal price;
  private BigDecimal quantity;
  private BigDecimal executedQty;

  @Column(name = "quote_qty")
  private BigDecimal quoteQty;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  private String status;
  private Instant transactTime;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public String getClientOrderId() {
    return clientOrderId;
  }

  public void setClientOrderId(String clientOrderId) {
    this.clientOrderId = clientOrderId;
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public OrderSide getSide() {
    return side;
  }

  public void setSide(OrderSide side) {
    this.side = side;
  }

  public OrderType getType() {
    return type;
  }

  public void setType(OrderType type) {
    this.type = type;
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

  public BigDecimal getExecutedQty() {
    return executedQty;
  }

  public void setExecutedQty(BigDecimal executedQty) {
    this.executedQty = executedQty;
  }

  public BigDecimal getQuoteQty() {
    return quoteQty;
  }

  public void setQuoteQty(BigDecimal quoteQty) {
    this.quoteQty = quoteQty;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getTransactTime() {
    return transactTime;
  }

  public void setTransactTime(Instant transactTime) {
    this.transactTime = transactTime;
  }
}
