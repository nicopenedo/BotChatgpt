package com.bottrading.service.trading;

// FIX: Ensure AllocationDecision exposes public accessors for downstream usage.

import com.bottrading.config.TradingProps;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.enums.PositionStatus;
import com.bottrading.repository.PositionRepository;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.risk.IntradayVarService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AllocatorService {

  private static final Logger log = LoggerFactory.getLogger(AllocatorService.class);
  private static final String CORR_INTERVAL = "1d";
  private static final Duration CORR_CACHE_TTL = Duration.ofHours(6);

  private final TradingProps tradingProps;
  private final PositionRepository positionRepository;
  private final BinanceClient binanceClient;
  private final MeterRegistry meterRegistry;
  private final IntradayVarService intradayVarService;
  private final ConcurrentMap<String, CachedCorrelation> correlationCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, AllocationStatus> lastStatus = new ConcurrentHashMap<>();

  public AllocatorService(
          TradingProps tradingProps,
          PositionRepository positionRepository,
          BinanceClient binanceClient,
          MeterRegistry meterRegistry,
          IntradayVarService intradayVarService) {
    this.tradingProps = tradingProps;
    this.positionRepository = positionRepository;
    this.binanceClient = binanceClient;
    this.meterRegistry = meterRegistry;
    this.intradayVarService = intradayVarService;
  }

  public AllocationDecision evaluate(String symbol) {
    if (!tradingProps.getAllocator().isEnabled()) {
      AllocationDecision decision = AllocationDecision.ok();
      lastStatus.put(symbol, new AllocationStatus(symbol, decision.allowed(), decision.reason(), 1.0));
      return decision;
    }
    AllocationDecision decision = doEvaluate(symbol);
    lastStatus.put(symbol, new AllocationStatus(symbol, decision.allowed(), decision.reason(), decision.sizingMultiplier()));
    String metricName = decision.allowed() ? "allocator.opens.allowed" : "allocator.opens.blocked";
    meterRegistry.counter(metricName, Tags.of("symbol", symbol, "reason", decision.reason())).increment();
    return decision;
  }

  public AllocationStatus status(String symbol) {
    return lastStatus.getOrDefault(symbol, new AllocationStatus(symbol, true, "INIT", 1.0));
  }

  private AllocationDecision doEvaluate(String symbol) {
    TradingProps.AllocatorProperties props = tradingProps.getAllocator();
    List<PositionEntity> open = openPositions();
    int active = open.size();
    double riskPerTrade = tradingProps.getRiskPerTradePct().doubleValue();
    double portfolioRisk = (active + 1) * riskPerTrade;
    if (props.getMaxSimultaneous() > 0 && active >= props.getMaxSimultaneous()) {
      return AllocationDecision.blocked("MAX_SIMULTANEOUS");
    }
    if (props.getPortfolioMaxTotalRiskPct() != null
            && props.getPortfolioMaxTotalRiskPct().doubleValue() > 0
            && portfolioRisk > props.getPortfolioMaxTotalRiskPct().doubleValue()) {
      return AllocationDecision.blocked("PORTFOLIO_RISK");
    }

    long symbolPositions =
            open.stream().filter(p -> symbol.equalsIgnoreCase(p.getSymbol())).count();
    double symbolRisk = (symbolPositions + 1) * riskPerTrade;
    if (props.getPerSymbolMaxRiskPct() != null
            && props.getPerSymbolMaxRiskPct().doubleValue() > 0
            && symbolRisk > props.getPerSymbolMaxRiskPct().doubleValue()) {
      return AllocationDecision.blocked("SYMBOL_RISK");
    }

    if (intradayVarService != null && intradayVarService.isEnabled()) {
      IntradayVarService.ExposureSnapshot exposure = intradayVarService.exposure(null);
      if (exposure.limit().compareTo(BigDecimal.ZERO) > 0
              && exposure.ratio().compareTo(BigDecimal.ONE) >= 0) {
        return AllocationDecision.blocked("VAR_BUDGET");
      }
    }

    Set<String> otherSymbols =
            open.stream()
                    .map(PositionEntity::getSymbol)
                    .filter(s -> !symbol.equalsIgnoreCase(s))
                    .collect(Collectors.toSet());

    for (String other : otherSymbols) {
      double corr = correlation(symbol, other);
      if (!Double.isNaN(corr) && corr >= props.getCorrMaxPairwise()) {
        return AllocationDecision.blocked("CORRELATION");
      }
    }

    return AllocationDecision.ok();
  }

  private List<PositionEntity> openPositions() {
    List<PositionEntity> open = new ArrayList<>();
    open.addAll(positionRepository.findByStatus(PositionStatus.OPEN));
    open.addAll(positionRepository.findByStatus(PositionStatus.OPENING));
    open.addAll(positionRepository.findByStatus(PositionStatus.CLOSING));
    return open;
  }

  private double correlation(String symbolA, String symbolB) {
    if (symbolA.equalsIgnoreCase(symbolB)) {
      return 1.0;
    }
    String key = cacheKey(symbolA, symbolB);
    CachedCorrelation cached = correlationCache.get(key);
    if (cached != null && !cached.expired()) {
      return cached.value();
    }
    try {
      List<com.bottrading.model.dto.Kline> seriesA =
              binanceClient.getKlines(symbolA, CORR_INTERVAL, tradingProps.getAllocator().getCorrLookbackDays() + 1);
      List<com.bottrading.model.dto.Kline> seriesB =
              binanceClient.getKlines(symbolB, CORR_INTERVAL, tradingProps.getAllocator().getCorrLookbackDays() + 1);
      double value = computeCorrelation(seriesA, seriesB);
      correlationCache.put(key, new CachedCorrelation(value, Instant.now()));
      return value;
    } catch (Exception ex) {
      log.debug("Unable to compute correlation between {} and {}: {}", symbolA, symbolB, ex.getMessage());
      return Double.NaN;
    }
  }

  private double computeCorrelation(
          List<com.bottrading.model.dto.Kline> seriesA, List<com.bottrading.model.dto.Kline> seriesB) {
    if (seriesA == null || seriesB == null || seriesA.size() < 3 || seriesB.size() < 3) {
      return Double.NaN;
    }
    int length = Math.min(seriesA.size(), seriesB.size());
    double[] returnsA = new double[length - 1];
    double[] returnsB = new double[length - 1];
    for (int i = 1; i < length; i++) {
      double prevA = seriesA.get(i - 1).close().doubleValue();
      double currA = seriesA.get(i).close().doubleValue();
      double prevB = seriesB.get(i - 1).close().doubleValue();
      double currB = seriesB.get(i).close().doubleValue();
      returnsA[i - 1] = safeLogReturn(currA, prevA);
      returnsB[i - 1] = safeLogReturn(currB, prevB);
    }
    return pearson(returnsA, returnsB);
  }

  private double safeLogReturn(double current, double previous) {
    if (previous <= 0 || current <= 0) {
      return 0;
    }
    return Math.log(current / previous);
  }

  private double pearson(double[] a, double[] b) {
    int n = Math.min(a.length, b.length);
    if (n == 0) {
      return Double.NaN;
    }
    double meanA = mean(a, n);
    double meanB = mean(b, n);
    double num = 0;
    double denA = 0;
    double denB = 0;
    for (int i = 0; i < n; i++) {
      double da = a[i] - meanA;
      double db = b[i] - meanB;
      num += da * db;
      denA += da * da;
      denB += db * db;
    }
    double denominator = Math.sqrt(denA) * Math.sqrt(denB);
    if (denominator == 0) {
      return Double.NaN;
    }
    return num / denominator;
  }

  private double mean(double[] values, int length) {
    double sum = 0;
    for (int i = 0; i < length; i++) {
      sum += values[i];
    }
    return sum / length;
  }

  private String cacheKey(String a, String b) {
    if (a.compareToIgnoreCase(b) <= 0) {
      return a.toUpperCase() + "|" + b.toUpperCase();
    }
    return b.toUpperCase() + "|" + a.toUpperCase();
  }

  private record CachedCorrelation(double value, Instant capturedAt) {
    boolean expired() {
      return capturedAt.plus(CORR_CACHE_TTL).isBefore(Instant.now());
    }
  }

  public record AllocationDecision(boolean allowed, String reason, double sizingMultiplier) {
    // Factory renombrado para no chocar con el accessor allowed()
    public static AllocationDecision ok() {
      return new AllocationDecision(true, "OK", 1.0);
    }

    public static AllocationDecision blocked(String reason) {
      return new AllocationDecision(false, reason, 0.0);
    }
    // Accessors (allowed(), reason(), sizingMultiplier()) los genera el record automáticamente.
  }

  public record AllocationStatus(String symbol, boolean allowed, String reason, double sizingMultiplier) {}
}
