package com.bottrading.research.io;

// FIX: Use XYChart for price+trades so we can overlay scatter markers in all XChart versions.

import com.bottrading.model.dto.Kline;
import com.bottrading.research.backtest.EquityPoint;
import com.bottrading.research.backtest.MetricsSummary;
import com.bottrading.research.backtest.TradeRecord;
import java.awt.Color;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.BitmapEncoder.BitmapFormat;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.XYSeries.XYSeriesRenderStyle;
import org.knowm.xchart.internal.chartpart.Chart;
import org.knowm.xchart.style.Styler.LegendPosition;
import org.knowm.xchart.style.lines.SeriesLines;
import org.knowm.xchart.style.markers.SeriesMarkers;

public class ChartExporter {

  private static final DateTimeFormatter LABEL_TIME_FORMAT =
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                  .withLocale(Locale.getDefault())
                  .withZone(ZoneId.systemDefault());

  private final CsvWriter csvWriter;

  public ChartExporter(CsvWriter csvWriter) {
    this.csvWriter = csvWriter;
  }

  public void exportAll(
          Path directory,
          String symbol,
          String runId,
          List<Kline> klines,
          List<TradeRecord> trades,
          List<EquityPoint> equityCurve,
          MetricsSummary metrics)
          throws IOException {
    if (directory == null) return;
    Files.createDirectories(directory);
    exportPriceChart(directory.resolve("price_with_trades.png"), symbol, runId, klines, trades);
    exportEquityChart(directory.resolve("equity_curve.png"), equityCurve);
    exportDrawdownChart(directory.resolve("drawdown.png"), equityCurve);
  }

  /** Precio (línea de cierre) + marcadores de entradas/salidas como scatter. */
  private void exportPriceChart(
          Path output,
          String symbol,
          String runId,
          List<Kline> klines,
          List<TradeRecord> trades)
          throws IOException {
    if (klines == null || klines.isEmpty()) return;

    XYChart chart =
            new XYChartBuilder()
                    .width(1400)
                    .height(720)
                    .title("Precio " + symbol + " | Run " + (runId == null ? "N/A" : runId))
                    .xAxisTitle("Tiempo")
                    .yAxisTitle("Precio (close)")
                    .build();

    chart.getStyler().setLegendPosition(LegendPosition.OutsideE);
    chart.getStyler().setDatePattern("yyyy-MM-dd HH:mm");
    chart.getStyler().setToolTipsEnabled(true);
    chart.getStyler().setPlotGridLinesVisible(true);
    chart.getStyler().setPlotGridLinesColor(new Color(200, 200, 200));

    // Serie de precio: línea de cierres
    List<Date> dates = new ArrayList<>(klines.size());
    List<Double> closes = new ArrayList<>(klines.size());
    for (Kline k : klines) {
      dates.add(Date.from(k.openTime()));
      closes.add(k.close().doubleValue());
    }
    XYSeries price = chart.addSeries("Close", dates, closes);
    price.setXYSeriesRenderStyle(XYSeriesRenderStyle.Line);
    price.setLineColor(new Color(52, 152, 219));
    price.setLineWidth(2f);
    price.setMarker(SeriesMarkers.NONE);

    // Marcadores de trades
    addTradeMarkers(chart, trades);

    saveChart(chart, output);
  }

  private void addTradeMarkers(XYChart chart, List<TradeRecord> trades) {
    if (trades == null || trades.isEmpty()) return;

    List<Date> entryTimes = new ArrayList<>();
    List<Double> entryPrices = new ArrayList<>();
    List<Date> exitTimes = new ArrayList<>();
    List<Double> exitPrices = new ArrayList<>();
    for (TradeRecord t : trades) {
      entryTimes.add(Date.from(t.entryTime()));
      entryPrices.add(t.entryPrice().doubleValue());
      exitTimes.add(Date.from(t.exitTime()));
      exitPrices.add(t.exitPrice().doubleValue());
    }

    if (!entryTimes.isEmpty()) {
      XYSeries entries = chart.addSeries("Entradas", entryTimes, entryPrices);
      entries.setXYSeriesRenderStyle(XYSeriesRenderStyle.Scatter);
      entries.setMarker(SeriesMarkers.CIRCLE);
      entries.setMarkerColor(new Color(39, 174, 96));
      entries.setLineStyle(SeriesLines.NONE);
    }

    if (!exitTimes.isEmpty()) {
      XYSeries exits = chart.addSeries("Salidas", exitTimes, exitPrices);
      exits.setXYSeriesRenderStyle(XYSeriesRenderStyle.Scatter);
      exits.setMarker(SeriesMarkers.DIAMOND);
      exits.setMarkerColor(new Color(192, 57, 43));
      exits.setLineStyle(SeriesLines.NONE);
    }
  }

