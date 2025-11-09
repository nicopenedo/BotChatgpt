package com.bottrading.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bottrading.bandit.BanditArmRole;
import com.bottrading.bandit.BanditSelection;
import com.bottrading.chaos.ChaosSuite;
import com.bottrading.config.TradingProps;
import com.bottrading.repository.DecisionRepository;
import com.bottrading.service.OrderExecutionService;
import com.bottrading.service.StrategyService;
import com.bottrading.service.anomaly.AnomalyDetector;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.health.HealthService;
import com.bottrading.service.market.CandleSanitizer;
import com.bottrading.service.preset.CanaryStageService;
import com.bottrading.service.risk.RiskGuard;
import com.bottrading.service.risk.TradingState;
import com.bottrading.service.risk.drift.DriftWatchdog;
import com.bottrading.service.trading.AllocatorService;
import com.bottrading.service.trading.AllocatorService.AllocationDecision;
import com.bottrading.strategy.SignalResult;
import com.bottrading.strategy.SignalSide;
import com.bottrading.strategy.StrategyContext;
import com.bottrading.strategy.StrategyDecision;
import com.bottrading.throttle.Throttle;
import com.bottrading.ws.WSKlineSubscriber;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class TradingSchedulerTest {

  private static final String SYMBOL = "BTCUSDT";
  private static final String INTERVAL = "1m";
  private static final long CLOSE_TIME = 1_694_995_200_000L;

  @Mock private StrategyService strategyService;
  @Mock private RiskGuard riskGuard;
  @Mock private BinanceClient binanceClient;
  @Mock private OrderExecutionService orderExecutionService;
  @Mock private DecisionRepository decisionRepository;
  @Mock private WSKlineSubscriber klineSubscriber;
  @Mock private AllocatorService allocatorService;
  @Mock private DriftWatchdog driftWatchdog;
  @Mock private HealthService healthService;
  @Mock private Throttle throttle;
  @Mock private ChaosSuite chaosSuite;
  @Mock private AnomalyDetector anomalyDetector;
  @Mock private CandleSanitizer candleSanitizer;
  @Mock private CanaryStageService canaryStageService;

  private TradingProps tradingProps;
  private TradingState tradingState;
  private MeterRegistry meterRegistry;
  private Clock clock;
  private TradingScheduler scheduler;

  @BeforeEach
  void setUp() {
    tradingProps = new TradingProps();
    tradingProps.setSymbol(SYMBOL);
    tradingProps.setSymbols(List.of(SYMBOL));
    tradingProps.setInterval(INTERVAL);
    tradingProps.setLiveEnabled(true);
    tradingProps.setJitterSeconds(0);
    tradingProps.setMinVolume24h(BigDecimal.ONE);
    tradingProps.setMaxOrdersPerMinute(10);
    tradingState = new TradingState();
    tradingState.setLiveEnabled(true);
    tradingState.setMode(TradingState.Mode.LIVE);
    meterRegistry = new SimpleMeterRegistry();
    clock = Clock.fixed(Instant.parse("2023-08-10T00:00:00Z"), ZoneOffset.UTC);

    ObjectProvider<Clock> clockProvider = mock(ObjectProvider.class);
    when(clockProvider.getIfAvailable()).thenReturn(clock);

    lenient().when(candleSanitizer.sanitize(anyString(), anyString(), anyLong()))
        .thenAnswer(invocation -> List.of(invocation.getArgument(2)));
    lenient().when(allocatorService.evaluate(anyString())).thenReturn(AllocationDecision.ok());
    lenient().when(driftWatchdog.allowTrading()).thenReturn(true);
    lenient().when(driftWatchdog.sizingMultiplier()).thenReturn(1.0);
    lenient().when(anomalyDetector.sizingMultiplier(anyString())).thenReturn(1.0);
    lenient().when(healthService.isHealthy()).thenReturn(true);
    lenient().when(binanceClient.get24hQuoteVolume(anyString())).thenReturn(BigDecimal.TEN);
    lenient().when(riskGuard.canOpen(anyString())).thenReturn(true);
    lenient().when(throttle.submit(any(), anyString(), any()))
        .thenAnswer(invocation -> {
          @SuppressWarnings("unchecked")
          var supplier = (java.util.function.Supplier<Object>) invocation.getArgument(2);
          return CompletableFuture.completedFuture(supplier.get());
        });
    lenient().when(chaosSuite.decorateApiCall(any())).thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(canaryStageService.multiplier(any())).thenReturn(1.0);
    lenient().when(decisionRepository.existsById(anyString())).thenReturn(false);
    lenient().when(decisionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    scheduler =
        new TradingScheduler(
            tradingProps,
            strategyService,
            tradingState,
            riskGuard,
            binanceClient,
            orderExecutionService,
            decisionRepository,
            meterRegistry,
            klineSubscriber,
            allocatorService,
            driftWatchdog,
            healthService,
            clockProvider,
            throttle,
            chaosSuite,
            anomalyDetector,
            candleSanitizer,
            canaryStageService);
  }

  @Test
  void onCandleClosedPersistsDecisionOncePerKey() {
    when(strategyService.decide(SYMBOL)).thenReturn(buyDecision(null));
    when(orderExecutionService.execute(anyString(), anyString(), anyString(), any(), anyLong(), anyDouble()))
        .thenReturn(Optional.of(executionResult()));
    when(decisionRepository.existsById(anyString())).thenReturn(false, true);

    scheduler.onCandleClosed(SYMBOL, INTERVAL, CLOSE_TIME);
    scheduler.onCandleClosed(SYMBOL, INTERVAL, CLOSE_TIME);

    verify(strategyService, times(1)).decide(SYMBOL);
    ArgumentCaptor<com.bottrading.model.entity.DecisionEntity> entityCaptor =
        ArgumentCaptor.forClass(com.bottrading.model.entity.DecisionEntity.class);
    verify(decisionRepository, times(1)).save(entityCaptor.capture());
    assertThat(entityCaptor.getValue().getDecisionKey())
        .isEqualTo(SYMBOL + "|" + INTERVAL + "|" + CLOSE_TIME);
    assertThat(entityCaptor.getValue().isExecuted()).isTrue();
  }

  @Test
  void candidateSelectionUsesCanaryMultiplier() {
    UUID presetId = UUID.randomUUID();
    BanditSelection selection =
        new BanditSelection(UUID.randomUUID(), presetId, BanditArmRole.CANDIDATE, "decision", java.util.Map.of(), "preset");
    when(canaryStageService.multiplier(presetId)).thenReturn(0.5);
    when(strategyService.decide(SYMBOL)).thenReturn(buyDecision(selection));
    when(orderExecutionService.execute(anyString(), anyString(), anyString(), any(), anyLong(), anyDouble()))
        .thenReturn(Optional.of(executionResult()));

    scheduler.onCandleClosed(SYMBOL, INTERVAL, CLOSE_TIME);

    ArgumentCaptor<Double> multiplierCaptor = ArgumentCaptor.forClass(Double.class);
    verify(orderExecutionService)
        .execute(anyString(), anyString(), anyString(), any(), anyLong(), multiplierCaptor.capture());
    assertThat(multiplierCaptor.getValue()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-9));
  }

  @Test
  void liveDisabledGateBlocksDecision() {
    tradingProps.setLiveEnabled(false);
    runGateScenario("LIVE_DISABLED");
  }

  @Test
  void killSwitchGateBlocksDecision() {
    tradingState.activateKillSwitch();
    runGateScenario("KILL_SWITCH");
  }

  @Test
  void riskGuardGateBlocksDecision() {
    when(riskGuard.canOpen(SYMBOL)).thenReturn(false);
    runGateScenario("RISK_GUARD");
  }

  @Test
  void windowGateBlocksDecision() {
    tradingProps.setTradingHours("12:00-13:00");
    runGateScenario("WINDOW");
  }

  @Test
  void volumeGateBlocksDecision() {
    when(binanceClient.get24hQuoteVolume(SYMBOL)).thenReturn(BigDecimal.ZERO);
    runGateScenario("VOLUME");
  }

  @Test
  void rateLimitGateBlocksDecision() {
    tradingProps.setMaxOrdersPerMinute(0);
    runGateScenario("RATE_LIMIT");
  }

  @Test
  void healthGateBlocksDecision() {
    when(healthService.isHealthy()).thenReturn(false);
    runGateScenario("HEALTH");
  }

  @Test
  void driftPausedGateBlocksDecision() {
    when(driftWatchdog.allowTrading()).thenReturn(false);
    runGateScenario("DRIFT_PAUSED");
  }

  @Test
  void driftShadowGateBlocksDecision() {
    tradingState.setMode(TradingState.Mode.SHADOW);
    runGateScenario("DRIFT_SHADOW");
  }

  @Test
  void allocatorGateBlocksDecision() {
    when(allocatorService.evaluate(SYMBOL)).thenReturn(AllocationDecision.blocked("ALLOCATOR"));
    runGateScenario("ALLOCATOR");
  }

  @Test
  void anomalyMultiplierZeroBlocksDecision() {
    when(anomalyDetector.sizingMultiplier(SYMBOL)).thenReturn(0.0);
    runGateScenario("SIZING_ZERO");
  }

  private void runGateScenario(String reasonFragment) {
    when(strategyService.decide(SYMBOL)).thenReturn(buyDecision(null));
    scheduler.onCandleClosed(SYMBOL, INTERVAL, CLOSE_TIME);

    verify(orderExecutionService, never())
        .execute(anyString(), anyString(), anyString(), any(), anyLong(), anyDouble());
    ArgumentCaptor<com.bottrading.model.entity.DecisionEntity> entityCaptor =
        ArgumentCaptor.forClass(com.bottrading.model.entity.DecisionEntity.class);
    verify(decisionRepository).save(entityCaptor.capture());
    assertThat(entityCaptor.getValue().isExecuted()).isFalse();
    assertThat(entityCaptor.getValue().getReason()).contains(reasonFragment);
  }

  private StrategyDecision buyDecision(BanditSelection selection) {
    SignalResult signal = new SignalResult(SignalSide.BUY, 0.9, "GO", List.of());
    StrategyContext context = StrategyContext.builder().symbol(SYMBOL).build();
    return new StrategyDecision(signal, context, null, "preset", selection);
  }

  private com.bottrading.execution.ExecutionEngine.ExecutionResult executionResult() {
    com.bottrading.model.dto.OrderResponse order =
        new com.bottrading.model.dto.OrderResponse(
            "order-1",
            "client-1",
            SYMBOL,
            OrderSide.BUY,
            OrderType.MARKET,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            "FILLED",
            Instant.now(clock));
    return new com.bottrading.execution.ExecutionEngine.ExecutionResult(
        new com.bottrading.execution.ExecutionPolicy.MarketPlan(),
        List.of(order),
        BigDecimal.ONE,
        BigDecimal.ONE);
  }
}
