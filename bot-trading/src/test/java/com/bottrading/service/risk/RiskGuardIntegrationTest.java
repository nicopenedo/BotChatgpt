package com.bottrading.service.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.config.RiskProperties;
import com.bottrading.config.TradingProps;
import com.bottrading.execution.PositionManager;
import com.bottrading.notify.TelegramNotifier;
import com.bottrading.repository.RiskEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

class RiskGuardIntegrationTest {

  private RiskGuard riskGuard;

  @BeforeEach
  void init() {
    TradingProps tradingProps = new TradingProps();
    RiskProperties riskProperties = new RiskProperties();
    riskProperties.setMaxApiErrorPct(BigDecimal.valueOf(5));
    TradingState tradingState = new TradingState();
    TelegramNotifier notifier = Mockito.mock(TelegramNotifier.class);
    PositionManager positionManager = Mockito.mock(PositionManager.class);
    RiskEventRepository repository = Mockito.mock(RiskEventRepository.class);
    ObjectProvider<PositionManager> provider = new ObjectProvider<>() {
      @Override
      public PositionManager getObject(Object... args) {
        return positionManager;
      }

      @Override
      public PositionManager getIfAvailable() {
        return positionManager;
      }

      @Override
      public PositionManager getIfUnique() {
        return positionManager;
      }

      @Override
      public PositionManager getObject() {
        return positionManager;
      }

      @Override
      public java.util.stream.Stream<PositionManager> stream() {
        return java.util.stream.Stream.of(positionManager);
      }
    };
    RiskAction riskAction = new RiskAction(riskProperties, notifier, provider);
    riskGuard =
        new RiskGuard(
            tradingProps,
            riskProperties,
            tradingState,
            riskAction,
            repository,
            new SimpleMeterRegistry());
  }

  @Test
  void shouldBlockNewOpeningsWhenApiErrorRateBreaches() {
    assertThat(riskGuard.canOpen("BTCUSDT")).isTrue();
    riskGuard.onApiError();
    assertThat(riskGuard.canOpen("BTCUSDT")).isFalse();
  }
}
