package com.bottrading.research.ga;

import com.bottrading.research.ga.io.GenomeFile;
import com.bottrading.research.ga.io.GenomeSection;
import com.bottrading.strategy.CompositeStrategy;
import com.bottrading.strategy.Signal;
import com.bottrading.strategy.signals.BollingerBandsSignal;
import com.bottrading.strategy.signals.DonchianChannelSignal;
import com.bottrading.strategy.signals.EmaCrossoverSignal;
import com.bottrading.strategy.signals.MacdSignal;
import com.bottrading.strategy.signals.RsiSignal;
import com.bottrading.strategy.signals.SmaCrossoverSignal;
import com.bottrading.strategy.signals.StochasticSignal;
import com.bottrading.strategy.signals.SupertrendSignal;
import com.bottrading.strategy.signals.VwapSignal;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class GenomeStrategyBuilder {

  private GenomeStrategyBuilder() {}

  public static CompositeStrategy build(GenomeFile file) {
    CompositeStrategy strategy = new CompositeStrategy();
    GenomeSection buy = file.buy();
    GenomeSection sell = file.sell();
    double buyThreshold = buy != null ? buy.threshold() : 1.0;
    double sellThreshold = sell != null ? sell.threshold() : 1.0;
    strategy.thresholds(buyThreshold, sellThreshold);

    Set<String> signalTypes = new LinkedHashSet<>();
    if (buy != null) {
      signalTypes.addAll(buy.weights().keySet());
      signalTypes.addAll(buy.enabledSignals());
      signalTypes.addAll(buy.params().keySet());
    }
    if (sell != null) {
      signalTypes.addAll(sell.weights().keySet());
      signalTypes.addAll(sell.enabledSignals());
      signalTypes.addAll(sell.params().keySet());
    }

    for (String type : signalTypes) {
      Map<String, Double> params = paramsFor(type, buy, sell);
      double confidence = confidenceFor(type, buy, sell);
      Signal signal = createSignal(type, params, confidence);
      if (signal == null) {
        continue;
      }
      double buyWeight = buy != null ? buy.weights().getOrDefault(type, 0.0) : 0.0;
      double sellWeight = sell != null ? sell.weights().getOrDefault(type, 0.0) : 0.0;
      boolean buyEnabled = buy != null && buy.enabledSignals().contains(type);
      boolean sellEnabled = sell != null && sell.enabledSignals().contains(type);
      strategy.addSignalForSides(signal, buyWeight, sellWeight, buyEnabled, sellEnabled);
    }
    return strategy;
  }

  private static Map<String, Double> paramsFor(String type, GenomeSection buy, GenomeSection sell) {
    if (buy != null && buy.params().containsKey(type)) {
      return buy.paramsFor(type);
    }
    if (sell != null && sell.params().containsKey(type)) {
      return sell.paramsFor(type);
    }
    return Map.of();
  }

  private static double confidenceFor(String type, GenomeSection buy, GenomeSection sell) {
    if (buy != null && buy.confidences().containsKey(type)) {
      return buy.confidences().get(type);
    }
    if (sell != null && sell.confidences().containsKey(type)) {
      return sell.confidences().get(type);
    }
    return 1.0;
  }

  private static Signal createSignal(String type, Map<String, Double> params, double confidence) {
    return switch (type.toUpperCase()) {
      case "SMA_CROSS" ->
          new SmaCrossoverSignal(
              (int) Math.round(params.getOrDefault("fast", 9.0)),
              (int) Math.round(params.getOrDefault("slow", 21.0)),
              confidence);
      case "EMA_CROSS" ->
          new EmaCrossoverSignal(
              (int) Math.round(params.getOrDefault("fast", 12.0)),
              (int) Math.round(params.getOrDefault("slow", 26.0)),
              confidence);
      case "MACD" ->
          new MacdSignal(
              (int) Math.round(params.getOrDefault("fast", 12.0)),
              (int) Math.round(params.getOrDefault("slow", 26.0)),
              (int) Math.round(params.getOrDefault("signal", 9.0)),
              confidence);
      case "RSI" ->
          new RsiSignal(
              (int) Math.round(params.getOrDefault("period", 14.0)),
              params.getOrDefault("lower", 30.0),
              params.getOrDefault("upper", 70.0),
              (int) Math.round(params.getOrDefault("trendSma", 50.0)),
              confidence);
      case "BOLLINGER" ->
          new BollingerBandsSignal(
              (int) Math.round(params.getOrDefault("period", 20.0)),
              params.getOrDefault("stdDevs", 2.0),
              confidence);
      case "SUPERTREND" ->
          new SupertrendSignal(
              (int) Math.round(params.getOrDefault("atrPeriod", 10.0)),
              params.getOrDefault("multiplier", 3.0),
              confidence);
      case "STOCHASTIC" ->
          new StochasticSignal(
              (int) Math.round(params.getOrDefault("k", 14.0)),
              (int) Math.round(params.getOrDefault("d", 3.0)),
              confidence);
      case "DONCHIAN" ->
          new DonchianChannelSignal((int) Math.round(params.getOrDefault("period", 20.0)), confidence);
      case "VWAP" ->
          new VwapSignal(
              params.getOrDefault("confirmation", 0.0) >= 0.5,
              (int) Math.round(params.getOrDefault("confirmationBars", 3.0)),
              confidence);
      default -> null;
    };
  }
}
