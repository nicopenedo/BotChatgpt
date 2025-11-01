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
          "exitSignals"
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
            String.join("|", trade.exitSignals())
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
    metrics.put("trades", result.metrics().trades());
    metrics.put("exposure", asString(result.metrics().exposure()));
    summary.put("metrics", metrics);

    summary.put("trades", tradesSummary(result.trades()));

    return summary;
  }

  private Map<String, Object> tradesSummary(List<TradeRecord> trades) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("total", trades.size());
    long wins = trades.stream().filter(TradeRecord::win).count();
    map.put("wins", wins);
    map.put("losses", trades.size() - wins);
    return map;
  }

  private String asString(Object value) {
    if (value == null) {
      return null;
    }
    return String.valueOf(value);
  }
}
