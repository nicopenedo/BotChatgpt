package com.bottrading.model.entity;

import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PositionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "positions")
public class PositionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String symbol;

  @Enumerated(EnumType.STRING)
  private OrderSide side;

  @Column(name = "entry_price")
  private BigDecimal entryPrice;

  @Column(name = "qty_init")
  private BigDecimal qtyInit;

  @Column(name = "qty_remaining")
  private BigDecimal qtyRemaining;

  @Column(name = "stop_loss")
  private BigDecimal stopLoss;

  @Column(name = "take_profit")
  private BigDecimal takeProfit;

  @Lob
  @Column(name = "trailing_conf")
  private String trailingConf;

  @Enumerated(EnumType.STRING)
  private PositionStatus status = PositionStatus.OPENING;

  @Column(name = "opened_at")
  private Instant openedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "last_update_at")
  private Instant lastUpdateAt;

  @Column(name = "correlation_id")
  private String correlationId;

  @Column(name = "regime_trend")
  private String regimeTrend;

  @Column(name = "regime_volatility")
  private String regimeVolatility;

  @Column(name = "preset_key")
  private String presetKey;

  @Column(name = "preset_id")
  private UUID presetId;

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

  public OrderSide getSide() {
    return side;
  }

  public void setSide(OrderSide side) {
    this.side = side;
  }

  public BigDecimal getEntryPrice() {
    return entryPrice;
  }

  public void setEntryPrice(BigDecimal entryPrice) {
    this.entryPrice = entryPrice;
  }

  public BigDecimal getQtyInit() {
    return qtyInit;
  }

  public void setQtyInit(BigDecimal qtyInit) {
    this.qtyInit = qtyInit;
  }

  public BigDecimal getQtyRemaining() {
    return qtyRemaining;
  }

  public void setQtyRemaining(BigDecimal qtyRemaining) {
    this.qtyRemaining = qtyRemaining;
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

  public String getTrailingConf() {
    return trailingConf;
  }

  public void setTrailingConf(String trailingConf) {
    this.trailingConf = trailingConf;
  }

  public PositionStatus getStatus() {
    return status;
  }

  public void setStatus(PositionStatus status) {
    this.status = status;
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

  public Instant getLastUpdateAt() {
    return lastUpdateAt;
  }

  public void setLastUpdateAt(Instant lastUpdateAt) {
    this.lastUpdateAt = lastUpdateAt;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public void setCorrelationId(String correlationId) {
    this.correlationId = correlationId;
  }

  public String getRegimeTrend() {
    return regimeTrend;
  }

  public void setRegimeTrend(String regimeTrend) {
    this.regimeTrend = regimeTrend;
  }

  public String getRegimeVolatility() {
    return regimeVolatility;
  }

  public void setRegimeVolatility(String regimeVolatility) {
    this.regimeVolatility = regimeVolatility;
  }

  public String getPresetKey() {
    return presetKey;
  }

  public void setPresetKey(String presetKey) {
    this.presetKey = presetKey;
  }

  public UUID getPresetId() {
    return presetId;
  }

  public void setPresetId(UUID presetId) {
    this.presetId = presetId;
  }
}
