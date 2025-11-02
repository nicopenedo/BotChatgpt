package com.bottrading.service.risk;

import com.bottrading.config.VarProperties;
import com.bottrading.saas.service.TenantMetrics;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.entity.RiskVarSnapshotEntity;
import com.bottrading.model.entity.ShadowPositionEntity;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PositionStatus;
import com.bottrading.repository.PositionRepository;
import com.bottrading.repository.RiskVarSnapshotRepository;
import com.bottrading.repository.ShadowPositionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class IntradayVarService {

  private static final Logger log = LoggerFactory.getLogger(IntradayVarService.class);
  private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

  private final VarProperties properties;
  private final RiskVarSnapshotRepository snapshotRepository;
  private final PositionRepository positionRepository;
  private final ShadowPositionRepository shadowPositionRepository;
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final MeterRegistry meterRegistry;
  private final ObjectMapper objectMapper;
  private final ConcurrentMap<MetricKey, AtomicReference<Double>> cvarGauges =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<MetricKey, DistributionSummary> ratioSummaries =
      new ConcurrentHashMap<>();
  private final AtomicReference<BigDecimal> lastEquity = new AtomicReference<>(BigDecimal.ZERO);
  private final TenantMetrics tenantMetrics;

  public IntradayVarService(
      VarProperties properties,
      RiskVarSnapshotRepository snapshotRepository,
      PositionRepository positionRepository,
      ShadowPositionRepository shadowPositionRepository,
      NamedParameterJdbcTemplate jdbcTemplate,
      MeterRegistry meterRegistry,
      TenantMetrics tenantMetrics) {
    this.properties = properties;
    this.snapshotRepository = snapshotRepository;
    this.positionRepository = positionRepository;
    this.shadowPositionRepository = shadowPositionRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.meterRegistry = meterRegistry;
    this.objectMapper = new ObjectMapper();
    this.tenantMetrics = tenantMetrics;
  }

  public boolean isEnabled() {
    return properties.isEnabled();
  }

  public VarAssessment assess(VarInput input) {
    if (!isEnabled()) {
      return VarAssessment.disabled(input.quantity());
    }
    updateEquity(input.equity());
    if (input.stopLoss() == null
        || input.entryPrice() == null
        || input.quantity() == null
        || input.entryPrice().compareTo(BigDecimal.ZERO) <= 0
        || input.quantity().compareTo(BigDecimal.ZERO) <= 0) {
      return VarAssessment.disabled(input.quantity());
    }

    double stopDistance = stopDistance(input.side(), input.entryPrice(), input.stopLoss());
    if (stopDistance <= 0) {
      return VarAssessment.disabled(input.quantity());
    }

    SampleUniverse universe = loadSamples(input.symbol(), input.presetKey(), input.regimeTrend(), input.regimeVolatility());
    if (universe.samples().isEmpty()) {
      List<VarReason> reasons = List.of(VarReason.of("NO_DATA", "No historical samples"));
      return VarAssessment.from(
          input.quantity(),
          input.quantity(),
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          reasons,
          false,
          BigDecimal.ZERO,
          0,
          SampleUniverse.empty());
    }

    Stats stats = computeStats(universe, input.entryPrice().doubleValue(), stopDistance);
    if (!stats.valid()) {
      List<VarReason> reasons = new ArrayList<>(universe.reasons());
      reasons.add(VarReason.of("DEGENERATE", "Unable to compute distribution"));
      return VarAssessment.from(
          input.quantity(),
          input.quantity(),
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          reasons,
          false,
          BigDecimal.ZERO,
          universe.samples().size(),
          universe);
    }

    double cvarR = stats.cvarR();
    double varR = stats.varR();
    BigDecimal baseQty = input.quantity();
    BigDecimal adjustedQty = baseQty;
    List<VarReason> reasons = new ArrayList<>(universe.reasons());

    double tradeLimit = properties.getCvarTargetPctPerTrade() / 100.0 * input.equity().doubleValue();
    double dailyLimit = properties.getCvarTargetPctPerDay() / 100.0 * input.equity().doubleValue();

    double riskPerUnit = stopDistance;
    double denominator = cvarR * riskPerUnit;
    if (denominator > 0) {
      double qtyLimit = tradeLimit / denominator;
      BigDecimal limit = BigDecimal.valueOf(qtyLimit);
      if (limit.compareTo(adjustedQty) < 0) {
        adjustedQty = limit;
        reasons.add(VarReason.of("TRADE_LIMIT", String.format("qty<=%.8f", qtyLimit)));
      }
    }

    adjustedQty = applyStepSize(adjustedQty, input.stepSize());
    if (adjustedQty.compareTo(BigDecimal.ZERO) <= 0) {
      reasons.add(VarReason.of("STEP_ZERO", "Quantity below step size"));
      meterRegistry.counter("blocks.by_var", Tags.of("symbol", input.symbol(), "reason", "STEP_ZERO"))
          .increment();
      return VarAssessment.blocked(baseQty, adjustedQty, varR, cvarR, reasons, universe);
    }

    double tradeExposure = cvarR * riskPerUnit * adjustedQty.doubleValue();
    double currentExposure = aggregateExposure();
    boolean blocked = false;
    if (dailyLimit > 0 && currentExposure + tradeExposure > dailyLimit) {
      reasons.add(
          VarReason.of(
              "DAILY_LIMIT",
              String.format(
                  "%.2f>%.2f", currentExposure + tradeExposure, dailyLimit)));
      blocked = true;
      meterRegistry
          .counter("blocks.by_var", Tags.of("symbol", input.symbol(), "reason", "DAILY_LIMIT"))
          .increment();
    }

    BigDecimal varMoney = BigDecimal.valueOf(varR * riskPerUnit * adjustedQty.doubleValue());
    BigDecimal cvarMoney = BigDecimal.valueOf(tradeExposure);
    double ratio =
        baseQty.compareTo(BigDecimal.ZERO) > 0
            ? adjustedQty.divide(baseQty, MC).doubleValue()
            : 1.0;

    registerMetrics(
        input.symbol(),
        input.regimeTrend(),
        input.presetKey(),
        cvarMoney.doubleValue(),
        ratio);

    persistSnapshot(
        input,
        varMoney,
        cvarMoney,
        ratio,
        reasons,
        adjustedQty,
        universe,
        blocked);

    return VarAssessment.from(
        baseQty,
        adjustedQty,
        varMoney,
        cvarMoney,
        reasons,
        blocked,
        BigDecimal.valueOf(currentExposure + tradeExposure),
        universe.samples().size(),
        universe);
  }

  public VarStatus status(String symbol) {
    if (!isEnabled()) {
      return VarStatus.disabled();
    }
    List<RiskVarSnapshotEntity> snapshots = snapshotRepository.findTop50BySymbolOrderByTimestampDesc(symbol);
    if (snapshots.isEmpty()) {
      return VarStatus.disabled();
    }
    RiskVarSnapshotEntity latest = snapshots.get(0);
    return new VarStatus(
        latest.getSymbol(),
        latest.getCvar(),
        latest.getVar(),
        latest.getQtyRatio(),
        latest.getTimestamp(),
        snapshots.stream().map(this::toStatusEntry).collect(Collectors.toList()));
  }

  public List<RiskVarSnapshotEntity> history(String symbol, Instant from) {
    return snapshotRepository.findSnapshots(symbol, from);
  }

  public ExposureSnapshot exposure(BigDecimal equity) {
    BigDecimal basis =
        equity != null && equity.compareTo(BigDecimal.ZERO) > 0 ? equity : lastEquity.get();
    if (!isEnabled() || basis.compareTo(BigDecimal.ZERO) <= 0) {
      return new ExposureSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
    double exposure = aggregateExposure();
    double limit = properties.getCvarTargetPctPerDay() / 100.0 * basis.doubleValue();
    double ratio = limit > 0 ? exposure / limit : 0;
    return new ExposureSnapshot(
        BigDecimal.valueOf(exposure), BigDecimal.valueOf(limit), BigDecimal.valueOf(ratio));
  }

  public void updateEquity(BigDecimal equity) {
    if (equity != null && equity.compareTo(BigDecimal.ZERO) > 0) {
      lastEquity.set(equity);
    }
  }

  private VarStatus.Entry toStatusEntry(RiskVarSnapshotEntity entity) {
    return new VarStatus.Entry(
        entity.getTimestamp(),
        entity.getCvar(),
        entity.getVar(),
        entity.getQtyRatio(),
        entity.getReasonsJson());
  }

  private void persistSnapshot(
      VarInput input,
      BigDecimal varMoney,
      BigDecimal cvarMoney,
      double ratio,
      List<VarReason> reasons,
      BigDecimal adjustedQty,
      SampleUniverse universe,
      boolean blocked) {
    try {
      RiskVarSnapshotEntity entity = new RiskVarSnapshotEntity();
      entity.setSymbol(input.symbol());
      entity.setPresetKey(input.presetKey());
      entity.setPresetId(input.presetId());
      entity.setRegimeTrend(input.regimeTrend());
      entity.setRegimeVolatility(input.regimeVolatility());
      entity.setRegime(composeRegime(input.regimeTrend(), input.regimeVolatility()));
      entity.setTimestamp(Instant.now());
      entity.setVar(varMoney);
      entity.setCvar(cvarMoney);
      entity.setQtyRatio(BigDecimal.valueOf(ratio));
      entity.setReasonsJson(objectMapper.writeValueAsString(reasons));
      snapshotRepository.save(entity);
    } catch (JsonProcessingException ex) {
      log.warn("Failed to persist VAR snapshot JSON: {}", ex.getMessage());
    } catch (Exception ex) {
      log.warn("Failed to persist VAR snapshot: {}", ex.getMessage());
    }
  }

  private void registerMetrics(
      String symbol, String regimeTrend, String presetKey, double cvar, double ratio) {
    MetricKey key = new MetricKey(symbol, regimeTrend, presetKey);
    AtomicReference<Double> ref =
        cvarGauges.computeIfAbsent(
            key,
            k ->
                meterRegistry.gauge(
                    "var.cvar_q",
                    Tags.concat(
                        tenantMetrics.tags(k.symbol()),
                        Tags.of(
                            "regime", Optional.ofNullable(k.regimeTrend()).orElse("UNKNOWN"),
                            "preset", Optional.ofNullable(k.presetKey()).orElse("default"))),
                    new AtomicReference<>(0.0)));
    ref.set(cvar);
    ratioSummaries
        .computeIfAbsent(
            key,
            k ->
                meterRegistry.summary(
                    "sizing.qty_var_ratio",
                    Tags.concat(
                        tenantMetrics.tags(k.symbol()),
                        Tags.of(
                            "regime", Optional.ofNullable(k.regimeTrend()).orElse("UNKNOWN"),
                            "preset", Optional.ofNullable(k.presetKey()).orElse("default")))))
        .record(ratio);
  }

  private double aggregateExposure() {
    List<PositionEntity> open = new ArrayList<>();
    open.addAll(positionRepository.findByStatus(PositionStatus.OPEN));
    open.addAll(positionRepository.findByStatus(PositionStatus.OPENING));
    open.addAll(positionRepository.findByStatus(PositionStatus.CLOSING));
    if (open.isEmpty()) {
      return 0;
    }
    double exposure = 0;
    for (PositionEntity position : open) {
      if (position.getStopLoss() == null
          || position.getEntryPrice() == null
          || position.getQtyRemaining() == null
          || position.getQtyRemaining().compareTo(BigDecimal.ZERO) <= 0) {
        continue;
      }
      double stopDistance =
          stopDistance(position.getSide(), position.getEntryPrice(), position.getStopLoss());
      if (stopDistance <= 0) {
        continue;
      }
      SampleUniverse positionUniverse =
          loadSamples(
              position.getSymbol(),
              position.getPresetKey(),
              position.getRegimeTrend(),
              position.getRegimeVolatility());
      Stats stats = computeStats(positionUniverse, position.getEntryPrice().doubleValue(), stopDistance);
      if (!stats.valid() || stats.cvarR() <= 0) {
        continue;
      }
      exposure += stats.cvarR() * stopDistance * position.getQtyRemaining().doubleValue();
    }
    return exposure;
  }

  SampleUniverse loadSamples(
      String symbol, String presetKey, String regimeTrend, String regimeVolatility) {
    List<VarReason> reasons = new ArrayList<>();
    List<HistoricalSample> samples = new ArrayList<>();
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("symbol", symbol)
            .addValue("limit", properties.getLookbackTrades())
            .addValue("presetKey", presetKey)
            .addValue("regimeTrend", regimeTrend)
            .addValue("regimeVolatility", regimeVolatility);
    List<HistoricalSample> base =
        jdbcTemplate.query(
            "SELECT executed_at, pnl_r, pnl, slippage_bps, entry_price, stop_loss, side, "
                + "position_regime_trend, position_regime_volatility, position_preset_key, preset_key "
                + "FROM vw_trades_enriched "
                + "WHERE symbol = :symbol "
                + "AND (:presetKey IS NULL OR preset_key = :presetKey OR position_preset_key = :presetKey) "
                + "ORDER BY executed_at DESC LIMIT :limit",
            params,
            this::mapSample);
    samples.addAll(base);
    if (samples.size() < properties.getMinTradesForSymbolPreset() && properties.isFallbackToRegimePool()) {
      reasons.add(VarReason.of("FALLBACK_REGIME", "Using regime pool"));
      List<HistoricalSample> regimeSamples =
          jdbcTemplate.query(
              "SELECT executed_at, pnl_r, pnl, slippage_bps, entry_price, stop_loss, side, "
                  + "position_regime_trend, position_regime_volatility, position_preset_key, preset_key "
                  + "FROM vw_trades_enriched "
                  + "WHERE (:regimeTrend IS NULL OR regime_trend = :regimeTrend) "
                  + "AND (:regimeVolatility IS NULL OR regime_volatility = :regimeVolatility) "
                  + "ORDER BY executed_at DESC LIMIT :limit",
              params,
              this::mapSample);
      samples.addAll(regimeSamples);
    }
    if (samples.isEmpty()) {
      reasons.add(VarReason.of("FALLBACK_GLOBAL", "Using global pool"));
      samples.addAll(
          jdbcTemplate.query(
              "SELECT executed_at, pnl_r, pnl, slippage_bps, entry_price, stop_loss, side, "
                  + "position_regime_trend, position_regime_volatility, position_preset_key, preset_key "
                  + "FROM vw_trades_enriched ORDER BY executed_at DESC LIMIT :limit",
              params,
              this::mapSample));
    }
    List<HistoricalSample> shadow = loadShadowSamples(symbol, presetKey, regimeTrend, regimeVolatility);
    samples.addAll(shadow);
    samples.removeIf(Objects::isNull);
    return new SampleUniverse(samples, reasons);
  }

  private List<HistoricalSample> loadShadowSamples(
      String symbol, String presetKey, String regimeTrend, String regimeVolatility) {
    List<ShadowPositionEntity> closed =
        shadowPositionRepository.findBySymbolOrderByOpenedAtDesc(symbol).stream()
            .filter(p -> p.getStatus() == PositionStatus.CLOSED)
            .limit(properties.getLookbackTrades())
            .collect(Collectors.toList());
    if (closed.isEmpty()) {
      return List.of();
    }
    List<HistoricalSample> samples = new ArrayList<>();
    for (ShadowPositionEntity position : closed) {
      if (presetKey != null
          && position.getPresetKey() != null
          && !presetKey.equalsIgnoreCase(position.getPresetKey())) {
        continue;
      }
      if (regimeTrend != null
          && position.getRegimeTrend() != null
          && !regimeTrend.equalsIgnoreCase(position.getRegimeTrend())) {
        continue;
      }
      if (regimeVolatility != null
          && position.getRegimeVolatility() != null
          && !regimeVolatility.equalsIgnoreCase(position.getRegimeVolatility())) {
        continue;
      }
      if (position.getRealizedPnl() == null
          || position.getStopLoss() == null
          || position.getEntryPrice() == null
          || position.getQuantity() == null
          || position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
        continue;
      }
      double stopDistance =
          stopDistance(position.getSide(), position.getEntryPrice(), position.getStopLoss());
      if (stopDistance <= 0) {
        continue;
      }
      double risk = stopDistance * position.getQuantity().doubleValue();
      if (risk <= 0) {
        continue;
      }
      double pnlR = position.getRealizedPnl().doubleValue() / risk;
      samples.add(
          new HistoricalSample(
              pnlR,
              0,
              position.getRegimeTrend(),
              position.getRegimeVolatility(),
              position.getPresetKey(),
              position.getPresetId()));
    }
    return samples;
  }

  private HistoricalSample mapSample(ResultSet rs, int rowNum) throws SQLException {
    Double pnlR = optionalDouble(rs, "pnl_r");
    Double pnl = optionalDouble(rs, "pnl");
    Double entry = optionalDouble(rs, "entry_price");
    Double stop = optionalDouble(rs, "stop_loss");
    String sideStr = rs.getString("side");
    double slippage = optionalDouble(rs, "slippage_bps");
    if ((pnlR == null || Double.isNaN(pnlR))
        && pnl != null
        && entry != null
        && stop != null
        && sideStr != null) {
      double distance = stopDistance(OrderSide.valueOf(sideStr), entry, stop);
      if (distance > 0) {
        pnlR = pnl / distance;
      }
    }
    if (pnlR == null || Double.isNaN(pnlR)) {
      return null;
    }
    return new HistoricalSample(
        pnlR,
        Double.isNaN(slippage) ? 0 : slippage,
        rs.getString("position_regime_trend") != null
            ? rs.getString("position_regime_trend")
            : rs.getString("regime_trend"),
        rs.getString("position_regime_volatility") != null
            ? rs.getString("position_regime_volatility")
            : rs.getString("regime_volatility"),
        rs.getString("position_preset_key") != null
            ? rs.getString("position_preset_key")
            : rs.getString("preset_key"),
        null);
  }

  private double optionalDouble(ResultSet rs, String column) throws SQLException {
    double value = rs.getDouble(column);
    return rs.wasNull() ? Double.NaN : value;
  }

  private Stats computeStats(SampleUniverse universe, double entryPrice, double stopDistance) {
    if (universe.samples().isEmpty() || stopDistance <= 0) {
      return Stats.invalid();
    }
    List<Double> normalized = new ArrayList<>(universe.samples().size());
    for (HistoricalSample sample : universe.samples()) {
      double slippageR =
          sample.slippageBps() / 10000.0 * (entryPrice / stopDistance);
      normalized.add(sample.pnlR() - slippageR);
    }
    normalized.removeIf(v -> Double.isNaN(v) || Double.isInfinite(v));
    if (normalized.size() < 5) {
      return Stats.invalid();
    }
    List<Double> simulated = simulate(normalized, properties.getMcIterations());
    if (simulated.isEmpty()) {
      return Stats.invalid();
    }
    Collections.sort(simulated);
    double alpha = 1.0 - properties.getQuantile();
    int index = (int) Math.floor(alpha * simulated.size());
    index = Math.min(Math.max(index, 0), simulated.size() - 1);
    double quantile = simulated.get(index);
    double cfQuantile = properties.isHeavyTails() ? cornishFisher(normalized, alpha) : quantile;
    double chosen = Math.min(quantile, cfQuantile);
    double varR = Math.max(0, -chosen);
    double cvarR = conditionalMean(simulated, chosen);
    if (Double.isNaN(cvarR) || cvarR <= 0) {
      return Stats.invalid();
    }
    return Stats.valid(varR, cvarR);
  }

  private double conditionalMean(List<Double> sorted, double threshold) {
    double sum = 0;
    int count = 0;
    for (double value : sorted) {
      if (value <= threshold) {
        sum += -value;
        count++;
      } else {
        break;
      }
    }
    if (count == 0) {
      return Double.NaN;
    }
    return sum / count;
  }

  private List<Double> simulate(List<Double> samples, int iterations) {
    if (samples.isEmpty()) {
      return List.of();
    }
    ThreadLocalRandom random = ThreadLocalRandom.current();
    List<Double> simulated = new ArrayList<>(iterations);
    for (int i = 0; i < iterations; i++) {
      double base = samples.get(random.nextInt(samples.size()));
      if (properties.isHeavyTails()) {
        base += heavyTailShock(samples, random);
      }
      simulated.add(base);
    }
    return simulated;
  }

  private double heavyTailShock(List<Double> samples, ThreadLocalRandom random) {
    DoubleSummaryStatistics stats = samples.stream().mapToDouble(Double::doubleValue).summaryStatistics();
    double mean = stats.getAverage();
    double variance = samples.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum() / samples.size();
    if (variance <= 0) {
      return 0;
    }
    double std = Math.sqrt(variance);
    double t = studentT(random);
    return (t * std) / Math.sqrt(samples.size());
  }

  private double studentT(ThreadLocalRandom random) {
    double u = random.nextGaussian();
    double v = random.nextGaussian();
    double w = random.nextGaussian();
    double chi2 = u * u + v * v + w * w;
    double df = 5.0;
    return u / Math.sqrt(chi2 / df);
  }

  private double cornishFisher(List<Double> samples, double alpha) {
    double mean = samples.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    double variance = samples.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum() / samples.size();
    if (variance <= 0) {
      return mean;
    }
    double std = Math.sqrt(variance);
    double skew = moment(samples, mean, std, 3);
    double kurtosis = moment(samples, mean, std, 4) - 3.0;
    double z = inverseNormal(alpha);
    double z2 = z * z;
    double z3 = z2 * z;
    double cf =
        z
            + (skew / 6.0) * (z2 - 1)
            + (kurtosis / 24.0) * (z3 - 3 * z)
            - (Math.pow(skew, 2) / 36.0) * (2 * z3 - 5 * z);
    return mean + cf * std;
  }

  private double moment(List<Double> samples, double mean, double std, int order) {
    double sum = 0;
    for (double v : samples) {
      sum += Math.pow((v - mean) / std, order);
    }
    return sum / samples.size();
  }

  private double inverseNormal(double p) {
    if (p <= 0 || p >= 1) {
      return 0;
    }
    return Math.sqrt(2) * erfinv(2 * p - 1);
  }

  private double erfinv(double x) {
    double a = 0.147;
    double ln = Math.log(1 - x * x);
    double first = 2 / (Math.PI * a) + ln / 2.0;
    double second = ln / a;
    double sign = x < 0 ? -1 : 1;
    return sign * Math.sqrt(Math.sqrt(first * first - second) - first);
  }

  private BigDecimal applyStepSize(BigDecimal quantity, BigDecimal stepSize) {
    if (stepSize == null || stepSize.compareTo(BigDecimal.ZERO) <= 0) {
      return quantity;
    }
    BigDecimal remainder = quantity.remainder(stepSize);
    return quantity.subtract(remainder).max(BigDecimal.ZERO);
  }

  private double stopDistance(OrderSide side, BigDecimal entry, BigDecimal stop) {
    return stopDistance(side, entry.doubleValue(), stop.doubleValue());
  }

  private double stopDistance(OrderSide side, double entry, double stop) {
    if (side == OrderSide.BUY) {
      return entry - stop;
    }
    return stop - entry;
  }

  private String composeRegime(String trend, String volatility) {
    if (trend == null && volatility == null) {
      return null;
    }
    return String.format("%s|%s", Optional.ofNullable(trend).orElse("?"), Optional.ofNullable(volatility).orElse("?"));
  }

  public record VarInput(
      String symbol,
      String presetKey,
      UUID presetId,
      String regimeTrend,
      String regimeVolatility,
      OrderSide side,
      BigDecimal entryPrice,
      BigDecimal stopLoss,
      BigDecimal quantity,
      BigDecimal equity,
      BigDecimal stepSize) {}

  public record VarAssessment(
      BigDecimal baseQuantity,
      BigDecimal adjustedQuantity,
      BigDecimal var,
      BigDecimal cvar,
      List<VarReason> reasons,
      boolean blocked,
      BigDecimal projectedExposure,
      int samples,
      SampleUniverse universe) {

    static VarAssessment from(
        BigDecimal base,
        BigDecimal adjusted,
        BigDecimal var,
        BigDecimal cvar,
        List<VarReason> reasons,
        boolean blocked,
        BigDecimal exposure,
        int samples,
        SampleUniverse universe) {
      return new VarAssessment(base, adjusted, var, cvar, reasons, blocked, exposure, samples, universe);
    }

    static VarAssessment blocked(
        BigDecimal base,
        BigDecimal adjusted,
        double varR,
        double cvarR,
        List<VarReason> reasons,
        SampleUniverse universe) {
      return new VarAssessment(
          base,
          adjusted,
          BigDecimal.valueOf(varR),
          BigDecimal.valueOf(cvarR),
          reasons,
          true,
          BigDecimal.ZERO,
          universe.samples().size(),
          universe);
    }

    static VarAssessment disabled(BigDecimal quantity) {
      return new VarAssessment(
          quantity,
          quantity,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          List.of(),
          false,
          BigDecimal.ZERO,
          0,
          SampleUniverse.empty());
    }
  }

  public record VarReason(String code, String detail) {
    public static VarReason of(String code, String detail) {
      return new VarReason(code, detail);
    }
  }

  public record VarStatus(
      String symbol,
      BigDecimal cvar,
      BigDecimal var,
      BigDecimal qtyRatio,
      Instant timestamp,
      List<Entry> history) {

    public static VarStatus disabled() {
      return new VarStatus(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, null, List.of());
    }

    public record Entry(Instant timestamp, BigDecimal cvar, BigDecimal var, BigDecimal qtyRatio, String reasonsJson) {}
  }

  public record SampleUniverse(List<HistoricalSample> samples, List<VarReason> reasons) {
    static SampleUniverse empty() {
      return new SampleUniverse(List.of(), List.of());
    }
  }

  public record HistoricalSample(
      double pnlR,
      double slippageBps,
      String regimeTrend,
      String regimeVolatility,
      String presetKey,
      UUID presetId) {}

  private record MetricKey(String symbol, String regimeTrend, String presetKey) {}

  public record ExposureSnapshot(BigDecimal exposure, BigDecimal limit, BigDecimal ratio) {}

  private record Stats(boolean valid, double varR, double cvarR) {
    static Stats invalid() {
      return new Stats(false, 0, 0);
    }

    static Stats valid(double varR, double cvarR) {
      return new Stats(true, varR, cvarR);
    }

    boolean valid() {
      return valid;
    }

    double varR() {
      return varR;
    }

    double cvarR() {
      return cvarR;
    }
  }
}
