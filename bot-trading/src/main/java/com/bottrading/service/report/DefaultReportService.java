package com.bottrading.service.report;

import com.bottrading.config.CacheConfig;
import com.bottrading.model.dto.report.AnnotationDto;
import com.bottrading.model.dto.report.HeatmapCell;
import com.bottrading.model.dto.report.HeatmapResponse;
import com.bottrading.model.dto.report.PnlAttributionGroup;
import com.bottrading.model.dto.report.PnlAttributionMetrics;
import com.bottrading.model.dto.report.PnlAttributionSymbolStats;
import com.bottrading.model.dto.report.SummaryBucket;
import com.bottrading.model.dto.report.TimePoint;
import com.bottrading.model.dto.report.TradeDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultReportService implements ReportService {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public DefaultReportService(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Page<TradeDto> findTrades(
      String symbol,
      Instant from,
      Instant to,
      String side,
      String status,
      Pageable pageable) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql =
        new StringBuilder("SELECT * FROM vw_trades_enriched WHERE 1=1");
    applyFilters(symbol, from, to, side, status, params, sql);

    long total = count("SELECT COUNT(*) FROM vw_trades_enriched WHERE 1=1", params, sql);

    if (pageable.getSort().isUnsorted()) {
      sql.append(" ORDER BY executed_at DESC");
    } else {
      sql.append(" ORDER BY ");
      sql.append(
          pageable.getSort().stream()
              .map(this::resolveSort)
              .collect(Collectors.joining(", ")));
    }
    sql.append(" LIMIT :limit OFFSET :offset");
    params.addValue("limit", pageable.getPageSize());
    params.addValue("offset", pageable.getOffset());

    List<TradeDto> trades = jdbcTemplate.query(sql.toString(), params, tradeRowMapper());
    return new PageImpl<>(trades, pageable, total);
  }

  @Override
  @Cacheable(cacheNames = CacheConfig.REPORT_CACHE, key = "#symbol + '|' + #groupBy + '|' + #from + '|' + #to")
  public List<SummaryBucket> summarize(String symbol, Instant from, Instant to, String groupBy) {
    String effectiveGroup = Optional.ofNullable(groupBy).orElse("day");
    String viewName = switch (effectiveGroup) {
      case "week" -> "vw_weekly_pnl";
      case "month" -> "vw_monthly_pnl";
      case "range" -> "vw_range_pnl";
      default -> "vw_daily_pnl";
    };
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql =
        new StringBuilder("SELECT * FROM " + viewName + " WHERE 1=1");
    applySummaryFilters(symbol, from, to, params, sql);
    sql.append(" ORDER BY period_start");
    return jdbcTemplate.query(sql.toString(), params, summaryRowMapper());
  }

  @Override
  @Cacheable(cacheNames = CacheConfig.REPORT_CACHE, key = "'equity|' + #symbol + '|' + #from + '|' + #to")
  public List<TimePoint> equityCurve(String symbol, Instant from, Instant to) {
    List<TradeDto> trades = loadTradesForRange(symbol, from, to);
    if (trades.isEmpty()) {
      return Collections.emptyList();
    }
    Map<Instant, BigDecimal> cumulative = new TreeMap<>();
    BigDecimal running = BigDecimal.ZERO;
    for (TradeDto trade : trades.stream().sorted((a, b) -> a.executedAt().compareTo(b.executedAt())).toList()) {
      running = running.add(Optional.ofNullable(trade.pnl()).orElse(BigDecimal.ZERO));
      cumulative.put(trade.executedAt(), running);
    }
    return cumulative.entrySet().stream()
        .map(entry -> new TimePoint(entry.getKey(), entry.getValue()))
        .toList();
  }

  @Override
  @Cacheable(cacheNames = CacheConfig.REPORT_CACHE, key = "'drawdown|' + #symbol + '|' + #from + '|' + #to")
  public List<TimePoint> drawdownCurve(String symbol, Instant from, Instant to) {
    List<TimePoint> equity = equityCurve(symbol, from, to);
    if (equity.isEmpty()) {
      return equity;
    }
    BigDecimal peak = BigDecimal.ZERO;
    List<TimePoint> dd = new ArrayList<>();
    for (TimePoint point : equity) {
      BigDecimal value = Optional.ofNullable(point.value()).orElse(BigDecimal.ZERO);
      peak = peak.max(value);
      BigDecimal drawdown =
          peak.compareTo(BigDecimal.ZERO) == 0
              ? BigDecimal.ZERO
              : value.subtract(peak).divide(peak, 8, RoundingMode.HALF_UP);
      dd.add(new TimePoint(point.ts(), drawdown));
    }
    return dd;
  }

  @Override
  public List<AnnotationDto> annotations(
      String symbol, Instant from, Instant to, boolean includeAdvanced) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql =
        new StringBuilder("SELECT * FROM vw_trade_annotations WHERE 1=1");
    applyFilters(symbol, from, to, null, null, params, sql);
    if (!includeAdvanced) {
      sql.append(" AND type IN ('BUY','SELL')");
    }
    sql.append(" ORDER BY executed_at");
    return jdbcTemplate.query(sql.toString(), params, annotationRowMapper());
  }

  @Override
  @Cacheable(cacheNames = CacheConfig.REPORT_CACHE, key = "'heatmap|' + #symbol + '|' + #from + '|' + #to + '|' + #bucket")
  public HeatmapResponse heatmap(String symbol, Instant from, Instant to, String bucket) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql =
        new StringBuilder("SELECT * FROM vw_trade_heatmap WHERE 1=1");
    applyFilters(symbol, from, to, null, null, params, sql);
    if (StringUtils.hasText(bucket) && bucket.equals("weekday")) {
      sql.append(" AND bucket = 'WEEKDAY'");
    } else {
      sql.append(" AND bucket = 'HOUR_WEEKDAY'");
    }
    List<HeatmapCell> cells = jdbcTemplate.query(sql.toString(), params, heatmapRowMapper());
    List<String> xLabels =
        new ArrayList<>(
            List.of(
                "00", "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12",
                "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23"));
    List<String> yLabels =
        new ArrayList<>(List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"));
    return new HeatmapResponse(xLabels, yLabels, cells);
  }

  @Override
  public List<PnlAttributionGroup> pnlAttributionBreakdown(String symbol, Instant from, Instant to) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql =
        new StringBuilder(
            "SELECT COALESCE(preset, 'N/A') AS preset, "
                + "COALESCE(regime, 'N/A') AS regime, "
                + "SUM(pnl_gross) AS pnl_gross, "
                + "SUM(signal_edge) AS signal_edge, "
                + "SUM(timing_cost) AS timing_cost, "
                + "SUM(slippage_cost) AS slippage_cost, "
                + "SUM(fees_cost) AS fees_cost, "
                + "SUM(pnl_net) AS pnl_net, "
                + "COUNT(*) AS trades "
                + "FROM pnl_attr WHERE 1=1");
    applyAttributionFilters(symbol, from, to, params, sql);
    sql.append(" GROUP BY COALESCE(preset, 'N/A'), COALESCE(regime, 'N/A')");
    sql.append(" ORDER BY SUM(pnl_net) DESC");
    return jdbcTemplate.query(
        sql.toString(),
        params,
        (rs, rowNum) ->
            new PnlAttributionGroup(
                rs.getString("preset"),
                rs.getString("regime"),
                rs.getBigDecimal("pnl_gross"),
                rs.getBigDecimal("signal_edge"),
                rs.getBigDecimal("timing_cost"),
                rs.getBigDecimal("slippage_cost"),
                rs.getBigDecimal("fees_cost"),
                rs.getBigDecimal("pnl_net"),
                rs.getLong("trades")));
  }

  @Override
  public List<PnlAttributionSymbolStats> pnlAttributionSymbolStats(
      String symbol, Instant from, Instant to) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql =
        new StringBuilder(
            "SELECT symbol, "
                + "SUM(slippage_cost) AS slippage_sum, "
                + "SUM(timing_cost) AS timing_sum, "
                + "SUM(notional) AS notional_sum, "
                + "MAX(slippage_bps) AS slippage_max, "
                + "MIN(slippage_bps) AS slippage_min, "
                + "MAX(timing_bps) AS timing_max, "
                + "MIN(timing_bps) AS timing_min, "
                + "SUM(pnl_net) AS pnl_net "
                + "FROM pnl_attr WHERE 1=1");
    applyAttributionFilters(symbol, from, to, params, sql);
    sql.append(" GROUP BY symbol");
    if (!StringUtils.hasText(symbol)) {
      sql.append(" ORDER BY ABS(COALESCE(MAX(slippage_bps), 0)) DESC LIMIT 10");
    }
    return jdbcTemplate.query(
        sql.toString(),
        params,
        (rs, rowNum) -> {
          double notional = Optional.ofNullable(rs.getBigDecimal("notional_sum")).map(BigDecimal::doubleValue).orElse(0.0);
          double slippageSum =
              Optional.ofNullable(rs.getBigDecimal("slippage_sum")).map(BigDecimal::doubleValue).orElse(0.0);
          double timingSum =
              Optional.ofNullable(rs.getBigDecimal("timing_sum")).map(BigDecimal::doubleValue).orElse(0.0);
          double avgSlippage = notional == 0.0 ? 0.0 : slippageSum / notional * 10_000d;
          double avgTiming = notional == 0.0 ? 0.0 : timingSum / notional * 10_000d;
          return new PnlAttributionSymbolStats(
              rs.getString("symbol"),
              avgSlippage,
              Optional.ofNullable(rs.getBigDecimal("slippage_max")).map(BigDecimal::doubleValue).orElse(Double.NaN),
              Optional.ofNullable(rs.getBigDecimal("slippage_min")).map(BigDecimal::doubleValue).orElse(Double.NaN),
              avgTiming,
              Optional.ofNullable(rs.getBigDecimal("timing_max")).map(BigDecimal::doubleValue).orElse(Double.NaN),
              Optional.ofNullable(rs.getBigDecimal("timing_min")).map(BigDecimal::doubleValue).orElse(Double.NaN),
              Optional.ofNullable(rs.getBigDecimal("pnl_net")).map(BigDecimal::doubleValue).orElse(0.0));
        });
  }

  @Override
  public PnlAttributionMetrics pnlAttributionMetrics(String symbol, Instant from, Instant to) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder totalsSql = new StringBuilder("SELECT SUM(timing_cost) AS timing_sum, SUM(fees_cost) AS fees_sum, SUM(notional) AS notional_sum FROM pnl_attr WHERE 1=1");
    applyAttributionFilters(symbol, from, to, params, totalsSql);
    Map<String, Object> totals = jdbcTemplate.queryForMap(totalsSql.toString(), params);
    double notional = optionalBigDecimal(totals.get("notional_sum"));
    double timingAvg = notional == 0.0 ? 0.0 : optionalBigDecimal(totals.get("timing_sum")) / notional * 10_000d;
    double feesAvg = notional == 0.0 ? 0.0 : optionalBigDecimal(totals.get("fees_sum")) / notional * 10_000d;

    MapSqlParameterSource symbolParams = new MapSqlParameterSource();
    StringBuilder perSymbol = new StringBuilder("SELECT symbol, SUM(slippage_cost) AS slip_sum, SUM(notional) AS notional_sum FROM pnl_attr WHERE 1=1");
    applyAttributionFilters(symbol, from, to, symbolParams, perSymbol);
    perSymbol.append(" GROUP BY symbol");
    Map<String, Double> averages =
        Optional.ofNullable(
                jdbcTemplate.query(
                    perSymbol.toString(),
                    symbolParams,
                    rs -> {
                      Map<String, Double> map = new LinkedHashMap<>();
                      while (rs.next()) {
                        double symbolNotional = optionalBigDecimal(rs.getBigDecimal("notional_sum"));
                        double slipAvg =
                            symbolNotional == 0.0
                                ? 0.0
                                : optionalBigDecimal(rs.getBigDecimal("slip_sum"))
                                    / symbolNotional
                                    * 10_000d;
                        map.put(rs.getString("symbol"), slipAvg);
                      }
                      return map;
                    }))
            .orElseGet(Collections::emptyMap);

    return new PnlAttributionMetrics(timingAvg, feesAvg, averages);
  }

  private void applyFilters(
      String symbol,
      Instant from,
      Instant to,
      String side,
      String status,
      MapSqlParameterSource params,
      StringBuilder sql) {
    if (StringUtils.hasText(symbol)) {
      sql.append(" AND symbol = :symbol");
      params.addValue("symbol", symbol);
    }
    if (from != null) {
      sql.append(" AND executed_at >= :from");
      params.addValue("from", from);
    }
    if (to != null) {
      sql.append(" AND executed_at <= :to");
      params.addValue("to", to);
    }
    if (StringUtils.hasText(side)) {
      sql.append(" AND side = :side");
      params.addValue("side", side);
    }
    if (StringUtils.hasText(status)) {
      sql.append(" AND COALESCE(position_status, 'UNKNOWN') = :status");
      params.addValue("status", status);
    }
  }

  private void applyAttributionFilters(
      String symbol, Instant from, Instant to, MapSqlParameterSource params, StringBuilder sql) {
    if (StringUtils.hasText(symbol)) {
      sql.append(" AND symbol = :attr_symbol");
      params.addValue("attr_symbol", symbol);
    }
    if (from != null) {
      sql.append(" AND ts >= :attr_from");
      params.addValue("attr_from", from);
    }
    if (to != null) {
      sql.append(" AND ts <= :attr_to");
      params.addValue("attr_to", to);
    }
  }

  private double optionalBigDecimal(Object value) {
    if (value instanceof BigDecimal bigDecimal) {
      return bigDecimal.doubleValue();
    }
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return 0.0;
  }

  private long count(String base, MapSqlParameterSource params, StringBuilder filters) {
    String countSql = base + filters.substring("SELECT * FROM vw_trades_enriched".length());
    return Optional.ofNullable(jdbcTemplate.queryForObject(countSql, params, Long.class)).orElse(0L);
  }

  private String resolveSort(Sort.Order order) {
    String property = switch (order.getProperty()) {
      case "price",
          "quantity",
          "fee",
          "feesBps",
          "pnl",
          "pnlNet",
          "signalEdge",
          "slippageBps",
          "timingBps",
          "symbol",
          "side" -> order.getProperty();
      case "executedAt" -> "executed_at";
      case "slippageCost" -> "slippage_cost";
      case "timingCost" -> "timing_cost";
      default -> "executed_at";
    };
    return property + (order.isAscending() ? " ASC" : " DESC");
  }

  private RowMapper<TradeDto> tradeRowMapper() {
    return (rs, rowNum) ->
        new TradeDto(
            rs.getLong("trade_id"),
            rs.getString("symbol"),
            rs.getString("side"),
            getInstant(rs, "executed_at"),
            rs.getBigDecimal("price"),
            rs.getBigDecimal("quantity"),
            rs.getBigDecimal("fee"),
            rs.getBigDecimal("fees_bps"),
            rs.getBigDecimal("pnl"),
            rs.getBigDecimal("pnl_net"),
            rs.getBigDecimal("signal_edge"),
            rs.getBigDecimal("pnl_r"),
            rs.getBigDecimal("slippage_bps"),
            rs.getBigDecimal("slippage_cost"),
            rs.getBigDecimal("timing_bps"),
            rs.getBigDecimal("timing_cost"),
            rs.getString("client_order_id"),
            rs.getString("decision_key"),
            rs.getString("decision_note"));
  }

  private RowMapper<SummaryBucket> summaryRowMapper() {
    return (rs, rowNum) -> {
      Instant start = getInstant(rs, "period_start");
      Instant end = getInstant(rs, "period_end");
      long trades = rs.getLong("trades");
      long wins = rs.getLong("wins");
      long losses = rs.getLong("losses");
      BigDecimal grossWins = Optional.ofNullable(rs.getBigDecimal("gross_wins")).orElse(BigDecimal.ZERO);
      BigDecimal grossLosses = Optional.ofNullable(rs.getBigDecimal("gross_losses")).orElse(BigDecimal.ZERO);
      BigDecimal netPnl = Optional.ofNullable(rs.getBigDecimal("net_pnl")).orElse(BigDecimal.ZERO);
      BigDecimal fees = Optional.ofNullable(rs.getBigDecimal("fees")).orElse(BigDecimal.ZERO);
      BigDecimal winRate =
          trades == 0
              ? BigDecimal.ZERO
              : BigDecimal.valueOf(wins)
                  .divide(BigDecimal.valueOf(trades), 4, RoundingMode.HALF_UP);
      BigDecimal profitFactor =
          grossLosses.compareTo(BigDecimal.ZERO) == 0
              ? null
              : grossWins.divide(grossLosses.abs(), 4, RoundingMode.HALF_UP);
      AdvancedMetrics metrics = computeAdvancedMetrics(rs.getString("symbol"), start, end);
      return new SummaryBucket(
          start,
          end,
          rs.getString("label"),
          trades,
          wins,
          losses,
          grossWins,
          netPnl,
          fees,
          winRate,
          profitFactor,
          metrics.maxDrawdown(),
          metrics.sharpe(),
          metrics.sortino());
    };
  }

  private RowMapper<AnnotationDto> annotationRowMapper() {
    return (rs, rowNum) ->
        new AnnotationDto(
            getInstant(rs, "executed_at"),
            rs.getString("type"),
            rs.getBigDecimal("price"),
            rs.getBigDecimal("quantity"),
            rs.getBigDecimal("pnl"),
            rs.getBigDecimal("pnl_r"),
            rs.getBigDecimal("fee"),
            rs.getBigDecimal("slippage_bps"),
            rs.getString("text"));
  }

  private RowMapper<HeatmapCell> heatmapRowMapper() {
    return (rs, rowNum) ->
        new HeatmapCell(
            rs.getInt("bucket_x"),
            rs.getInt("bucket_y"),
            rs.getLong("trades"),
            rs.getBigDecimal("net_pnl"),
            rs.getBigDecimal("win_rate"));
  }

  private Instant getInstant(ResultSet rs, String column) throws SQLException {
    return Optional.ofNullable(rs.getTimestamp(column)).map(ts -> ts.toInstant()).orElse(null);
  }

  private List<TradeDto> loadTradesForRange(String symbol, Instant from, Instant to) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    StringBuilder sql = new StringBuilder("SELECT * FROM vw_trades_enriched WHERE 1=1");
    applyFilters(symbol, from, to, null, null, params, sql);
    sql.append(" ORDER BY executed_at");
    return jdbcTemplate.query(sql.toString(), params, tradeRowMapper());
  }

  private void applySummaryFilters(
      String symbol, Instant from, Instant to, MapSqlParameterSource params, StringBuilder sql) {
    if (StringUtils.hasText(symbol)) {
      sql.append(" AND symbol = :symbol");
      params.addValue("symbol", symbol);
    }
    if (from != null) {
      sql.append(" AND period_end >= :from");
      params.addValue("from", from);
    }
    if (to != null) {
      sql.append(" AND period_start <= :to");
      params.addValue("to", to);
    }
  }

  private AdvancedMetrics computeAdvancedMetrics(String symbol, Instant start, Instant end) {
    List<TradeDto> trades = loadTradesForRange(symbol, start, end);
    if (trades.isEmpty()) {
      return new AdvancedMetrics(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
    BigDecimal equity = BigDecimal.ZERO;
    BigDecimal peak = BigDecimal.ZERO;
    BigDecimal maxDrawdown = BigDecimal.ZERO;
    Map<String, BigDecimal> daily = new LinkedHashMap<>();
    for (TradeDto trade : trades) {
      BigDecimal pnl = Optional.ofNullable(trade.pnl()).orElse(BigDecimal.ZERO);
      equity = equity.add(pnl);
      peak = peak.max(equity);
      if (peak.compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal dd = peak.subtract(equity).divide(peak, 8, RoundingMode.HALF_UP);
        if (dd.compareTo(maxDrawdown) > 0) {
          maxDrawdown = dd;
        }
      }
      if (trade.executedAt() != null) {
        String key = trade.executedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();
        daily.merge(key, pnl, BigDecimal::add);
      }
    }
    List<BigDecimal> returns = new ArrayList<>(daily.values());
    BigDecimal sharpe = computeSharpe(returns);
    BigDecimal sortino = computeSortino(returns);
    return new AdvancedMetrics(maxDrawdown, sharpe, sortino);
  }

  private BigDecimal computeSharpe(List<BigDecimal> returns) {
    if (returns.isEmpty()) {
      return BigDecimal.ZERO;
    }
    BigDecimal mean = mean(returns);
    BigDecimal std = stddev(returns, mean, false);
    if (std.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    double annualizedFactor = Math.sqrt(252);
    return BigDecimal.valueOf(mean.doubleValue() / std.doubleValue() * annualizedFactor)
        .setScale(4, RoundingMode.HALF_UP);
  }

  private BigDecimal computeSortino(List<BigDecimal> returns) {
    if (returns.isEmpty()) {
      return BigDecimal.ZERO;
    }
    BigDecimal mean = mean(returns);
    List<BigDecimal> downside = returns.stream().filter(r -> r.compareTo(BigDecimal.ZERO) < 0).toList();
    if (downside.isEmpty()) {
      return BigDecimal.ZERO;
    }
    BigDecimal std = stddev(downside, BigDecimal.ZERO, true);
    if (std.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    double annualizedFactor = Math.sqrt(252);
    return BigDecimal.valueOf(mean.doubleValue() / std.doubleValue() * annualizedFactor)
        .setScale(4, RoundingMode.HALF_UP);
  }

  private BigDecimal mean(List<BigDecimal> values) {
    if (values.isEmpty()) {
      return BigDecimal.ZERO;
    }
    BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    return sum.divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
  }

  private BigDecimal stddev(List<BigDecimal> values, BigDecimal mean, boolean usePopulation) {
    if (values.size() <= 1) {
      return BigDecimal.ZERO;
    }
    BigDecimal variance = BigDecimal.ZERO;
    for (BigDecimal value : values) {
      BigDecimal diff = value.subtract(mean);
      variance = variance.add(diff.multiply(diff));
    }
    int divisor = usePopulation ? values.size() : values.size() - 1;
    variance = variance.divide(BigDecimal.valueOf(divisor), 8, RoundingMode.HALF_UP);
    return BigDecimal.valueOf(Math.sqrt(variance.doubleValue())).setScale(8, RoundingMode.HALF_UP);
  }

  private record AdvancedMetrics(BigDecimal maxDrawdown, BigDecimal sharpe, BigDecimal sortino) {}
}
