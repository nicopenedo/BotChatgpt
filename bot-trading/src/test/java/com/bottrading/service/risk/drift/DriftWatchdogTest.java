package com.bottrading.service.risk.drift;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.config.TradingProps;
import com.bottrading.service.risk.TradingState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriftWatchdogTest {

  private TradingProps props;
  private TradingState tradingState;
  private DriftWatchdog watchdog;

  @BeforeEach
  void setUp() {
    props = new TradingProps();
    props.getDrift().setEnabled(true);
    props.getDrift().setWindowTrades(6);
    props.getDrift().setThresholdPfDrop(0.2);
    props.getDrift().setThresholdMaxddPct(5.0);
    props.getDrift().setExpectedProfitFactor(1.5);
    props.getDrift().setExpectedWinRate(0.6);
    tradingState = new TradingState();
    tradingState.setLiveEnabled(true);
    watchdog = new DriftWatchdog(props, tradingState, new SimpleMeterRegistry());
  }

  @Test
  void shouldEscalateAndPauseWhenPerformanceDrops() {
    DriftWatchdog.Status status = triggerPause();
    assertThat(status.stage()).isEqualTo(DriftWatchdog.Stage.PAUSED);
    assertThat(watchdog.allowTrading()).isFalse();
    assertThat(watchdog.sizingMultiplier()).isZero();
    assertThat(tradingState.getMode()).isEqualTo(TradingState.Mode.PAUSED);
  }

  @Test
  void shouldResetToNormalAndResumeTrading() {
    triggerPause();
    watchdog.reset();

    DriftWatchdog.Status status = watchdog.status();
    assertThat(status.stage()).isEqualTo(DriftWatchdog.Stage.NORMAL);
    assertThat(status.sizingMultiplier()).isEqualTo(1.0);
    assertThat(watchdog.allowTrading()).isTrue();
    assertThat(tradingState.getMode()).isEqualTo(TradingState.Mode.LIVE);
  }

  private DriftWatchdog.Status triggerPause() {
    for (int i = 0; i < 6; i++) {
      watchdog.recordShadowTrade("BTCUSDT", 2.0);
    }
    for (int i = 0; i < 6; i++) {
      watchdog.recordLiveTrade("BTCUSDT", -2.5);
    }
    return watchdog.status();
  }
}

