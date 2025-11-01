package com.bottrading.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bottrading.config.SizingProperties;
import com.bottrading.config.TradingProps;
import com.bottrading.model.dto.ExchangeInfo;
import com.bottrading.model.enums.OrderSide;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderSizingServiceTests {

  private final TradingProps tradingProps = new TradingProps();
  private final SizingProperties sizingProperties = new SizingProperties();
  private final OrderSizingService service = new OrderSizingService(tradingProps, sizingProperties);

  @Test
  void shouldCalculateBuyQuantityWithPercentMode() {
    tradingProps.setRiskPerTradePct(BigDecimal.valueOf(1.0));
    ExchangeInfo info =
        new ExchangeInfo(BigDecimal.valueOf(0.01), BigDecimal.valueOf(0.001), BigDecimal.valueOf(10));

    OrderSizingService.OrderSizingResult result =
        service.size(
            OrderSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(95),
            null,
            BigDecimal.valueOf(1000),
            info);

    assertThat(result.quantity()).isEqualByComparingTo(BigDecimal.valueOf(2.000));
    assertThat(result.orderType()).isEqualTo(com.bottrading.model.enums.OrderType.MARKET);
  }

  @Test
  void shouldCalculateSellQuantityRespectingStepSize() {
    tradingProps.setRiskPerTradePct(BigDecimal.valueOf(0.5));
    ExchangeInfo info =
        new ExchangeInfo(BigDecimal.valueOf(0.01), BigDecimal.valueOf(0.1), BigDecimal.valueOf(5));

    OrderSizingService.OrderSizingResult result =
        service.size(
            OrderSide.SELL,
            BigDecimal.valueOf(120),
            BigDecimal.valueOf(125),
            null,
            BigDecimal.valueOf(2000),
            info);

    assertThat(result.quantity()).isEqualByComparingTo(BigDecimal.valueOf(2.0));
  }

  @Test
  void shouldRejectWhenBelowMinNotional() {
    tradingProps.setRiskPerTradePct(BigDecimal.valueOf(0.1));
    ExchangeInfo info =
        new ExchangeInfo(BigDecimal.valueOf(0.01), BigDecimal.valueOf(0.001), BigDecimal.valueOf(100));

    assertThatThrownBy(
            () ->
                service.size(
                    OrderSide.BUY,
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(99.5),
                    null,
                    BigDecimal.valueOf(1000),
                    info))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Notional");
  }
}
