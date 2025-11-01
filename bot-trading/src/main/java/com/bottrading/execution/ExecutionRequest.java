package com.bottrading.execution;

import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.dto.ExchangeInfo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record ExecutionRequest(
    String symbol,
    OrderSide side,
    BigDecimal quantity,
    BigDecimal referencePrice,
    BigDecimal notional,
    ExchangeInfo exchangeInfo,
    Urgency urgency,
    double maxSlippageBps,
    Instant deadline,
    boolean dryRun,
    BigDecimal volume24h,
    BigDecimal atr,
    double spreadBps,
    double expectedVolatilityBps,
    double latencyMs,
    String baseClientOrderId) {

  public ExecutionRequest {
    Objects.requireNonNull(symbol, "symbol");
    Objects.requireNonNull(side, "side");
    Objects.requireNonNull(quantity, "quantity");
    Objects.requireNonNull(referencePrice, "referencePrice");
    Objects.requireNonNull(exchangeInfo, "exchangeInfo");
    Objects.requireNonNull(urgency, "urgency");
    Objects.requireNonNull(deadline, "deadline");
    Objects.requireNonNull(baseClientOrderId, "baseClientOrderId");
  }

  public enum Urgency {
    LOW,
    MEDIUM,
    HIGH
  }
}
