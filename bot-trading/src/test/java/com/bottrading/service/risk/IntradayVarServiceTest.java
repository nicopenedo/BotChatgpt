package com.bottrading.service.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.bottrading.config.VarProperties;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PositionStatus;
import com.bottrading.repository.PositionRepository;
import com.bottrading.repository.RiskVarSnapshotRepository;
import com.bottrading.repository.ShadowPositionRepository;
import com.bottrading.service.risk.IntradayVarService.HistoricalSample;
import com.bottrading.service.risk.IntradayVarService.SampleUniverse;
import com.bottrading.service.risk.IntradayVarService.VarAssessment;
import com.bottrading.service.risk.IntradayVarService.VarInput;
import com.bottrading.service.risk.IntradayVarService.VarReason;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class IntradayVarServiceTest {

  @Mock private RiskVarSnapshotRepository snapshotRepository;
  @Mock private PositionRepository positionRepository;
  @Mock private ShadowPositionRepository shadowPositionRepository;
  @Mock private NamedParameterJdbcTemplate jdbcTemplate;

  private SimpleMeterRegistry meterRegistry;

  private IntradayVarService service;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    VarProperties props = new VarProperties();
    props.setEnabled(true);
    props.setQuantile(0.99);
    props.setCvarTargetPctPerTrade(0.25);
    props.setCvarTargetPctPerDay(1.5);
    props.setMcIterations(5000);
    props.setHeavyTails(false);
    service = new IntradayVarService(
        props,
        snapshotRepository,
        positionRepository,
        shadowPositionRepository,
        jdbcTemplate,
        meterRegistry);
    service = org.mockito.Mockito.spy(service);
    when(positionRepository.findByStatus(PositionStatus.OPEN)).thenReturn(List.of());
    when(positionRepository.findByStatus(PositionStatus.OPENING)).thenReturn(List.of());
    when(positionRepository.findByStatus(PositionStatus.CLOSING)).thenReturn(List.of());
  }

  @Test
  void assessShouldReduceQuantityWhenTradeCvarExceedsTarget() {
    HistoricalSample sample = new HistoricalSample(-1.5, 0, null, null, null, null);
    doReturn(new SampleUniverse(List.of(sample), List.of()))
        .when(service)
        .loadSamples(any(), any(), any(), any());

    VarInput input =
        new VarInput(
            "BTCUSDT",
            "default",
            null,
            null,
            null,
            OrderSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(99),
            BigDecimal.valueOf(5),
            BigDecimal.valueOf(10_000),
            BigDecimal.valueOf("0.001"));

    VarAssessment assessment = service.assess(input);
    assertThat(assessment.adjustedQuantity()).isLessThan(input.quantity());
    assertThat(assessment.reasons())
        .extracting(VarReason::code)
        .contains("TRADE_LIMIT");
  }

  @Test
  void assessShouldBlockWhenDailyBudgetExceeded() {
    HistoricalSample sample = new HistoricalSample(-2.0, 0, null, null, null, null);
    doReturn(new SampleUniverse(List.of(sample), List.of()))
        .when(service)
        .loadSamples(any(), any(), any(), any());

    PositionEntity openPosition = new PositionEntity();
    openPosition.setStatus(PositionStatus.OPEN);
    openPosition.setSymbol("BTCUSDT");
    openPosition.setSide(OrderSide.BUY);
    openPosition.setEntryPrice(BigDecimal.valueOf(100));
    openPosition.setStopLoss(BigDecimal.valueOf(95));
    openPosition.setQtyRemaining(BigDecimal.valueOf(500));
    openPosition.setPresetKey("default");
    when(positionRepository.findByStatus(PositionStatus.OPEN)).thenReturn(List.of(openPosition));
    when(positionRepository.findByStatus(PositionStatus.OPENING)).thenReturn(List.of());
    when(positionRepository.findByStatus(PositionStatus.CLOSING)).thenReturn(List.of());

    VarInput input =
        new VarInput(
            "BTCUSDT",
            "default",
            UUID.randomUUID(),
            null,
            null,
            OrderSide.BUY,
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(95),
            BigDecimal.ONE,
            BigDecimal.valueOf(10_000),
            BigDecimal.valueOf("0.001"));

    VarAssessment assessment = service.assess(input);
    assertThat(assessment.blocked()).isTrue();
    assertThat(assessment.reasons()).extracting(VarReason::code).contains("DAILY_LIMIT");
  }
}
