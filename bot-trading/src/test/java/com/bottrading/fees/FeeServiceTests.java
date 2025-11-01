package com.bottrading.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bottrading.config.FeeProperties;
import com.bottrading.service.binance.BinanceClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FeeServiceTests {

  private final BinanceClient binanceClient = Mockito.mock(BinanceClient.class);
  private final FeeProperties properties = new FeeProperties();
  private FeeService feeService;

  @BeforeEach
  void setup() {
    properties.setCacheMinutes(30);
    properties.setPayWithBnb(true);
    feeService = new FeeService(binanceClient, properties, new SimpleMeterRegistry());
  }

  @Test
  void shouldCacheFees() {
    when(binanceClient.getTradingCommission("BTCUSDT"))
        .thenReturn(BigDecimal.valueOf(0.001));

    feeService.effectiveFees("BTCUSDT");
    feeService.effectiveFees("BTCUSDT");

    verify(binanceClient, times(1)).getTradingCommission("BTCUSDT");
  }

  @Test
  void shouldApplyBnbDiscount() {
    when(binanceClient.getTradingCommission("ETHUSDT"))
        .thenReturn(BigDecimal.valueOf(0.001));

    FeeService.FeeInfo info = feeService.effectiveFees("ETHUSDT");

    assertThat(info.maker()).isEqualByComparingTo(BigDecimal.valueOf(0.00075));
    assertThat(info.taker()).isEqualByComparingTo(BigDecimal.valueOf(0.00075));
    assertThat(info.payingWithBnb()).isTrue();
  }
}
