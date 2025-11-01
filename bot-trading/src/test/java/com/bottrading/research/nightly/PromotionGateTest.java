package com.bottrading.research.nightly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.research.backtest.MetricsSummary;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PromotionGateTest {

  @Test
  void oosGateRejectsWhenTradesBelowMinimum() {
    ResearchProperties properties = new ResearchProperties();
    ResearchProperties.Nightly.Gate gate = properties.getNightly().getGate();
    gate.setMinTradesOos(150);
    gate.setPfBaseline(1.0);
    MetricsSummary metrics =
        new MetricsSummary(
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.valueOf(6.0),
            BigDecimal.valueOf(1.5),
            BigDecimal.valueOf(0.55),
            BigDecimal.ONE,
            BigDecimal.ONE,
            100,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE);

    PromotionGate.GateDecision decision =
        PromotionGate.evaluateOos(metrics, gate, gate.getPfBaseline());

    assertThat(decision.approved()).isFalse();
    assertThat(decision.reason()).contains("Trades");
  }

  @Test
  void shadowGateRejectsWhenProfitFactorDropsTooMuch() {
    ResearchProperties properties = new ResearchProperties();
    ResearchProperties.Nightly.Gate gate = properties.getNightly().getGate();
    gate.setShadowMinTrades(50);
    gate.setShadowPfDropTolerance(0.15);
    double pfOos = 1.6;
    double pfShadow = 1.0;

    PromotionGate.GateDecision decision =
        PromotionGate.evaluateShadow(pfOos, pfShadow, 60, gate);

    assertThat(decision.approved()).isFalse();
    assertThat(decision.reason()).contains("Shadow PF");
  }
}
