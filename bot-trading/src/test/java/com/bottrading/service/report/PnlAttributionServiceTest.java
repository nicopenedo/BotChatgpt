package com.bottrading.service.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bottrading.fees.FeeService;
import com.bottrading.fees.FeeService.FeeInfo;
import com.bottrading.model.dto.Kline;
import com.bottrading.model.entity.DecisionEntity;
import com.bottrading.model.entity.PnlAttributionEntity;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.entity.TradeEntity;
import com.bottrading.model.entity.TradeFillEntity;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.repository.DecisionRepository;
import com.bottrading.repository.PnlAttributionRepository;
import com.bottrading.repository.TradeFillRepository;
import com.bottrading.service.market.MarketDataService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class PnlAttributionServiceTest {

  @Mock private PnlAttributionRepository repository;
  @Mock private DecisionRepository decisionRepository;
  @Mock private TradeFillRepository tradeFillRepository;
  @Mock private MarketDataService marketDataService;
  @Mock private FeeService feeService;

  private PnlAttributionService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(feeService.effectiveFees(any(), any(Boolean.class)))
        .thenReturn(new FeeInfo(BigDecimal.ZERO, BigDecimal.ZERO, 0, true));
    service =
        new PnlAttributionService(
            repository,
            decisionRepository,
            tradeFillRepository,
            marketDataService,
            feeService,
            new SimpleMeterRegistry());
  }

  @Test
  void slippageCostIncreasesWithSpread() {
    PositionEntity position = new PositionEntity();
    position.setId(1L);
    position.setSymbol("BTCUSDT");
    position.setEntryPrice(BigDecimal.valueOf(100));
    position.setSide(OrderSide.BUY);
    position.setCorrelationId("signal-1");

    DecisionEntity decision = new DecisionEntity();
    decision.setDecisionKey("signal-1");
    decision.setSymbol("BTCUSDT");
    decision.setInterval("1m");
    Instant closeTime = Instant.parse("2024-01-01T00:00:00Z");
    decision.setCloseTime(closeTime);
    when(decisionRepository.findByDecisionKey("signal-1")).thenReturn(Optional.of(decision));

    Kline kline =
        new Kline(
            closeTime.minusSeconds(60),
            closeTime,
            BigDecimal.valueOf(99.5),
            BigDecimal.valueOf(101),
            BigDecimal.valueOf(99),
            BigDecimal.valueOf(100),
            BigDecimal.ONE);
    when(marketDataService.getKlines("BTCUSDT", "1m", closeTime, closeTime, 5))
        .thenReturn(List.of(kline));

    TradeEntity tradeTight = new TradeEntity();
    tradeTight.setId(1L);
    tradeTight.setPrice(BigDecimal.valueOf(101));
    tradeTight.setQuantity(BigDecimal.ONE);
    tradeTight.setSide(OrderSide.SELL);
    tradeTight.setOrderId("ord-tight");
    tradeTight.setExecutedAt(closeTime.plusSeconds(10));

    TradeEntity tradeWide = new TradeEntity();
    tradeWide.setId(2L);
    tradeWide.setPrice(BigDecimal.valueOf(102));
    tradeWide.setQuantity(BigDecimal.ONE);
    tradeWide.setSide(OrderSide.SELL);
    tradeWide.setOrderId("ord-wide");
    tradeWide.setExecutedAt(closeTime.plusSeconds(20));

    TradeFillEntity fillTight = new TradeFillEntity();
    fillTight.setRefPrice(BigDecimal.valueOf(100.9));
    fillTight.setFillPrice(BigDecimal.valueOf(101));
    fillTight.setExecutedAt(closeTime.plusSeconds(10));

    TradeFillEntity fillWide = new TradeFillEntity();
    fillWide.setRefPrice(BigDecimal.valueOf(100.5));
    fillWide.setFillPrice(BigDecimal.valueOf(102));
    fillWide.setExecutedAt(closeTime.plusSeconds(20));

    when(tradeFillRepository.findTopByOrderIdOrderByExecutedAtDesc("ord-tight"))
        .thenReturn(fillTight);
    when(tradeFillRepository.findTopByOrderIdOrderByExecutedAtDesc("ord-wide"))
        .thenReturn(fillWide);

    service.record(position, tradeTight);
    service.record(position, tradeWide);

    ArgumentCaptor<PnlAttributionEntity> captor = ArgumentCaptor.forClass(PnlAttributionEntity.class);
    verify(repository, times(2)).save(captor.capture());
    List<PnlAttributionEntity> saved = captor.getAllValues();
    double tightSlippage = saved.get(0).getSlippageCost().doubleValue();
    double wideSlippage = saved.get(1).getSlippageCost().doubleValue();

    assertThat(Math.abs(wideSlippage)).isGreaterThan(Math.abs(tightSlippage));
    assertThat(saved.get(1).getPnlGross()).isNotNull();
  }
}
