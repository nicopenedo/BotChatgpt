package com.bottrading.executor;

import com.bottrading.config.TradingProps;
import com.bottrading.config.TradingProps.Mode;
import com.bottrading.chaos.ChaosSuite;
import com.bottrading.model.dto.Kline;
import com.bottrading.repository.DecisionRepository;
import com.bottrading.service.OrderExecutionService;
import com.bottrading.service.StrategyService;
import com.bottrading.service.anomaly.AnomalyDetector;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.health.HealthService;
import com.bottrading.bandit.BanditArmRole;
import com.bottrading.service.risk.RiskGuard;
import com.bottrading.service.risk.TradingState;
import com.bottrading.service.risk.drift.DriftWatchdog;
import com.bottrading.service.market.CandleSanitizer;
import com.bottrading.service.trading.AllocatorService;
import com.bottrading.service.trading.AllocatorService.AllocationDecision;
import com.bottrading.strategy.SignalResult;
import com.bottrading.strategy.SignalSide;
import com.bottrading.strategy.StrategyDecision;
import com.bottrading.service.preset.CanaryStageService;
import com.bottrading.throttle.Endpoint;
import com.bottrading.throttle.Throttle;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TradingScheduler {

  private static final Logger log = LoggerFactory.getLogger(TradingScheduler.class);

  private final TradingProps tradingProps;
  private final StrategyService strategyService;
  private final TradingState tradingState;
  private final RiskGuard riskGuard;
  private final BinanceClient binanceClient;
  private final OrderExecutionService orderExecutionService;
  private final DecisionRepository decisionRepository;
  private final MeterRegistry meterRegistry;
  private final WSKlineSubscriber klineSubscriber;
  private final AllocatorService allocatorService;
  private final DriftWatchdog driftWatchdog;
  private final HealthService healthService;
  private final Clock clock;
  private final Timer decisionTimer;
  private final Throttle throttle;
  private final ChaosSuite chaosSuite;
  private final CandleSanitizer candleSanitizer;
  private final AnomalyDetector anomalyDetector;
  private final CanaryStageService canaryStageService;

  private final Lock executionLock = new ReentrantLock();
  private final AtomicBoolean enabled = new AtomicBoolean(true);
  private final ConcurrentMap<String, AtomicLong> lastCloseTimes = new ConcurrentHashMap<>();
  private final AtomicReference<String> lastDecisionKey = new AtomicReference<>("NONE");
  private final Deque<Instant> orderTimes = new ConcurrentLinkedDeque<>();
  private final ConcurrentMap<String, AtomicLong> backlogGauge = new ConcurrentHashMap<>();

  public TradingScheduler(
      TradingProps tradingProps,
      StrategyService strategyService,
      TradingState tradingState,
      RiskGuard riskGuard,
      BinanceClient binanceClient,
      OrderExecutionService orderExecutionService,
      DecisionRepository decisionRepository,
      MeterRegistry meterRegistry,
      WSKlineSubscriber klineSubscriber,
      AllocatorService allocatorService,
      DriftWatchdog driftWatchdog,
      HealthService healthService,
      ObjectProvider<Clock> clockProvider,
      Throttle throttle,
      ChaosSuite chaosSuite,
      AnomalyDetector anomalyDetector,
      CandleSanitizer candleSanitizer,
      CanaryStageService canaryStageService) {
    this.tradingProps = tradingProps;
    this.strategyService = strategyService;
    this.tradingState = tradingState;
    this.riskGuard = riskGuard;
    this.binanceClient = binanceClient;
    this.orderExecutionService = orderExecutionService;
    this.decisionRepository = decisionRepository;
    this.meterRegistry = meterRegistry;
    this.klineSubscriber = klineSubscriber;
    this.allocatorService = allocatorService;
    this.driftWatchdog = driftWatchdog;
    this.healthService = healthService;
    this.clock = Objects.requireNonNullElse(clockProvider.getIfAvailable(), Clock.systemUTC());
    this.throttle = Objects.requireNonNull(throttle, "throttle");
    this.chaosSuite = Objects.requireNonNull(chaosSuite, "chaosSuite");
    this.candleSanitizer = Objects.requireNonNull(candleSanitizer, "candleSanitizer");
    this.anomalyDetector = Objects.requireNonNull(anomalyDetector, "anomalyDetector");
    this.canaryStageService = Objects.requireNonNull(canaryStageService, "canaryStageService");
    this.decisionTimer =
        Timer.builder("scheduler.candle.duration.ms")
            .publishPercentileHistogram()
            .register(meterRegistry);
    Gauge.builder("scheduler.candle.enabled", enabled, flag -> flag.get() ? 1.0 : 0.0)
        .tags(Tags.empty())
        .register(meterRegistry);
    registerBacklogGauge(tradingProps.getSymbol());
    for (String symbol : tradingProps.getSymbols()) {
      registerBacklogGauge(symbol);
    }
  }

  @PostConstruct
  void init() {
    for (String symbol : tradingProps.getSymbols()) {
      lastCloseTimes.putIfAbsent(symbol, new AtomicLong(-1));
    }
    if (tradingProps.getMode() == Mode.WEBSOCKET) {
      klineSubscriber.start(tradingProps.getSymbol(), tradingProps.getInterval(), this::onKline);
    } else {
      log.info("Trading scheduler configured for polling mode");
    }
  }

  public void enable() {
    enabled.set(true);
  }

  public void disable() {
    enabled.set(false);
  }

  public SchedulerStatus status() {
    Instant cooldownUntil = tradingState.getCooldownUntil();
    long secondsRemaining = Math.max(0, cooldownUntil.getEpochSecond() - Instant.now(clock).getEpochSecond());
    return new SchedulerStatus(
        tradingProps.getMode().name().toLowerCase(),
        lastDecisionKey.get(),
        enabled.get(),
        tradingProps.isLiveEnabled() && tradingState.isLiveEnabled(),
        secondsRemaining,
        lastCloseTimes
            .getOrDefault(tradingProps.getSymbol(), new AtomicLong(-1))
            .get(),
        tradingState.getMode().name().toLowerCase());
  }

  private void onKline(KlineEvent event) {
    if (!event.isFinal()) {
      return;
    }
    onCandleClosed(event.symbol(), event.interval(), event.closeTime());
  }

  public void onCandleClosed(String symbol, String interval, long closeTime) {
    List<Long> sanitized = candleSanitizer.sanitize(symbol, interval, closeTime);
    for (int i = 0; i < sanitized.size(); i++) {
      long ts = sanitized.get(i);
      String source = i == sanitized.size() - 1 ? "websocket" : "websocket-gap";
      processCandle(symbol, interval, ts, source);
    }
  }

  @Scheduled(fixedDelayString = "${trading.poll-delay-ms:1500}")
  public void pollCandles() {
    if (!enabled.get()) {
      return;
    }
    List<String> symbols = tradingProps.getSymbols();
    for (String symbol : symbols) {
      if (!shouldPoll(symbol)) {
        continue;
      }
      try {
        long baseDelayMs = Math.max(1000L, candleSanitizer.intervalMillis(tradingProps.getInterval()) / 4);
        if (!chaosSuite.allowRestPoll(symbol, baseDelayMs, Instant.now(clock))) {
          continue;
        }
        if (!throttle.canSchedule(Endpoint.KLINES, symbol)) {
          continue;
        }
        long start = System.nanoTime();
        List<Kline> klines = binanceClient.getKlines(symbol, tradingProps.getInterval(), 2);
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        healthService.onApiCall(latencyMs, klines != null);
        if (klines == null || klines.isEmpty()) {
          continue;
        }
        Kline last = klines.get(klines.size() - 1);
        long closeTime = last.closeTime().toEpochMilli();
        AtomicLong lastClose = lastCloseTimes.computeIfAbsent(symbol, s -> new AtomicLong(-1));
        if (closeTime <= lastClose.get()) {
          continue;
        }
        List<Long> sanitized = candleSanitizer.sanitize(symbol, tradingProps.getInterval(), closeTime);
        for (int i = 0; i < sanitized.size(); i++) {
          long ts = sanitized.get(i);
          String source = i == sanitized.size() - 1 ? "polling" : "polling-gap";
          processCandle(symbol, tradingProps.getInterval(), ts, source);
        }
        if (!sanitized.isEmpty()) {
          chaosSuite.onWebsocketState(false);
        }
      } catch (Exception ex) {
        log.warn("Polling klines failed for {}: {}", symbol, ex.getMessage());
      }
    }
  }

  private boolean shouldPoll(String symbol) {
    if (!enabled.get()) {
      return false;
    }
    if (tradingProps.getMode() == Mode.POLLING || chaosSuite.forceRestFallback()) {
      return true;
    }
    if (symbol.equalsIgnoreCase(tradingProps.getSymbol())) {
      return !klineSubscriber.isHealthy();
    }
    return true;
  }

  private void processCandle(String symbol, String interval, long closeTime, String source) {
    if (!enabled.get()) {
      log.debug("Scheduler disabled; skipping candle {}", closeTime);
      incrementDecisionMetric("SKIPPED", "DISABLED");
      return;
    }
    if (!executionLock.tryLock()) {
      log.debug("Execution lock busy; skipping candle {}", closeTime);
      incrementDecisionMetric("SKIPPED", "LOCKED");
      return;
    }
    try {
      applyJitter();
      Instant now = Instant.now(clock);
      long backlog = Math.max(0, now.toEpochMilli() - closeTime);
      registerBacklogGauge(symbol).set(backlog);
      String decisionKey = decisionKey(symbol, interval, closeTime);
      lastDecisionKey.set(decisionKey);
      if (decisionRepository.existsById(decisionKey)) {
        log.debug("Decision {} already processed", decisionKey);
        incrementDecisionMetric("SKIPPED", "DUPLICATE");
        return;
      }
      lastCloseTimes.computeIfAbsent(symbol, s -> new AtomicLong(-1)).set(closeTime);
      Timer.Sample sample = Timer.start(clock);
      try {
        StrategyDecision decision = strategyService.decide(symbol);
        DecisionContext context = new DecisionContext(decisionKey, symbol, interval, closeTime, now, source);
        handleDecision(context, decision);
      } finally {
        sample.stop(decisionTimer);
      }
    } finally {
      executionLock.unlock();
    }
  }

  private void handleDecision(DecisionContext context, StrategyDecision decision) {
    SignalResult signal = decision.signal();
    DecisionRecord record = new DecisionRecord(decision, context);
    if (signal.side() == SignalSide.FLAT) {
      record.reason(signal.note());
      record.executed(false);
      persistDecision(record);
      incrementDecisionMetric("FLAT", "SIGNAL");
      return;
    }

    GateResult gateResult = evaluateGates(context, decision);
    if (!gateResult.allowed()) {
      record.reason(signal.note() + " | " + gateResult.reason());
      record.executed(false);
      persistDecision(record);
      incrementDecisionMetric("SKIPPED", gateResult.reason());
      return;
    }

    Optional<com.bottrading.execution.ExecutionEngine.ExecutionResult> response =
        orderExecutionService.execute(
            context.decisionKey(),
            context.symbol(),
            context.interval(),
            decision,
            context.closeTime(),
            gateResult.sizingMultiplier());
    if (response.isPresent()) {
      record.executed(true);
      var orders = response.get().orders();
      if (!orders.isEmpty()) {
        record.orderId(orders.get(orders.size() - 1).orderId());
      }
      persistDecision(record);
      registerOrder(signal.side());
      incrementDecisionMetric(signal.side() == SignalSide.BUY ? "BUY" : "SELL", context.source());
    } else {
      record.reason(signal.note() + " | EXECUTION_FAILED");
      record.executed(false);
      persistDecision(record);
      incrementDecisionMetric("SKIPPED", "EXECUTION_FAILED");
    }
  }

  private void registerOrder(SignalSide side) {
    Instant now = Instant.now(clock);
    orderTimes.addLast(now);
    purgeOldOrders(now);
  }

  private void persistDecision(DecisionRecord record) {
    com.bottrading.model.entity.DecisionEntity entity = new com.bottrading.model.entity.DecisionEntity();
    entity.setDecisionKey(record.context().decisionKey());
    entity.setSymbol(record.context().symbol());
    entity.setInterval(record.context().interval());
    entity.setCloseTime(Instant.ofEpochMilli(record.context().closeTime()));
    entity.setDecidedAt(record.context().decidedAt());
    entity.setSide(record.decision().signal().side());
    entity.setConfidence(record.decision().signal().confidence());
    entity.setReason(record.reason());
    entity.setExecuted(record.executed());
    entity.setOrderId(record.orderId());
    entity.setRegimeTrend(
        record.decision().regime() != null ? record.decision().regime().trend().name() : null);
    entity.setRegimeVolatility(
        record.decision().regime() != null ? record.decision().regime().volatility().name() : null);
    entity.setPresetKey(record.decision().preset());
    entity.setPresetId(
        record.decision().banditSelection() != null
            ? record.decision().banditSelection().presetId()
            : null);
    decisionRepository.save(entity);
  }

  private void purgeOldOrders(Instant now) {
    Instant cutoff = now.minusSeconds(60);
    while (true) {
      Instant first = orderTimes.peekFirst();
      if (first == null || !first.isBefore(cutoff)) {
        break;
      }
      orderTimes.pollFirst();
    }
  }

  private GateResult evaluateGates(DecisionContext context, StrategyDecision decision) {
    if (!tradingProps.isLiveEnabled() || !tradingState.isLiveEnabled()) {
      return GateResult.blocked("LIVE_DISABLED", 0);
    }
    if (tradingState.isKillSwitchActive()) {
      return GateResult.blocked("KILL_SWITCH", 0);
    }
    if (!riskGuard.canOpen(context.symbol())) {
      return GateResult.blocked("RISK_GUARD", 0);
    }
    if (!withinTradingWindow(context.decidedAt())) {
      return GateResult.blocked("WINDOW", 0);
    }
    if (!hasSufficientVolume(context.symbol())) {
      return GateResult.blocked("VOLUME", 0);
    }
    if (!withinOrderRate(context.decidedAt())) {
      return GateResult.blocked("RATE_LIMIT", 0);
    }
    if (!healthService.isHealthy()) {
      return GateResult.blocked("HEALTH", 0);
    }
    if (!driftWatchdog.allowTrading() || tradingState.getMode() == TradingState.Mode.PAUSED) {
      return GateResult.blocked("DRIFT_PAUSED", 0);
    }
    if (tradingState.getMode() == TradingState.Mode.SHADOW) {
      return GateResult.blocked("DRIFT_SHADOW", 0);
    }
    AllocationDecision allocation = allocatorService.evaluate(context.symbol());
    if (!allocation.allowed()) {
      return GateResult.blocked(allocation.reason(), 0);
    }
    double finalMultiplier = allocation.sizingMultiplier() * driftWatchdog.sizingMultiplier();
    finalMultiplier *= anomalyDetector.sizingMultiplier(context.symbol());
    if (decision.banditSelection() != null
        && decision.banditSelection().role() == BanditArmRole.CANDIDATE) {
      double stageMultiplier =
          canaryStageService.multiplier(decision.banditSelection().presetId());
      finalMultiplier *= stageMultiplier;
    }
    if (finalMultiplier <= 0) {
      return GateResult.blocked("SIZING_ZERO", 0);
    }
    return GateResult.allowed(finalMultiplier);
  }

  private boolean withinOrderRate(Instant now) {
    purgeOldOrders(now);
    int placed = orderTimes.size();
    return placed < tradingProps.getMaxOrdersPerMinute();
  }

  private boolean hasSufficientVolume(String symbol) {
    try {
      return binanceClient
              .get24hQuoteVolume(symbol)
              .compareTo(tradingProps.getMinVolume24h())
          >= 0;
    } catch (Exception ex) {
      log.warn("Unable to fetch 24h volume: {}", ex.getMessage());
      return false;
    }
  }

  private boolean withinTradingWindow(Instant now) {
    LocalTime start = tradingProps.getTradingWindowStart();
    LocalTime end = tradingProps.getTradingWindowEnd();
    LocalTime current = LocalDateTime.ofInstant(now, ZoneId.systemDefault()).toLocalTime();
    if (start.equals(end)) {
      return true;
    }
    if (start.isBefore(end)) {
      return !current.isBefore(start) && !current.isAfter(end);
    }
    return !current.isBefore(start) || !current.isAfter(end);
  }

  private void applyJitter() {
    int jitter = tradingProps.getJitterSeconds();
    if (jitter <= 0) {
      return;
    }
    int offset = ThreadLocalRandom.current().nextInt(-jitter, jitter + 1);
    if (offset > 0) {
      try {
        TimeUnit.SECONDS.sleep(offset);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void incrementDecisionMetric(String result, String reason) {
    meterRegistry.counter("scheduler.candle.decisions", Tags.of("result", result, "reason", reason)).increment();
  }

  private AtomicLong registerBacklogGauge(String symbol) {
    return backlogGauge.computeIfAbsent(
        symbol,
        key -> {
          AtomicLong value = new AtomicLong(0L);
          Gauge.builder("scheduler.candle.backlog.ms", value, AtomicLong::get)
              .tags("symbol", key)
              .register(meterRegistry);
          return value;
        });
  }

  private String decisionKey(String symbol, String interval, long closeTime) {
    return symbol + "|" + interval + "|" + closeTime;
  }

  private record DecisionContext(
      String decisionKey, String symbol, String interval, long closeTime, Instant decidedAt, String source) {}

  private static final class DecisionRecord {
    private final StrategyDecision decision;
    private final DecisionContext context;
    private String reason = "";
    private boolean executed;
    private String orderId;

    private DecisionRecord(StrategyDecision decision, DecisionContext context) {
      this.decision = decision;
      this.context = context;
    }

    public DecisionRecord reason(String reason) {
      this.reason = (reason != null ? reason : "");
      return this;
    }

    public DecisionRecord executed(boolean executed) {
      this.executed = executed;
      return this;
    }

    public DecisionRecord orderId(String orderId) {
      this.orderId = orderId;
      return this;
    }

    public StrategyDecision decision() {
      return decision;
    }

    public DecisionContext context() {
      return context;
    }

    public String reason() {
      return reason;
    }

    public boolean executed() {
      return executed;
    }

    public String orderId() {
      return orderId;
    }
  }

  private record GateResult(boolean allowed, String reason, double sizingMultiplier) {
    static GateResult allowed(double sizingMultiplier) {
      return new GateResult(true, "OK", sizingMultiplier);
    }

    static GateResult blocked(String reason, double sizingMultiplier) {
      return new GateResult(false, reason, sizingMultiplier);
    }
  }

  public record SchedulerStatus(
      String mode,
      String lastDecisionKey,
      boolean enabled,
      boolean liveEnabled,
      long cooldownSeconds,
      long lastCloseTime,
      String tradingMode) {}
}
