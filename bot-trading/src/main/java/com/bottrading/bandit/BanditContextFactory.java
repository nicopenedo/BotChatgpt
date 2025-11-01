package com.bottrading.bandit;

import com.bottrading.research.regime.Regime;
import com.bottrading.strategy.StrategyContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class BanditContextFactory {

  public BanditContext build(
      Regime regime,
      StrategyContext strategyContext,
      Double spreadBps,
      Double slippageExpectedBps,
      Double expectedVolatilityBps,
      Long daysSinceActivated) {
    BanditContext.Builder builder = BanditContext.builder();
    if (regime != null) {
      builder
          .put("trend", regime.trend() != null ? regime.trend().name() : null)
          .put("atrPct", sanitize(regime.normalizedAtr()))
          .put("adx", sanitize(regime.adx()))
          .put("rangeScore", sanitize(regime.rangeScore()))
          .put(
              "hourOfDay",
              regime.timestamp() != null
                  ? regime.timestamp().atZone(ZoneOffset.UTC).getHour()
                  : null);
      if (regime.volatility() != null) {
        builder.put("volatility", regime.volatility().name());
      }
    }
    if (strategyContext != null) {
      builder
          .put("symbol", strategyContext.symbol())
          .put("vol24h", value(strategyContext.volume24h()))
          .put(
              "timestamp",
              strategyContext.asOf() != null ? strategyContext.asOf() : Instant.now());
    }
    builder
        .put("spread_bps", spreadBps)
        .put("slippage_expected_bps", slippageExpectedBps)
        .put("volatility_bps", expectedVolatilityBps)
        .put("daysSincePresetActivated", daysSinceActivated);
    return builder.build();
  }

  private Double value(BigDecimal value) {
    return value != null ? value.doubleValue() : null;
  }

  private Double sanitize(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return null;
    }
    return value;
  }
}
