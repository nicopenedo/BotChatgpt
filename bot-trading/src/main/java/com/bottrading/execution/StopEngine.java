package com.bottrading.execution;

import com.bottrading.config.StopProperties;
import com.bottrading.config.StopProperties.StopSymbolProperties;
import com.bottrading.model.enums.OrderSide;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class StopEngine {

  private final StopProperties stopProperties;

  public StopEngine(StopProperties stopProperties) {
    this.stopProperties = stopProperties;
  }

  public StopPlan plan(String symbol, OrderSide side, BigDecimal entryPrice, BigDecimal atr) {
    Objects.requireNonNull(symbol, "symbol");
    Objects.requireNonNull(side, "side");
    Objects.requireNonNull(entryPrice, "entryPrice");
    StopSymbolProperties conf = stopProperties.getForSymbol(symbol);
    StopProperties.Mode mode = conf.getModeOrDefault(stopProperties);
    BigDecimal stopLoss;
    BigDecimal takeProfit;
    if (mode == StopProperties.Mode.ATR && atr != null) {
      BigDecimal slOffset = atr.multiply(conf.getSlAtrMultOrDefault(stopProperties));
      BigDecimal tpOffset = atr.multiply(conf.getTpAtrMultOrDefault(stopProperties));
      if (side == OrderSide.BUY) {
        stopLoss = entryPrice.subtract(slOffset);
        takeProfit = entryPrice.add(tpOffset);
      } else {
        stopLoss = entryPrice.add(slOffset);
        takeProfit = entryPrice.subtract(tpOffset);
      }
    } else {
      BigDecimal slPct =
          conf.getSlPctOrDefault(stopProperties).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
      BigDecimal tpPct =
          conf.getTpPctOrDefault(stopProperties).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
      if (side == OrderSide.BUY) {
        stopLoss = entryPrice.multiply(BigDecimal.ONE.subtract(slPct));
        takeProfit = entryPrice.multiply(BigDecimal.ONE.add(tpPct));
      } else {
        stopLoss = entryPrice.multiply(BigDecimal.ONE.add(slPct));
        takeProfit = entryPrice.multiply(BigDecimal.ONE.subtract(tpPct));
      }
    }
    BigDecimal trailingOffset = computeTrailingOffset(side, entryPrice, atr, conf);
    BigDecimal breakevenTrigger =
        conf.isBreakevenEnabledOrDefault(stopProperties)
            ? entryPrice
                .multiply(
                    BigDecimal.ONE.add(
                        conf.getBreakevenTriggerPctOrDefault(stopProperties)
                            .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                            .multiply(side == OrderSide.BUY ? BigDecimal.ONE : BigDecimal.valueOf(-1))))
                .setScale(8, RoundingMode.HALF_UP)
            : null;
    return new StopPlan(stopLoss, takeProfit, trailingOffset, breakevenTrigger, conf);
  }

  private BigDecimal computeTrailingOffset(
      OrderSide side, BigDecimal entryPrice, BigDecimal atr, StopSymbolProperties conf) {
    if (!conf.isTrailingEnabledOrDefault(stopProperties)) {
      return null;
    }
    if (conf.getModeOrDefault(stopProperties) == StopProperties.Mode.ATR && atr != null) {
      return atr.multiply(conf.getTrailingAtrMultOrDefault(stopProperties));
    }
    BigDecimal pct =
        conf.getTrailingPctOrDefault(stopProperties).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
    BigDecimal offset = entryPrice.multiply(pct);
    return offset.max(BigDecimal.ZERO);
  }

  public void updateConfiguration(String symbol, StopSymbolProperties config) {
    stopProperties.getSymbols().put(symbol, config);
  }

  public StopStatus status(String symbol) {
    StopSymbolProperties conf = stopProperties.getForSymbol(symbol);
    return new StopStatus(conf, List.of());
  }

  public record StopPlan(
      BigDecimal stopLoss,
      BigDecimal takeProfit,
      BigDecimal trailingOffset,
      BigDecimal breakevenTrigger,
      StopSymbolProperties properties) {}

  public record StopStatus(StopSymbolProperties properties, List<Object> activePositions) {}
}
