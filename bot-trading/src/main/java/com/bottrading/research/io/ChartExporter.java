package com.bottrading.research.io;

import com.bottrading.model.dto.Kline;
import com.bottrading.research.backtest.EquityPoint;
import com.bottrading.research.backtest.MetricsSummary;
import com.bottrading.research.backtest.TradeRecord;
import com.bottrading.strategy.SignalSide;
import java.awt.Color;
import java.awt.Font;
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
import java.util.StringJoiner;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.BitmapEncoder.BitmapFormat;
import org.knowm.xchart.OHLCChart;
import org.knowm.xchart.OHLCChartBuilder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.XYSeries.XYSeriesRenderStyle;
import org.knowm.xchart.annotations.XYTextAnnotation;
import org.knowm.xchart.internal.chartpart.Chart;
import org.knowm.xchart.style.Styler.LegendPosition;
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
    if (directory == null) {
      return;
    }
    Files.createDirectories(directory);
    exportPriceChart(directory.resolve("price_with_trades.png"), symbol, runId, klines, trades, metrics);
    exportEquityChart(directory.resolve("equity_curve.png"), equityCurve);
    exportDrawdownChart(directory.resolve("drawdown.png"), equityCurve);
  }

  private void exportPriceChart(
      Path output,
      String symbol,
      String runId,
      List<Kline> klines,
      List<TradeRecord> trades,
      MetricsSummary metrics)
      throws IOException {
    if (klines == null || klines.isEmpty()) {
      return;
    }
    OHLCChart chart =
        new OHLCChartBuilder()
            .width(1400)
            .height(720)
            .title("Precio " + symbol + " | Run " + (runId == null ? "N/A" : runId))
            .xAxisTitle("Tiempo")
            .yAxisTitle("Precio")
            .build();
    chart.getStyler().setLegendPosition(LegendPosition.OutsideE);
    chart.getStyler().setDatePattern("yyyy-MM-dd HH:mm");
    chart.getStyler().setToolTipsEnabled(true);
    chart.getStyler().setToolTipsAlwaysVisible(false);
    chart.getStyler().setPlotGridLinesVisible(true);
    chart.getStyler().setPlotGridLinesColor(new Color(200, 200, 200));

    List<Date> dates = new ArrayList<>(klines.size());
    List<Double> opens = new ArrayList<>(klines.size());
    List<Double> highs = new ArrayList<>(klines.size());
    List<Double> lows = new ArrayList<>(klines.size());
    List<Double> closes = new ArrayList<>(klines.size());
    for (Kline kline : klines) {
      dates.add(Date.from(kline.openTime()));
      opens.add(kline.open().doubleValue());
      highs.add(kline.high().doubleValue());
      lows.add(kline.low().doubleValue());
      closes.add(kline.close().doubleValue());
    }
    chart.addSeries("Precio", dates, opens, highs, lows, closes);

    addTradeMarkers(chart, trades);
    annotateMetrics(chart, metrics, klines, highs);

    saveChart(chart, output);
  }

  private void addTradeMarkers(OHLCChart chart, List<TradeRecord> trades) {
    if (trades == null || trades.isEmpty()) {
      return;
    }
    List<Date> buyWinsX = new ArrayList<>();
    List<Double> buyWinsY = new ArrayList<>();
    List<Date> buyLossX = new ArrayList<>();
    List<Double> buyLossY = new ArrayList<>();
    List<Date> sellWinsX = new ArrayList<>();
    List<Double> sellWinsY = new ArrayList<>();
    List<Date> sellLossX = new ArrayList<>();
    List<Double> sellLossY = new ArrayList<>();
    List<Date> exitsX = new ArrayList<>();
    List<Double> exitsY = new ArrayList<>();

    for (TradeRecord trade : trades) {
      Date entryDate = Date.from(trade.entryTime());
      double entryPrice = trade.entryPrice().doubleValue();
      boolean win = trade.win();
      if (trade.side() == SignalSide.SELL) {
        if (win) {
          sellWinsX.add(entryDate);
          sellWinsY.add(entryPrice);
        } else {
          sellLossX.add(entryDate);
          sellLossY.add(entryPrice);
        }
      } else {
        if (win) {
          buyWinsX.add(entryDate);
          buyWinsY.add(entryPrice);
        } else {
          buyLossX.add(entryDate);
          buyLossY.add(entryPrice);
        }
      }
      exitsX.add(Date.from(trade.exitTime()));
      exitsY.add(trade.exitPrice().doubleValue());
      addTradeAnnotation(chart, trade);
    }

    if (!buyWinsX.isEmpty()) {
      XYSeries series = chart.addSeries("BUY ✅", buyWinsX, buyWinsY);
      series.setXYSeriesRenderStyle(XYSeriesRenderStyle.Scatter);
      series.setMarker(SeriesMarkers.CIRCLE);
      series.setMarkerColor(new Color(46, 204, 113));
    }
    if (!buyLossX.isEmpty()) {
      XYSeries series = chart.addSeries("BUY ❌", buyLossX, buyLossY);
      series.setXYSeriesRenderStyle(XYSeriesRenderStyle.Scatter);
      series.setMarker(SeriesMarkers.CROSS);
      series.setMarkerColor(new Color(231, 76, 60));
    }
    if (!sellWinsX.isEmpty()) {
      XYSeries series = chart.addSeries("SELL ✅", sellWinsX, sellWinsY);
      series.setXYSeriesRenderStyle(XYSeriesRenderStyle.Scatter);
      series.setMarker(SeriesMarkers.CIRCLE);
      series.setMarkerColor(new Color(52, 152, 219));
    }
    if (!sellLossX.isEmpty()) {
      XYSeries series = chart.addSeries("SELL ❌", sellLossX, sellLossY);
      series.setXYSeriesRenderStyle(XYSeriesRenderStyle.Scatter);
      series.setMarker(SeriesMarkers.CROSS);
      series.setMarkerColor(new Color(142, 68, 173));
    }
    if (!exitsX.isEmpty()) {
      XYSeries series = chart.addSeries("Salidas", exitsX, exitsY);
      series.setXYSeriesRenderStyle(XYSeriesRenderStyle.Scatter);
      series.setMarker(SeriesMarkers.DIAMOND);
      series.setMarkerColor(new Color(44, 62, 80));
    }
  }

  private void addTradeAnnotation(OHLCChart chart, TradeRecord trade) {
    String entryLabel =
        "✅ "
            + trade.side()
            + " "
            + LABEL_TIME_FORMAT.format(trade.entryTime())
            + "\nPrecio: "
            + formatPrice(trade.entryPrice())
            + "\nSeñales: "
            + summarizeSignals(trade.entrySignals())
            + "\nMotivo: "
            + safeText(trade.entryReason());
    XYTextAnnotation entryAnnotation =
        new XYTextAnnotation(
            entryLabel,
            Date.from(trade.entryTime()).getTime(),
            trade.entryPrice().doubleValue());
    entryAnnotation.setFont(new Font("SansSerif", Font.PLAIN, 10));
    chart.addAnnotation(entryAnnotation);

    String exitLabel =
        "❌ "
            + LABEL_TIME_FORMAT.format(trade.exitTime())
            + "\nPrecio: "
            + formatPrice(trade.exitPrice())
            + "\nSeñales: "
            + summarizeSignals(trade.exitSignals())
            + "\nMotivo: "
            + safeText(trade.exitReason())
            + "\nPnL: "
            + formatPrice(trade.pnl());
    XYTextAnnotation exitAnnotation =
        new XYTextAnnotation(
            exitLabel, Date.from(trade.exitTime()).getTime(), trade.exitPrice().doubleValue());
    exitAnnotation.setFont(new Font("SansSerif", Font.PLAIN, 10));
    chart.addAnnotation(exitAnnotation);
  }

  private void annotateMetrics(
      OHLCChart chart,
      MetricsSummary metrics,
      List<Kline> klines,
      List<Double> highs) {
    if (metrics == null) {
      return;
    }
    StringJoiner joiner = new StringJoiner("  |  ");
    joiner.add("WinRate: " + percent(metrics.winRate()));
    joiner.add("ProfitFactor: " + decimal(metrics.profitFactor()));
    joiner.add("MaxDD: " + percent(metrics.maxDrawdown()));
    joiner.add("Sharpe: " + decimal(metrics.sharpe()));
    joiner.add("Sortino: " + decimal(metrics.sortino()));
    joiner.add("Trades: " + metrics.trades());
    double top = highs.stream().mapToDouble(Double::doubleValue).max().orElse(0);
    if (!klines.isEmpty()) {
      XYTextAnnotation annotation =
          new XYTextAnnotation(
              joiner.toString(), Date.from(klines.get(0).openTime()).getTime(), top * 1.01);
      annotation.setFont(new Font("SansSerif", Font.BOLD, 11));
      chart.addAnnotation(annotation);
    }
  }

  private void exportEquityChart(Path output, List<EquityPoint> equityCurve) throws IOException {
    if (equityCurve == null || equityCurve.isEmpty()) {
      return;
    }
    XYChart chart =
        new XYChartBuilder().width(1200).height(600).title("Equity Curve").xAxisTitle("Tiempo").yAxisTitle("Equity").build();
    chart.getStyler().setLegendVisible(false);
    chart.getStyler().setDatePattern("yyyy-MM-dd HH:mm");

    List<Date> xData = new ArrayList<>(equityCurve.size());
    List<Double> yData = new ArrayList<>(equityCurve.size());
    for (EquityPoint point : equityCurve) {
      xData.add(Date.from(point.time()));
      yData.add(point.equity().doubleValue());
    }
    chart.addSeries("Equity", xData, yData).setLineColor(new Color(41, 128, 185));
    saveChart(chart, output);
    exportEquityCsv(output.getParent().resolve("equity.csv"), equityCurve);
  }

  private void exportDrawdownChart(Path output, List<EquityPoint> equityCurve) throws IOException {
    if (equityCurve == null || equityCurve.isEmpty()) {
      return;
    }
    XYChart chart =
        new XYChartBuilder().width(1200).height(400).title("Drawdown %").xAxisTitle("Tiempo").yAxisTitle("DD %").build();
    chart.getStyler().setLegendVisible(false);
    chart.getStyler().setDatePattern("yyyy-MM-dd HH:mm");

    List<Date> xData = new ArrayList<>(equityCurve.size());
    List<Double> yData = new ArrayList<>(equityCurve.size());
    double peak = equityCurve.get(0).equity().doubleValue();
    for (EquityPoint point : equityCurve) {
      peak = Math.max(peak, point.equity().doubleValue());
      double dd = peak == 0 ? 0 : (point.equity().doubleValue() - peak) / peak;
      xData.add(Date.from(point.time()));
      yData.add(dd * 100);
    }
    XYSeries series = chart.addSeries("Drawdown", xData, yData);
    series.setLineColor(new Color(231, 76, 60));
    series.setLineWidth(2f);
    saveChart(chart, output);
    exportDrawdownCsv(output.getParent().resolve("drawdown.csv"), xData, yData);
  }

  private void exportEquityCsv(Path path, List<EquityPoint> equityCurve) throws IOException {
    List<String[]> rows = new ArrayList<>();
    rows.add(new String[] {"timestamp", "equity"});
    for (EquityPoint point : equityCurve) {
      rows.add(new String[] {String.valueOf(point.time().toEpochMilli()), point.equity().toPlainString()});
    }
    csvWriter.write(path, rows);
  }

  private void exportDrawdownCsv(Path path, List<Date> dates, List<Double> drawdownPct) throws IOException {
    List<String[]> rows = new ArrayList<>();
    rows.add(new String[] {"timestamp", "drawdown_pct"});
    for (int i = 0; i < dates.size(); i++) {
      rows.add(new String[] {String.valueOf(dates.get(i).getTime()), String.format(Locale.US, "%.6f", drawdownPct.get(i))});
    }
    csvWriter.write(path, rows);
  }

  private void saveChart(Chart<?, ?> chart, Path output) throws IOException {
    Files.createDirectories(output.getParent());
    String base = output.toString();
    if (base.endsWith(".png")) {
      base = base.substring(0, base.length() - 4);
    }
    BitmapEncoder.saveBitmapWithDPI(chart, base, BitmapFormat.PNG, 150);
  }

  private String formatPrice(BigDecimal price) {
    return new DecimalFormat("#,##0.########").format(price);
  }

  private String summarizeSignals(List<String> signals) {
    if (signals == null || signals.isEmpty()) {
      return "N/A";
    }
    if (signals.size() <= 3) {
      return String.join(" | ", signals);
    }
    return String.join(" | ", signals.subList(0, 3)) + " (+" + (signals.size() - 3) + ")";
  }

  private String safeText(String value) {
    return value == null || value.isBlank() ? "-" : value;
  }

  private String decimal(BigDecimal value) {
    if (value == null) {
      return "n/a";
    }
    return new DecimalFormat("0.00").format(value);
  }

  private String percent(BigDecimal value) {
    if (value == null) {
      return "n/a";
    }
    return new DecimalFormat("0.00%").format(value.doubleValue());
  }
}
