package com.bottrading.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "positions")
public class PositionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String symbol;
  private BigDecimal entryPrice;
  private BigDecimal quantity;
  private BigDecimal stopLoss;
  private BigDecimal takeProfit;
  private Instant openedAt;
  private Instant closedAt;

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

  public BigDecimal getEntryPrice() {
    return entryPrice;
  }

  public void setEntryPrice(BigDecimal entryPrice) {
    this.entryPrice = entryPrice;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public void setQuantity(BigDecimal quantity) {
    this.quantity = quantity;
  }

  public BigDecimal getStopLoss() {
    return stopLoss;
  }

  public void setStopLoss(BigDecimal stopLoss) {
    this.stopLoss = stopLoss;
  }

  public BigDecimal getTakeProfit() {
    return takeProfit;
  }

  public void setTakeProfit(BigDecimal takeProfit) {
    this.takeProfit = takeProfit;
  }

  public Instant getOpenedAt() {
    return openedAt;
  }

  public void setOpenedAt(Instant openedAt) {
    this.openedAt = openedAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public void setClosedAt(Instant closedAt) {
    this.closedAt = closedAt;
  }
}
