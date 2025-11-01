package com.bottrading.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.config.StopProperties;
import com.bottrading.config.StopProperties.Mode;
import com.bottrading.model.enums.OrderSide;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StopEngineTests {

  @Test
  void shouldPlanStopsUsingPercentagesForBuy() {
    StopProperties properties = new StopProperties();
    properties.setMode(Mode.PERCENT);
    properties.setSlPct(BigDecimal.valueOf(2));
    properties.setTpPct(BigDecimal.valueOf(4));
    StopEngine engine = new StopEngine(properties);

    StopEngine.StopPlan plan = engine.plan("BTCUSDT", OrderSide.BUY, BigDecimal.valueOf(100), null);

    assertThat(plan.stopLoss()).isEqualByComparingTo("98.00000000");
    assertThat(plan.takeProfit()).isEqualByComparingTo("104.00000000");
  }

  @Test
  void shouldPlanStopsUsingAtrForSell() {
    StopProperties properties = new StopProperties();
    properties.setMode(Mode.ATR);
    properties.setSlAtrMult(BigDecimal.valueOf(2));
    properties.setTpAtrMult(BigDecimal.valueOf(3));
    StopEngine engine = new StopEngine(properties);

    StopEngine.StopPlan plan =
        engine.plan("BTCUSDT", OrderSide.SELL, BigDecimal.valueOf(100), BigDecimal.valueOf(1.5));

    assertThat(plan.stopLoss()).isEqualByComparingTo("103.00000000");
    assertThat(plan.takeProfit()).isEqualByComparingTo("95.50000000");
  }
}
