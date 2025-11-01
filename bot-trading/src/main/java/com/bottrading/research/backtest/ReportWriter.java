package com.bottrading.research.backtest;

import com.bottrading.research.io.ChartExporter;
import com.bottrading.research.io.CsvWriter;
import com.bottrading.research.io.JsonWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReportWriter {

  private final CsvWriter csvWriter;
  private final JsonWriter jsonWriter;
  private final ChartExporter chartExporter;

  public ReportWriter(CsvWriter csvWriter, JsonWriter jsonWriter, ChartExporter chartExporter) {
    this.csvWriter = csvWriter;
    this.jsonWriter = jsonWriter;
    this.chartExporter = chartExporter;
  }

  public void write(Path directory, BacktestResult result) throws IOException {
    if (directory != null) {
      Files.createDirectories(directory);
    }
    writeTrades(directory.resolve("trades.csv"), result.trades());
    chartExporter.exportAll(
        directory,
        result.request().symbol(),
        result.request().runId(),
        result.klines(),
        result.trades(),
        result.equityCurve(),
        result.metrics());
    jsonWriter.write(directory.resolve("summary.json"), buildSummary(result));
  }

  private void writeTrades(Path path, List<TradeRecord> trades) throws IOException {
    List<String[]> rows = new ArrayList<>();
    rows.add(
        new String[] {
          "timestamp",
          "side",
          "price",
          "qty",
          "motivo",
          "pnl",
          "exitTimestamp",
          "exitPrice",
          "exitMotivo",
          "entrySignals",
          "exitSignals",
          "entryFills",
          "exitFills",
          "totalFees",
          "slippageBps",
          "avgQueueMs",
          "riskMultiple",
          "entryExec",
          "exitExec"
        });
    for (TradeRecord trade : trades) {
      rows.add(
          new String[] {
            String.valueOf(trade.entryTime().toEpochMilli()),
            trade.side().name(),
            trade.entryPrice().toPlainString(),
            trade.quantity().toPlainString(),
            trade.entryReason(),
            trade.pnl().toPlainString(),
            String.valueOf(trade.exitTime().toEpochMilli()),
            trade.exitPrice().toPlainString(),
            trade.exitReason(),
            String.join("|", trade.entrySignals()),
            String.join("|", trade.exitSignals()),
            formatFills(trade.entryFills()),
            formatFills(trade.exitFills()),
            trade.totalFees().toPlainString(),
            trade.slippageBps().toPlainString(),
            trade.averageQueueTimeMs().toPlainString(),
            trade.riskMultiple().toPlainString(),
            trade.entryExecutionType().name(),
            trade.exitExecutionType().name()
          });
    }
    csvWriter.write(path, rows);
  }

  private Map<String, Object> buildSummary(BacktestResult result) {
    Map<String, Object> summary = new LinkedHashMap<>();
    BacktestRequest request = result.request();
    summary.put("symbol", request.symbol());
    summary.put("interval", request.interval());
    summary.put("from", request.from() != null ? request.from().toString() : null);
    summary.put("to", request.to() != null ? request.to().toString() : null);
    summary.put("runId", request.runId());
    summary.put("seed", request.seed());
    summary.put("dataHash", result.dataHash());
    summary.put("generatedAt", Instant.now().toString());
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("slippageBps", asString(request.slippageBps()));
    params.put("takerFeeBps", asString(request.takerFeeBps()));
    params.put("makerFeeBps", asString(request.makerFeeBps()));
    params.put("dynamicFees", request.useDynamicFees());
    params.put("strategyConfig", request.strategyConfig() != null ? request.strategyConfig().toString() : null);
    params.put("genomesConfig", request.genomesConfig() != null ? request.genomesConfig().toString() : null);
    summary.put("parameters", params);

    Map<String, Object> metrics = new LinkedHashMap<>();
    metrics.put("cagr", asString(result.metrics().cagr()));
    metrics.put("sharpe", asString(result.metrics().sharpe()));
    metrics.put("sortino", asString(result.metrics().sortino()));
    metrics.put("calmar", asString(result.metrics().calmar()));
    metrics.put("maxDrawdown", asString(result.metrics().maxDrawdown()));
    metrics.put("profitFactor", asString(result.metrics().profitFactor()));
    metrics.put("winRate", asString(result.metrics().winRate()));
    metrics.put("expectancy", asString(result.metrics().expectancy()));
    metrics.put("averageR", asString(result.metrics().averageR()));
    metrics.put("trades", result.metrics().trades());
    metrics.put("exposure", asString(result.metrics().exposure()));
    metrics.put("fillRate", asString(result.metrics().fillRate()));
    metrics.put("ttlExpiredRate", asString(result.metrics().ttlExpiredRate()));
    summary.put("metrics", metrics);

    summary.put("trades", tradesSummary(result.trades(), result.executionStatistics()));

    return summary;
  }

  private Map<String, Object> tradesSummary(List<TradeRecord> trades, ExecutionStatistics stats) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("total", trades.size());
    long wins = trades.stream().filter(TradeRecord::win).count();
    map.put("wins", wins);
    map.put("losses", trades.size() - wins);
    if (stats != null) {
      map.put("requestedQty", asString(stats.requestedQty()));
      map.put("filledQty", asString(stats.filledQty()));
      map.put("fillRate", asString(stats.fillRate()));
      map.put("ttlExpired", stats.ttlExpiredOrders());
    }
    return map;
  }

  private String formatFills(List<FillDetail> fills) {
    if (fills == null || fills.isEmpty()) {
      return "";
    }
    List<String> encoded = new ArrayList<>();
    for (FillDetail fill : fills) {
      long epoch = fill.time() != null ? fill.time().toEpochMilli() : 0L;
      encoded.add(
          epoch
              + "@"
              + fill.price().toPlainString()
              + "#"
              + fill.quantity().toPlainString()
              + "#"
              + fill.queueTimeMs());
    }
    return String.join("|", encoded);
  }

  private String asString(Object value) {
    if (value == null) {
      return null;
    }
    return String.valueOf(value);
  }
}
