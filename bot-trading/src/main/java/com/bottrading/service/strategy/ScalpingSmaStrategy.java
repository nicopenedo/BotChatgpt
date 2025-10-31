package com.bottrading.service.strategy;

import com.bottrading.config.TradingProperties;
import com.bottrading.model.dto.Kline;
import com.bottrading.model.dto.StrategySignal;
import com.bottrading.model.enums.OrderSide;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ScalpingSmaStrategy {

  private static final Logger log = LoggerFactory.getLogger(ScalpingSmaStrategy.class);
  private final TradingProperties tradingProperties;
  private final Counter signals;

  public ScalpingSmaStrategy(TradingProperties tradingProperties, MeterRegistry meterRegistry) {
    this.tradingProperties = tradingProperties;
    this.signals = meterRegistry.counter("strategy.signals");
  }

  public StrategySignal evaluate(List<Kline> klines, BigDecimal volume24h, BigDecimal lastPrice) {
    if (klines == null || klines.size() < 30) {
      log.debug("Not enough klines to evaluate strategy");
      return null;
    }
    if (volume24h.compareTo(tradingProperties.getMinVolume24h()) < 0) {
      log.debug("Volume filter not met: {} < {}", volume24h, tradingProperties.getMinVolume24h());
      return null;
    }

    BigDecimal shortSma = calculateSma(klines, 9);
    BigDecimal longSma = calculateSma(klines, 26);
    log.debug("shortSMA={} longSMA={} lastPrice={}", shortSma, longSma, lastPrice);

    if (shortSma.compareTo(longSma) > 0 && lastPrice.compareTo(shortSma) > 0) {
      signals.increment();
      return new StrategySignal(Instant.now(), OrderSide.BUY, lastPrice, null, "SMA_CROSS_UP");
    }
    if (shortSma.compareTo(longSma) < 0 && lastPrice.compareTo(longSma) < 0) {
      signals.increment();
      return new StrategySignal(Instant.now(), OrderSide.SELL, lastPrice, null, "SMA_CROSS_DOWN");
    }
    return null;
  }

  private BigDecimal calculateSma(List<Kline> klines, int period) {
    Deque<BigDecimal> subset = new ArrayDeque<>(period);
    for (int i = klines.size() - period; i < klines.size(); i++) {
      subset.add(klines.get(i).close());
    }
    BigDecimal sum = subset.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    return sum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
  }
}
