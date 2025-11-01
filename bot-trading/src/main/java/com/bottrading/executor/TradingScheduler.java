package com.bottrading.executor;

import com.bottrading.config.TradingProps;
import com.bottrading.config.TradingProps.Mode;
import com.bottrading.model.dto.Kline;
import com.bottrading.model.dto.OrderResponse;
import com.bottrading.repository.DecisionRepository;
import com.bottrading.service.OrderExecutionService;
import com.bottrading.service.StrategyService;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.risk.RiskGuard;
import com.bottrading.service.risk.TradingState;
import com.bottrading.strategy.SignalResult;
import com.bottrading.strategy.SignalSide;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
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
  private final Clock clock;
  private final Timer decisionTimer;

  private final Lock executionLock = new ReentrantLock();
  private final AtomicBoolean enabled = new AtomicBoolean(true);
  private final AtomicLong lastCloseTime = new AtomicLong(-1);
  private final AtomicReference<String> lastDecisionKey = new AtomicReference<>("NONE");
  private final Deque<Instant> orderTimes = new ConcurrentLinkedDeque<>();

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
      ObjectProvider<Clock> clockProvider) {
    this.tradingProps = tradingProps;
    this.strategyService = strategyService;
    this.tradingState = tradingState;
    this.riskGuard = riskGuard;
    this.binanceClient = binanceClient;
    this.orderExecutionService = orderExecutionService;
    this.decisionRepository = decisionRepository;
    this.meterRegistry = meterRegistry;
    this.klineSubscriber = klineSubscriber;
    this.clock = Objects.requireNonNullElse(clockProvider.getIfAvailable(), Clock.systemUTC());
    this.decisionTimer =
        Timer.builder("scheduler.candle.duration.ms")
            .publishPercentileHistogram()
            .register(meterRegistry);
    meterRegistry.gauge("scheduler.candle.enabled", Tags.empty(), enabled, value -> value.get() ? 1.0 : 0.0);
  }

  @PostConstruct
  void init() {
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
        lastCloseTime.get());
  }

  private void onKline(KlineEvent event) {
    if (!event.isFinal()) {
      return;
    }
    onCandleClosed(event.symbol(), event.interval(), event.closeTime());
  }

  public void onCandleClosed(String symbol, String interval, long closeTime) {
    processCandle(symbol, interval, closeTime, "websocket");
  }

  @Scheduled(fixedDelayString = "${trading.poll-delay-ms:1500}")
  public void pollCandles() {
    if (!shouldPoll()) {
      return;
    }
    try {
      List<Kline> klines = binanceClient.getKlines(tradingProps.getSymbol(), tradingProps.getInterval(), 2);
      if (klines == null || klines.isEmpty()) {
        return;
      }
      Kline last = klines.get(klines.size() - 1);
      long closeTime = last.closeTime().toEpochMilli();
      if (closeTime <= lastCloseTime.get()) {
        return;
      }
      processCandle(tradingProps.getSymbol(), tradingProps.getInterval(), closeTime, "polling");
    } catch (Exception ex) {
      log.warn("Polling klines failed: {}", ex.getMessage());
    }
  }

  private boolean shouldPoll() {
    if (!enabled.get()) {
      return false;
    }
    if (tradingProps.getMode() == Mode.POLLING) {
      return true;
    }
    return !klineSubscriber.isHealthy();
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
      String decisionKey = decisionKey(symbol, interval, closeTime);
      lastDecisionKey.set(decisionKey);
      if (decisionRepository.existsById(decisionKey)) {
        log.debug("Decision {} already processed", decisionKey);
        incrementDecisionMetric("SKIPPED", "DUPLICATE");
        return;
      }
      lastCloseTime.set(closeTime);
      Timer.Sample sample = Timer.start(clock);
      try {
        SignalResult decision = strategyService.decide(symbol);
        DecisionContext context = new DecisionContext(decisionKey, symbol, interval, closeTime, now, source);
        handleDecision(context, decision);
      } finally {
        sample.stop(decisionTimer);
      }
    } finally {
      executionLock.unlock();
    }
  }

  private void handleDecision(DecisionContext context, SignalResult decision) {
    DecisionRecord record = new DecisionRecord(decision, context);
    if (decision.side() == SignalSide.FLAT) {
      record.reason(decision.note());
      record.executed(false);
      persistDecision(record);
      incrementDecisionMetric("FLAT", "SIGNAL");
      return;
    }

    GateResult gateResult = evaluateGates(context, decision);
    if (!gateResult.allowed()) {
      record.reason(decision.note() + " | " + gateResult.reason());
      record.executed(false);
      persistDecision(record);
      incrementDecisionMetric("SKIPPED", gateResult.reason());
      return;
    }

    Optional<OrderResponse> response =
        orderExecutionService.execute(context.decisionKey(), decision, context.closeTime());
    if (response.isPresent()) {
      record.executed(true);
      record.orderId(response.get().orderId());
      persistDecision(record);
      registerOrder(decision.side());
      incrementDecisionMetric(decision.side() == SignalSide.BUY ? "BUY" : "SELL", context.source());
    } else {
      record.reason(decision.note() + " | EXECUTION_FAILED");
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
    entity.setSide(record.decision().side());
    entity.setConfidence(record.decision().confidence());
    entity.setReason(record.reason());
    entity.setExecuted(record.executed());
    entity.setOrderId(record.orderId());
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

  private GateResult evaluateGates(DecisionContext context, SignalResult decision) {
    if (!tradingState.isLiveEnabled() || !tradingProps.isLiveEnabled()) {
      return GateResult.blocked("LIVE_DISABLED");
    }
    if (tradingState.isKillSwitchActive()) {
      return GateResult.blocked("KILL_SWITCH");
    }
    if (!riskGuard.canTrade()) {
      return GateResult.blocked("RISK_GUARD");
    }
    if (!withinTradingWindow(context.decidedAt())) {
      return GateResult.blocked("WINDOW");
    }
    if (!hasSufficientVolume()) {
      return GateResult.blocked("VOLUME");
    }
    if (!withinOrderRate(context.decidedAt())) {
      return GateResult.blocked("RATE_LIMIT");
    }
    return GateResult.allowed();
  }

  private boolean withinOrderRate(Instant now) {
    purgeOldOrders(now);
    int placed = orderTimes.size();
    return placed < tradingProps.getMaxOrdersPerMinute();
  }

  private boolean hasSufficientVolume() {
    try {
      return binanceClient
              .get24hQuoteVolume(tradingProps.getSymbol())
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

  private String decisionKey(String symbol, String interval, long closeTime) {
    return symbol + "|" + interval + "|" + closeTime;
  }

  private record DecisionContext(
      String decisionKey, String symbol, String interval, long closeTime, Instant decidedAt, String source) {}

  private record DecisionRecord(SignalResult decision, DecisionContext context) {
    private String reason = "";
    private boolean executed;
    private String orderId;

    public DecisionRecord reason(String reason) {
      this.reason = reason;
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

  private record GateResult(boolean allowed, String reason) {
    static GateResult allowed() {
      return new GateResult(true, "OK");
    }

    static GateResult blocked(String reason) {
      return new GateResult(false, reason);
    }
  }

  public record SchedulerStatus(
      String mode, String lastDecisionKey, boolean enabled, boolean liveEnabled, long cooldownSeconds, long lastCloseTime) {}
}
