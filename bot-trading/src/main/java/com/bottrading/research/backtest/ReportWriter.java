package com.bottrading.research.backtest;

import com.bottrading.research.io.ChartExporter;
import com.bottrading.research.io.CsvWriter;
import com.bottrading.research.io.JsonWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    Path tradesPath = directory.resolve("trades.csv");
    List<String[]> rows = new ArrayList<>();
    rows.add(new String[] {"entryTime", "entryPrice", "exitTime", "exitPrice", "quantity", "pnl", "win"});
    for (TradeRecord trade : result.trades()) {
      rows.add(
          new String[] {
            String.valueOf(trade.entryTime().toEpochMilli()),
            trade.entryPrice().toPlainString(),
            String.valueOf(trade.exitTime().toEpochMilli()),
            trade.exitPrice().toPlainString(),
            trade.quantity().toPlainString(),
            trade.pnl().toPlainString(),
            Boolean.toString(trade.win())
          });
    }
    csvWriter.write(tradesPath, rows);

    jsonWriter.write(directory.resolve("metrics.json"), result.metrics());
    chartExporter.export(directory, result.equityCurve());
  }
}
