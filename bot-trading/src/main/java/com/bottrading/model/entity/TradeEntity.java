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
@Table(name = "trades")
public class TradeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String symbol;

  @Column(name = "order_id")
  private String orderId;

  private BigDecimal price;
  private BigDecimal quantity;

  @Column(name = "quote_qty")
  private BigDecimal quoteQty;

  private Instant tradeTime;
  private BigDecimal fee;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
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

  public BigDecimal getQuoteQty() {
    return quoteQty;
  }

  public void setQuoteQty(BigDecimal quoteQty) {
    this.quoteQty = quoteQty;
  }

  public Instant getTradeTime() {
    return tradeTime;
  }

  public void setTradeTime(Instant tradeTime) {
    this.tradeTime = tradeTime;
  }

  public BigDecimal getFee() {
    return fee;
  }

  public void setFee(BigDecimal fee) {
    this.fee = fee;
  }
}
