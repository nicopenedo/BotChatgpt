package com.bottrading.service.risk.drift;

import com.bottrading.config.TradingProps;
import com.bottrading.service.risk.TradingState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DriftWatchdog {

  private static final Logger log = LoggerFactory.getLogger(DriftWatchdog.class);

  private final TradingProps tradingProps;
  private final TradingState tradingState;
  private final PerformanceWindow liveWindow;
  private final PerformanceWindow shadowWindow;
  private final Counter downgradeCounter;
  private final AtomicReference<Stage> stage = new AtomicReference<>(Stage.NORMAL);
  private final AtomicReference<Double> sizingMultiplier = new AtomicReference<>(1.0);

  public DriftWatchdog(TradingProps tradingProps, TradingState tradingState, MeterRegistry meterRegistry) {
    this.tradingProps = tradingProps;
    this.tradingState = tradingState;
    this.liveWindow = new PerformanceWindow(tradingProps.getDrift().getWindowTrades());
    this.shadowWindow = new PerformanceWindow(tradingProps.getDrift().getWindowTrades());
    this.downgradeCounter = meterRegistry.counter("drift.downgrades", Tags.empty());
    Gauge.builder("drift.stage", stage, st -> st.get().ordinal())
        .tags(Tags.empty())
        .register(meterRegistry);
    Gauge.builder("drift.sizing.multiplier", sizingMultiplier, AtomicReference::get)
        .tags(Tags.empty())
        .register(meterRegistry);
  }

  public void recordLiveTrade(String symbol, double pnl) {
    if (!tradingProps.getDrift().isEnabled()) {
      return;
    }
    liveWindow.add(pnl);
    evaluate();
  }

  public void recordShadowTrade(String symbol, double pnl) {
    if (!tradingProps.getDrift().isEnabled()) {
      return;
    }
    shadowWindow.add(pnl);
    evaluate();
  }

  public void reset() {
    liveWindow.clear();
    shadowWindow.clear();
    stage.set(Stage.NORMAL);
    sizingMultiplier.set(1.0);
    tradingState.setMode(TradingState.Mode.LIVE);
  }

  public boolean allowTrading() {
    return stage.get() != Stage.PAUSED;
  }

  public double sizingMultiplier() {
    return sizingMultiplier.get();
  }

  public Status status() {
    PerformanceMetrics live = liveWindow.metrics();
    PerformanceMetrics shadow = shadowWindow.metrics();
    return new Status(stage.get(), live, shadow, sizingMultiplier.get());
  }

  private void evaluate() {
    if (!tradingProps.getDrift().isEnabled()) {
      return;
    }
    PerformanceMetrics live = liveWindow.metrics();
    if (live.count() < Math.max(5, tradingProps.getDrift().getWindowTrades() / 2)) {
      return;
    }
    PerformanceMetrics shadow = shadowWindow.metrics();
    TradingProps.DriftProperties props = tradingProps.getDrift();

    boolean pfBreach =
        dropBelow(live.profitFactor(), shadow.profitFactor(), props.getThresholdPfDrop())
            || dropBelow(live.profitFactor(), props.getExpectedProfitFactor(), props.getThresholdPfDrop());
    boolean winBreach = live.winRate() < props.getExpectedWinRate() * (1 - props.getThresholdPfDrop());
    boolean ddBreach = live.maxDrawdownPct() > props.getThresholdMaxddPct();

    if (pfBreach || winBreach || ddBreach) {
      escalate(pfBreach, winBreach, ddBreach);
    } else {
      maybeRecover(live);
    }
  }

  private boolean dropBelow(double actual, double reference, double threshold) {
    if (Double.isNaN(actual) || Double.isNaN(reference) || reference <= 0) {
      return false;
    }
    return actual < reference * (1 - threshold);
  }

  private void escalate(boolean pf, boolean win, boolean dd) {
    if (!tradingProps.getDrift().isActionsAutoDowngrade()) {
      log.warn("Drift detected (pf={} win={} dd={}), but auto downgrade disabled", pf, win, dd);
      return;
    }
    Stage current = stage.get();
    Stage next = switch (current) {
      case NORMAL -> Stage.REDUCED;
      case REDUCED -> Stage.SHADOW;
      case SHADOW -> Stage.PAUSED;
      case PAUSED -> Stage.PAUSED;
    };
    if (next != current) {
      downgradeCounter.increment();
      stage.set(next);
      applyStage(next);
      log.warn("Drift watchdog escalated from {} to {} (pf={} win={} dd={})", current, next, pf, win, dd);
    }
  }

  private void maybeRecover(PerformanceMetrics live) {
    Stage current = stage.get();
    if (current == Stage.NORMAL) {
      return;
    }
    TradingProps.DriftProperties props = tradingProps.getDrift();
    boolean pfRecovered = live.profitFactor() >= props.getExpectedProfitFactor() * 0.9;
    boolean winRecovered = live.winRate() >= props.getExpectedWinRate() * 0.95;
    boolean ddRecovered = live.maxDrawdownPct() <= props.getThresholdMaxddPct() * 0.5;
    if (pfRecovered && winRecovered && ddRecovered) {
      Stage next = switch (current) {
        case PAUSED -> Stage.SHADOW;
        case SHADOW -> Stage.REDUCED;
        case REDUCED -> Stage.NORMAL;
        default -> Stage.NORMAL;
      };
      stage.set(next);
      applyStage(next);
      log.info("Drift watchdog recovered from {} to {}", current, next);
    }
  }

  private void applyStage(Stage next) {
    switch (next) {
      case NORMAL -> {
        sizingMultiplier.set(1.0);
        tradingState.setMode(TradingState.Mode.LIVE);
      }
      case REDUCED -> {
        sizingMultiplier.set(0.5);
        tradingState.setMode(TradingState.Mode.LIVE);
      }
      case SHADOW -> {
        sizingMultiplier.set(0.25);
        tradingState.setMode(TradingState.Mode.SHADOW);
      }
      case PAUSED -> {
        sizingMultiplier.set(0.0);
        tradingState.setMode(TradingState.Mode.PAUSED);
        tradingState.activateKillSwitch();
      }
    }
  }

  public enum Stage {
    NORMAL,
    REDUCED,
    SHADOW,
    PAUSED
  }

  public record Status(Stage stage, PerformanceMetrics live, PerformanceMetrics shadow, double sizingMultiplier) {}

  public record PerformanceMetrics(double profitFactor, double winRate, double maxDrawdownPct, int count) {}

  private static final class PerformanceWindow {
    private final int maxSize;
    private final Deque<Double> pnl = new ArrayDeque<>();
    private PerformanceMetrics metrics = new PerformanceMetrics(Double.NaN, Double.NaN, 0, 0);

    private PerformanceWindow(int maxSize) {
      this.maxSize = Math.max(1, maxSize);
    }

    synchronized void add(double value) {
      pnl.addLast(value);
      while (pnl.size() > maxSize) {
        pnl.removeFirst();
      }
      recompute();
    }

    synchronized void clear() {
      pnl.clear();
      metrics = new PerformanceMetrics(Double.NaN, Double.NaN, 0, 0);
    }

    synchronized PerformanceMetrics metrics() {
      return metrics;
    }

    private void recompute() {
      if (pnl.isEmpty()) {
        metrics = new PerformanceMetrics(Double.NaN, Double.NaN, 0, 0);
        return;
      }
      double sumPos = 0;
      double sumNeg = 0;
      int wins = 0;
      double equity = 0;
      double peak = 0;
      double maxDrawdown = 0;
      for (double trade : pnl) {
        if (trade > 0) {
          sumPos += trade;
          wins++;
        } else if (trade < 0) {
          sumNeg += trade;
        }
        equity += trade;
        peak = Math.max(peak, equity);
        maxDrawdown = Math.max(maxDrawdown, peak - equity);
      }
      double profitFactor =
          sumPos <= 0 || sumNeg >= 0 ? Double.NaN : sumPos / Math.abs(sumNeg);
      double winRate = pnl.isEmpty() ? Double.NaN : (double) wins / pnl.size();
      double maxDdPct = peak <= 0 ? 0 : (maxDrawdown / Math.max(peak, 1e-9)) * 100;
      metrics = new PerformanceMetrics(profitFactor, winRate, maxDdPct, pnl.size());
    }
  }
}
