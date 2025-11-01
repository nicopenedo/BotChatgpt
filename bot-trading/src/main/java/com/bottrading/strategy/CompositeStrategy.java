package com.bottrading.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public class CompositeStrategy {

  private final List<WeightedSignal> signals = new ArrayList<>();
  private final List<Signal> filters = new ArrayList<>();
  private double buyThreshold = 1.0;
  private double sellThreshold = 1.0;

  public CompositeStrategy addSignal(Signal signal, double weight) {
    Objects.requireNonNull(signal, "signal");
    if (weight <= 0) {
      throw new IllegalArgumentException("weight must be positive");
    }
    signals.add(new WeightedSignal(signal, weight, weight, true, true));
    return this;
  }

  public CompositeStrategy addSignalForSides(
      Signal signal,
      double buyWeight,
      double sellWeight,
      boolean buyEnabled,
      boolean sellEnabled) {
    Objects.requireNonNull(signal, "signal");
    if (buyWeight < 0 || sellWeight < 0) {
      throw new IllegalArgumentException("weights must be non-negative");
    }
    signals.add(new WeightedSignal(signal, buyWeight, sellWeight, buyEnabled, sellEnabled));
    return this;
  }

  public CompositeStrategy addFilter(Signal filter) {
    Objects.requireNonNull(filter, "filter");
    filters.add(filter);
    return this;
  }

  public CompositeStrategy thresholds(double buyThreshold, double sellThreshold) {
    this.buyThreshold = buyThreshold;
    this.sellThreshold = sellThreshold;
    return this;
  }

  public SignalResult evaluate(List<String[]> klines) {
    return evaluate(klines, StrategyContext.builder().symbol("UNKNOWN").build());
  }

  public SignalResult evaluate(List<String[]> klines, StrategyContext context) {
    StrategyContext ctx = context;
    if (ctx == null) {
      ctx = StrategyContext.builder().symbol("UNKNOWN").build();
    }

    List<String> notes = new ArrayList<>();

    for (Signal filter : filters) {
      filter.applyContext(ctx);
      SignalResult result = filter.evaluate(klines);
      if (result != null && !result.note().isBlank()) {
        notes.add("[" + filter.name() + "] " + result.note());
      }
      if (result == null || result.side() == SignalSide.FLAT) {
        return SignalResult.flat(joinNotes(notes, "Filters blocking"));
      }
    }

    double buyScore = 0;
    double sellScore = 0;
    List<String> buySignals = new ArrayList<>();
    List<String> sellSignals = new ArrayList<>();

    for (WeightedSignal weighted : signals) {
      weighted.signal().applyContext(ctx);
      SignalResult result = weighted.signal().evaluate(klines);
      if (result == null) {
        continue;
      }
      if (result.side() == SignalSide.BUY && weighted.buyEnabled()) {
        buyScore += weighted.buyWeight() * result.confidence();
        buySignals.add(voteLabel(weighted.signal(), result));
      } else if (result.side() == SignalSide.SELL && weighted.sellEnabled()) {
        sellScore += weighted.sellWeight() * result.confidence();
        sellSignals.add(voteLabel(weighted.signal(), result));
      }
      if (result.note() != null && !result.note().isBlank()) {
        notes.add("[" + weighted.signal().name() + "] " + result.note());
      }
    }

    double reference = Math.max(buyThreshold, sellThreshold);
    double confidence =
        reference <= 0 ? 0 : Math.min(1.0, Math.max(buyScore, sellScore) / reference);

    if (buyScore >= buyThreshold) {
      return SignalResult.buy(confidence, joinNotes(notes, "BUY"), buySignals);
    }
    if (sellScore >= sellThreshold) {
      return SignalResult.sell(confidence, joinNotes(notes, "SELL"), sellSignals);
    }
    return SignalResult.flat(joinNotes(notes, "FLAT"));
  }

  private String voteLabel(Signal signal, SignalResult result) {
    String base = signal.name();
    if (result.note() == null || result.note().isBlank()) {
      return base;
    }
    return base + " - " + result.note();
  }

  private String joinNotes(List<String> notes, String defaultNote) {
    if (notes.isEmpty()) {
      return defaultNote;
    }
    StringJoiner joiner = new StringJoiner(" | ");
    notes.forEach(joiner::add);
    return joiner.toString();
  }

  public List<Signal> getSignals() {
    List<Signal> list = new ArrayList<>();
    for (WeightedSignal ws : signals) {
      list.add(ws.signal());
    }
    return Collections.unmodifiableList(list);
  }

  public List<Signal> getFilters() {
    return Collections.unmodifiableList(filters);
  }

  private record WeightedSignal(
      Signal signal, double buyWeight, double sellWeight, boolean buyEnabled, boolean sellEnabled) {}
}
