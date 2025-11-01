package com.bottrading.execution;

import com.bottrading.config.SizingProperties;
import com.bottrading.config.TradingProps;
import com.bottrading.model.dto.ExchangeInfo;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class OrderSizingService {

  private final TradingProps tradingProps;
  private final SizingProperties sizingProperties;

  public OrderSizingService(TradingProps tradingProps, SizingProperties sizingProperties) {
    this.tradingProps = tradingProps;
    this.sizingProperties = sizingProperties;
  }

  public OrderSizingResult size(
      OrderSide side,
      BigDecimal entryPrice,
      BigDecimal stopPrice,
      BigDecimal atr,
      BigDecimal equity,
      ExchangeInfo exchangeInfo,
      double sizingMultiplier) {
    Objects.requireNonNull(side, "side");
    Objects.requireNonNull(entryPrice, "entryPrice");
    Objects.requireNonNull(stopPrice, "stopPrice");
    Objects.requireNonNull(equity, "equity");
    Objects.requireNonNull(exchangeInfo, "exchangeInfo");

    if (entryPrice.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Entry price must be positive");
    }
    if (stopPrice.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Stop price must be positive");
    }
    BigDecimal stopDistance =
        side == OrderSide.BUY ? entryPrice.subtract(stopPrice) : stopPrice.subtract(entryPrice);
    if (stopDistance.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Stop distance must be positive");
    }

    BigDecimal riskFraction =
        tradingProps.getRiskPerTradePct().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
    BigDecimal multiplier =
        BigDecimal.valueOf(Math.max(0.0, Math.min(1.0, sizingMultiplier))).setScale(8, RoundingMode.HALF_UP);
    BigDecimal riskAmount = equity.multiply(riskFraction).multiply(multiplier);
    if (riskAmount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalStateException("Risk amount must be positive");
    }

    BigDecimal rawQty = riskAmount.divide(stopDistance, 8, RoundingMode.DOWN);
    BigDecimal stepSize = exchangeInfo.stepSize();
    if (stepSize != null && stepSize.compareTo(BigDecimal.ZERO) > 0) {
      rawQty = rawQty.subtract(rawQty.remainder(stepSize));
    }
    if (rawQty.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalStateException("Quantity below step size");
    }

    BigDecimal notional = rawQty.multiply(entryPrice);
    BigDecimal minNotional = exchangeInfo.minNotional();
    BigDecimal bufferMultiplier =
        BigDecimal.ONE.add(
            sizingProperties
                .getMinNotionalBufferPct()
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
    if (minNotional != null && notional.compareTo(minNotional.multiply(bufferMultiplier)) < 0) {
      throw new IllegalStateException("Notional below min notional with buffer");
    }

    OrderType orderType = suggestOrderType(entryPrice, atr);
    return new OrderSizingResult(rawQty, notional, orderType, sizingProperties.isIcebergEnabled());
  }

  private OrderType suggestOrderType(BigDecimal entryPrice, BigDecimal atr) {
    BigDecimal expectedBps;
    if (sizingProperties.getSlippageModel() == SizingProperties.SlippageModel.ATR && atr != null) {
      expectedBps =
          atr
              .divide(entryPrice, 8, RoundingMode.HALF_UP)
              .multiply(BigDecimal.valueOf(10000));
    } else {
      expectedBps = sizingProperties.getSlippageFixedBps();
    }
    if (expectedBps == null) {
      expectedBps = BigDecimal.valueOf(5);
    }
    return expectedBps.compareTo(BigDecimal.TEN) > 0 ? OrderType.LIMIT : OrderType.MARKET;
  }

  public record OrderSizingResult(
      BigDecimal quantity, BigDecimal notional, OrderType orderType, boolean iceberg) {}
}
