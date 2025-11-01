package com.bottrading.service.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.bottrading.config.RiskProperties;
import com.bottrading.config.TradingProps;
import com.bottrading.execution.PositionManager;
import com.bottrading.model.entity.RiskEventEntity;
import com.bottrading.notify.TelegramNotifier;
import com.bottrading.repository.RiskEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

class RiskGuardTest {

  private TradingProps tradingProps;
  private RiskProperties riskProperties;
  private TradingState tradingState;
  private TelegramNotifier notifier;
  private PositionManager positionManager;
  private RiskEventRepository riskEventRepository;
  private RiskGuard riskGuard;

  @BeforeEach
  void setUp() {
    tradingProps = new TradingProps();
    riskProperties = new RiskProperties();
    riskProperties.setMaxDailyLossPct(BigDecimal.valueOf(2));
    riskProperties.setForceCloseOnPause(true);
    tradingState = new TradingState();
    notifier = mock(TelegramNotifier.class);
    positionManager = mock(PositionManager.class);
    riskEventRepository = Mockito.mock(RiskEventRepository.class);
    Mockito.lenient()
        .when(riskEventRepository.save(Mockito.any(RiskEventEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
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
            riskEventRepository,
            new SimpleMeterRegistry());
  }

  @Test
  void shouldPauseAndForceCloseWhenDailyLossExceeded() {
    riskGuard.onEquityUpdate(BigDecimal.valueOf(1000));
    riskGuard.onEquityUpdate(BigDecimal.valueOf(950));

    assertThat(tradingState.isKillSwitchActive()).isTrue();
    assertThat(riskGuard.getState().flags()).contains(RiskFlag.DAILY_LOSS);
    verify(positionManager).forceCloseAll();
  }

  @Test
  void shouldBlockWhenMarketDataIsStale() {
    assertThat(riskGuard.canOpen("BTCUSDT")).isTrue();
    riskGuard.setMarketDataStale(true);
    assertThat(riskGuard.canOpen("BTCUSDT")).isFalse();
    riskGuard.setMarketDataStale(false);
    assertThat(riskGuard.canOpen("BTCUSDT")).isTrue();
  }
}
