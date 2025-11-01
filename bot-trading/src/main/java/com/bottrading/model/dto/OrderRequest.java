package com.bottrading.model.dto;

import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class OrderRequest {

  @NotBlank private String symbol;

  @NotNull private OrderSide side;

  @NotNull private OrderType type;

  @DecimalMin("0.0") private BigDecimal price;

  @DecimalMin("0.0") private BigDecimal quantity;

  @DecimalMin("0.0") private BigDecimal quoteAmount;

  private boolean dryRun;
  private String clientOrderId;

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

  public BigDecimal getQuoteAmount() {
    return quoteAmount;
  }

  public void setQuoteAmount(BigDecimal quoteAmount) {
    this.quoteAmount = quoteAmount;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public void setDryRun(boolean dryRun) {
    this.dryRun = dryRun;
  }

  public String getClientOrderId() {
    return clientOrderId;
  }

  public void setClientOrderId(String clientOrderId) {
    this.clientOrderId = clientOrderId;
  }
}
