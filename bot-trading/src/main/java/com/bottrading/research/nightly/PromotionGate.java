package com.bottrading.research.nightly;

import com.bottrading.research.backtest.MetricsSummary;

public final class PromotionGate {

  private PromotionGate() {}

  public static GateDecision evaluateOos(
      MetricsSummary metrics, ResearchProperties.Nightly.Gate gate, double baselinePf) {
    if (metrics == null || gate == null) {
      return new GateDecision(false, "Missing metrics");
    }
    int trades = metrics.trades();
    if (trades < gate.getMinTradesOos()) {
      return new GateDecision(false, "Trades below minimum");
    }
    double pf = metrics.profitFactor() != null ? metrics.profitFactor().doubleValue() : 0.0d;
    double requiredPf = baselinePf * (1.0 + gate.getEpsilonPf());
    if (pf < requiredPf) {
      return new GateDecision(false, "Profit factor below baseline");
    }
    double maxDd = metrics.maxDrawdown() != null ? metrics.maxDrawdown().doubleValue() : 0.0d;
    if (maxDd > gate.getMaxddCapPct()) {
      return new GateDecision(false, "Max drawdown above cap");
    }
    return new GateDecision(true, "ok");
  }

  public static GateDecision evaluateShadow(
      double pfOos, double pfShadow, int trades, ResearchProperties.Nightly.Gate gate) {
    if (gate == null) {
      return new GateDecision(false, "Missing gate config");
    }
    if (trades < gate.getShadowMinTrades()) {
      return new GateDecision(false, "Shadow trades below minimum");
    }
    double threshold = pfOos * (1.0 - gate.getShadowPfDropTolerance());
    if (Double.isFinite(threshold) && pfShadow < threshold) {
      return new GateDecision(false, "Shadow PF below tolerance");
    }
    return new GateDecision(true, "ok");
  }

  public record GateDecision(boolean approved, String reason) {}
}
