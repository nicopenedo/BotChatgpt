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

@Entity
@Table(name = "trade_fill")
public class TradeFillEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id")
  private String orderId;

  @Column(name = "client_order_id")
  private String clientOrderId;

  private String symbol;

  @Enumerated(EnumType.STRING)
  private OrderType orderType;

  @Enumerated(EnumType.STRING)
  private OrderSide orderSide;

  @Column(name = "ref_price")
  private BigDecimal refPrice;

  @Column(name = "fill_price")
  private BigDecimal fillPrice;

  @Column(name = "slippage_bps")
  private Double slippageBps;

  @Column(name = "queue_time_ms")
  private Long queueTimeMs;

  @Column(name = "executed_at")
  private Instant executedAt;

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

  public OrderType getOrderType() {
    return orderType;
  }

  public void setOrderType(OrderType orderType) {
    this.orderType = orderType;
  }

  public OrderSide getOrderSide() {
    return orderSide;
  }

  public void setOrderSide(OrderSide orderSide) {
    this.orderSide = orderSide;
  }

  public BigDecimal getRefPrice() {
    return refPrice;
  }

  public void setRefPrice(BigDecimal refPrice) {
    this.refPrice = refPrice;
  }

  public BigDecimal getFillPrice() {
    return fillPrice;
  }

  public void setFillPrice(BigDecimal fillPrice) {
    this.fillPrice = fillPrice;
  }

  public Double getSlippageBps() {
    return slippageBps;
  }

  public void setSlippageBps(Double slippageBps) {
    this.slippageBps = slippageBps;
  }

  public Long getQueueTimeMs() {
    return queueTimeMs;
  }

  public void setQueueTimeMs(Long queueTimeMs) {
    this.queueTimeMs = queueTimeMs;
  }

  public Instant getExecutedAt() {
    return executedAt;
  }

  public void setExecutedAt(Instant executedAt) {
    this.executedAt = executedAt;
  }
}
