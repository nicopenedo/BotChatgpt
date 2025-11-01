package com.bottrading.strategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import com.bottrading.research.regime.Regime;

public class StrategyContext {
  private final String symbol;
  private final BigDecimal lastPrice;
  private final BigDecimal volume24h;
  private final Instant asOf;
  private final Regime regime;
  private final String preset;
  private final Double normalizedAtr;
  private final Double adx;
  private final Double rangeScore;

  private StrategyContext(Builder builder) {
    this.symbol = builder.symbol;
    this.lastPrice = builder.lastPrice;
    this.volume24h = builder.volume24h;
    this.asOf = builder.asOf == null ? Instant.now() : builder.asOf;
    this.regime = builder.regime;
    this.preset = builder.preset;
    this.normalizedAtr = builder.normalizedAtr;
    this.adx = builder.adx;
    this.rangeScore = builder.rangeScore;
  }

  public String symbol() {
    return symbol;
  }

  public BigDecimal lastPrice() {
    return lastPrice;
  }

  public BigDecimal volume24h() {
    return volume24h;
  }

  public Instant asOf() {
    return asOf;
  }

  public Regime regime() {
    return regime;
  }

  public String preset() {
    return preset;
  }

  public Double normalizedAtr() {
    return normalizedAtr;
  }

  public Double adx() {
    return adx;
  }

  public Double rangeScore() {
    return rangeScore;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String symbol;
    private BigDecimal lastPrice;
    private BigDecimal volume24h;
    private Instant asOf;
    private Regime regime;
    private String preset;
    private Double normalizedAtr;
    private Double adx;
    private Double rangeScore;

    public Builder symbol(String symbol) {
      this.symbol = symbol;
      return this;
    }

    public Builder lastPrice(BigDecimal lastPrice) {
      this.lastPrice = lastPrice;
      return this;
    }

    public Builder volume24h(BigDecimal volume24h) {
      this.volume24h = volume24h;
      return this;
    }

    public Builder asOf(Instant asOf) {
      this.asOf = asOf;
      return this;
    }

    public Builder regime(Regime regime) {
      this.regime = regime;
      return this;
    }

    public Builder preset(String preset) {
      this.preset = preset;
      return this;
    }

    public Builder normalizedAtr(Double normalizedAtr) {
      this.normalizedAtr = normalizedAtr;
      return this;
    }

    public Builder adx(Double adx) {
      this.adx = adx;
      return this;
    }

    public Builder rangeScore(Double rangeScore) {
      this.rangeScore = rangeScore;
      return this;
    }

    public StrategyContext build() {
      Objects.requireNonNull(symbol, "symbol");
      return new StrategyContext(this);
    }
  }
}
