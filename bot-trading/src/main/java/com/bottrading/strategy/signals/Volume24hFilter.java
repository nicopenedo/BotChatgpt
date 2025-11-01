package com.bottrading.strategy.signals;

import com.bottrading.strategy.Signal;
import com.bottrading.strategy.SignalResult;
import com.bottrading.strategy.StrategyContext;
import java.util.List;

public class Volume24hFilter implements Signal {

  private final double minQuoteVolume;
  private StrategyContext context;

  public Volume24hFilter(double minQuoteVolume) {
    this.minQuoteVolume = minQuoteVolume;
  }

  @Override
  public void applyContext(StrategyContext context) {
    this.context = context;
  }

  @Override
  public SignalResult evaluate(List<String[]> klines) {
    if (context == null || context.volume24h() == null) {
      return SignalResult.flat("24h volume unavailable");
    }
    double volume = context.volume24h().doubleValue();
    if (volume < minQuoteVolume) {
      return SignalResult.flat(
          "24h volume %.2f below %.2f".formatted(volume, minQuoteVolume));
    }
    return SignalResult.buy(1.0, "24h volume %.2f >= %.2f".formatted(volume, minQuoteVolume));
  }
}
