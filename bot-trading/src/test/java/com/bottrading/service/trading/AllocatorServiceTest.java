package com.bottrading.service.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.bottrading.config.TradingProps;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.enums.PositionStatus;
import com.bottrading.repository.PositionRepository;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.risk.IntradayVarService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AllocatorServiceTest {

  private static final String SYMBOL = "BTCUSDT";

  @Mock private PositionRepository positionRepository;
  @Mock private BinanceClient binanceClient;
  @Mock private IntradayVarService intradayVarService;

  private TradingProps tradingProps;
  private AllocatorService allocatorService;

  @BeforeEach
  void setUp() {
    tradingProps = new TradingProps();
    tradingProps.setRiskPerTradePct(BigDecimal.valueOf(0.25));
    tradingProps.getAllocator().setEnabled(true);
    tradingProps.getAllocator().setMaxSimultaneous(2);
    tradingProps.getAllocator().setPerSymbolMaxRiskPct(BigDecimal.ONE);
    tradingProps.getAllocator().setPortfolioMaxTotalRiskPct(BigDecimal.valueOf(10));

    allocatorService =
        new AllocatorService(
            tradingProps,
            positionRepository,
            binanceClient,
            new SimpleMeterRegistry(),
            intradayVarService);

    when(positionRepository.findByStatus(PositionStatus.OPEN)).thenReturn(List.of());
    when(positionRepository.findByStatus(PositionStatus.OPENING)).thenReturn(List.of());
    when(positionRepository.findByStatus(PositionStatus.CLOSING)).thenReturn(List.of());
    when(intradayVarService.isEnabled()).thenReturn(false);
  }

  @Test
  void returnsOkDecisionWhenAllocatorDisabled() {
    tradingProps.getAllocator().setEnabled(false);

    AllocatorService.AllocationDecision decision = allocatorService.evaluate(SYMBOL);

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.reason()).isEqualTo("OK");
    assertThat(decision.sizingMultiplier()).isEqualTo(1.0);
  }

  @Test
  void blocksWhenMaxSimultaneousReached() {
    PositionEntity open = new PositionEntity();
    open.setSymbol("ETHUSDT");
    open.setStatus(PositionStatus.OPEN);
    when(positionRepository.findByStatus(PositionStatus.OPEN)).thenReturn(List.of(open));
    tradingProps.getAllocator().setMaxSimultaneous(1);

    AllocatorService.AllocationDecision decision = allocatorService.evaluate(SYMBOL);

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo("MAX_SIMULTANEOUS");
    assertThat(decision.sizingMultiplier()).isZero();
  }

  @Test
  void blocksWhenPerSymbolRiskExceeded() {
    PositionEntity existing = new PositionEntity();
    existing.setSymbol(SYMBOL);
    existing.setStatus(PositionStatus.OPEN);
    when(positionRepository.findByStatus(PositionStatus.OPEN)).thenReturn(List.of(existing));
    tradingProps.getAllocator().setPerSymbolMaxRiskPct(BigDecimal.valueOf(0.1));

    AllocatorService.AllocationDecision decision = allocatorService.evaluate(SYMBOL);

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo("SYMBOL_RISK");
  }
}