  private void exportEquityChart(Path output, List<EquityPoint> equityCurve) throws IOException {
    if (equityCurve == null || equityCurve.isEmpty()) return;

    XYChart chart =
            new XYChartBuilder()
                    .width(1200)
                    .height(600)
                    .title("Equity Curve")
                    .xAxisTitle("Tiempo")
                    .yAxisTitle("Equity")
                    .build();
    chart.getStyler().setLegendVisible(false);
    chart.getStyler().setDatePattern("yyyy-MM-dd HH:mm");

    List<Date> xData = new ArrayList<>(equalitySize(equityCurve));
    List<Double> yData = new ArrayList<>(equalitySize(equityCurve));
    for (EquityPoint p : equityCurve) {
      xData.add(Date.from(p.time()));
      yData.add(p.equity().doubleValue());
    }
    chart.addSeries("Equity", xData, yData).setLineColor(new Color(41, 128, 185));
    saveChart(chart, output);
    exportEquityCsv(output.getParent().resolve("equity.csv"), equityCurve);
  }

  private void exportDrawdownChart(Path output, List<EquityPoint> equityCurve) throws IOException {
    if (equityCurve == null || equityCurve.isEmpty()) return;

    XYChart chart =
            new XYChartBuilder()
                    .width(1200)
                    .height(400)
                    .title("Drawdown %")
                    .xAxisTitle("Tiempo")
                    .yAxisTitle("DD %")
                    .build();
    chart.getStyler().setLegendVisible(false);
    chart.getStyler().setDatePattern("yyyy-MM-dd HH:mm");

    List<Date> xData = new ArrayList<>(equalitySize(equityCurve));
    List<Double> yData = new ArrayList<>(equalitySize(equityCurve));
    double peak = equityCurve.get(0).equity().doubleValue();
    for (EquityPoint p : equityCurve) {
      peak = Math.max(peak, p.equity().doubleValue());
      double dd = peak == 0 ? 0 : (p.equity().doubleValue() - peak) / peak;
      xData.add(Date.from(p.time()));
      yData.add(dd * 100);
    }
    XYSeries series = chart.addSeries("Drawdown", xData, yData);
    series.setLineColor(new Color(231, 76, 60));
    series.setLineWidth(2f);
    saveChart(chart, output);
    exportDrawdownCsv(output.getParent().resolve("drawdown.csv"), xData, yData);
  }

  private int equalitySize(List<?> list) {
    return list == null ? 0 : list.size();
  }

  private void exportEquityCsv(Path path, List<EquityPoint> equityCurve) throws IOException {
    List<String[]> rows = new ArrayList<>();
    rows.add(new String[] {"timestamp", "equity"});
    for (EquityPoint point : equityCurve) {
      rows.add(new String[] {
              String.valueOf(point.time().toEpochMilli()),
              point.equity().toPlainString()
      });
    }
    csvWriter.write(path, rows);
  }

  private void exportDrawdownCsv(Path path, List<Date> dates, List<Double> drawdownPct) throws IOException {
    List<String[]> rows = new ArrayList<>();
    rows.add(new String[] {"timestamp", "drawdown_pct"});
    for (int i = 0; i < dates.size(); i++) {
      rows.add(new String[] {
              String.valueOf(dates.get(i).getTime()),
              String.format(Locale.US, "%.6f", drawdownPct.get(i))
      });
    }
    csvWriter.write(path, rows);
  }

  private void saveChart(Chart<?, ?> chart, Path output) throws IOException {
    Files.createDirectories(output.getParent());
    String base = output.toString();
    if (base.endsWith(".png")) base = base.substring(0, base.length() - 4);
    BitmapEncoder.saveBitmapWithDPI(chart, base, BitmapFormat.PNG, 150);
  }

  private String formatPrice(BigDecimal price) {
    return new DecimalFormat("#,##0.########").format(price);
  }

  private String summarizeSignals(List<String> signals) {
    if (signals == null || signals.isEmpty()) return "N/A";
    if (signals.size() <= 3) return String.join(" | ", signals);
    return String.join(" | ", signals.subList(0, 3)) + " (+" + (signals.size() - 3) + ")";
  }

  private String safeText(String value) {
    return (value == null || value.isBlank()) ? "-" : value;
  }

  private String decimal(BigDecimal value) {
    return (value == null) ? "n/a" : new DecimalFormat("0.00").format(value);
  }

  private String percent(BigDecimal value) {
    return (value == null) ? "n/a" : new DecimalFormat("0.00%").format(value.doubleValue());
  }
}
