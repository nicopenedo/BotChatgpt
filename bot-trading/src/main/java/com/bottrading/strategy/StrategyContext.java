package com.bottrading.strategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class StrategyContext {
  private final String symbol;
  private final BigDecimal lastPrice;
  private final BigDecimal volume24h;
  private final Instant asOf;

  private StrategyContext(Builder builder) {
    this.symbol = builder.symbol;
    this.lastPrice = builder.lastPrice;
    this.volume24h = builder.volume24h;
    this.asOf = builder.asOf == null ? Instant.now() : builder.asOf;
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

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String symbol;
    private BigDecimal lastPrice;
    private BigDecimal volume24h;
    private Instant asOf;

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

    public StrategyContext build() {
      Objects.requireNonNull(symbol, "symbol");
      return new StrategyContext(this);
    }
  }
}
