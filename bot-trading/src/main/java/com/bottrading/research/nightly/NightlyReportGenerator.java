package com.bottrading.research.nightly;

import com.bottrading.research.backtest.MetricsSummary;
import com.bottrading.research.regime.RegimeTrend;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

public class NightlyReportGenerator {

  private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("0.###");

  public ReportFiles generate(ReportData data) throws IOException {
    Files.createDirectories(data.reportDir());
    Path html = data.reportDir().resolve("report.html");
    String htmlContent = buildHtml(data);
    Files.writeString(html, htmlContent, StandardCharsets.UTF_8);
    Path pdf = data.reportDir().resolve("report.pdf");
    try (OutputStream outputStream = Files.newOutputStream(pdf)) {
      PdfRendererBuilder builder = new PdfRendererBuilder();
      builder.useFastMode();
      builder.withHtmlContent(htmlContent, data.reportDir().toUri().toString());
      builder.toStream(outputStream);
      builder.run();
    } catch (Exception ex) {
      throw new IOException("Unable to render nightly report PDF", ex);
    }
    return new ReportFiles(html, pdf);
  }

  private String buildHtml(ReportData data) {
    StringBuilder builder = new StringBuilder();
    builder
        .append("<html><head><meta charset=\"utf-8\"/><title>Nightly Research Report</title>")
        .append("<style>body{font-family:Arial,sans-serif;margin:24px;} table{border-collapse:collapse;margin:16px 0;} th,td{border:1px solid #ccc;padding:8px;text-align:left;} h2{margin-top:32px;} .status{font-size:1.2em;font-weight:bold;} .charts img{max-width:720px;margin:12px 0;} </style>")
        .append("</head><body>");
    builder
        .append("<h1>Nightly Research Report</h1>")
        .append("<div class=\"status\">Run ID: ")
        .append(escape(data.runId()))
        .append(" | Regime: ")
        .append(data.trend())
        .append(" | Status: ")
        .append(escape(data.status()))
        .append("</div>");
    if (data.note() != null && !data.note().isBlank()) {
      builder
          .append("<p><strong>Notes:</strong> ")
          .append(escape(data.note()))
          .append("</p>");
    }

    builder.append("<h2>Walk-Forward Metrics</h2>");
    if (data.walkForward() == null || data.walkForward().isEmpty()) {
      builder.append("<p>No walk-forward windows evaluated.</p>");
    } else {
      builder.append("<table><thead><tr><th>Window</th><th>Trades</th><th>PF</th><th>MaxDD</th></tr></thead><tbody>");
      for (WindowMetrics window : data.walkForward()) {
        builder
            .append("<tr><td>")
            .append(escape(window.windowId()))
            .append("</td><td>")
            .append(window.metrics().trades())
            .append("</td><td>")
            .append(format(window.metrics().profitFactor()))
            .append("</td><td>")
            .append(format(window.metrics().maxDrawdown()))
            .append("</td></tr>");
      }
      builder.append("</tbody></table>");
    }

    builder.append("<h2>Out-of-Sample Metrics</h2>");
    builder.append(renderMetricsTable(data.oosMetrics()));

    builder.append("<h2>Shadow Metrics</h2>");
    if (data.shadowMetrics() == null || data.shadowMetrics().isEmpty()) {
      builder.append("<p>Shadow forward evaluation pending.</p>");
    } else {
      builder.append(renderMetricsTable(data.shadowMetrics()));
    }

    builder.append("<div class=\"charts\">");
    builder
        .append("<h2>Charts</h2>")
        .append("<p>Price with trades, equity curve and drawdown charts.</p>")
        .append("<img src=\"price_with_trades.png\" alt=\"Price with trades\"/>")
        .append("<img src=\"equity_curve.png\" alt=\"Equity curve\"/>")
        .append("<img src=\"drawdown.png\" alt=\"Drawdown\"/>");
    builder.append("</div>");

    builder.append("</body></html>");
    return builder.toString();
  }

  private String renderMetricsTable(Map<String, Object> metrics) {
    if (metrics == null || metrics.isEmpty()) {
      return "<p>No metrics available.</p>";
    }
    StringBuilder builder = new StringBuilder();
    builder.append("<table><thead><tr><th>Metric</th><th>Value</th></tr></thead><tbody>");
    metrics.forEach(
        (key, value) ->
            builder
                .append("<tr><td>")
                .append(escape(key))
                .append("</td><td>")
                .append(escape(String.valueOf(value)))
                .append("</td></tr>"));
    builder.append("</tbody></table>");
    return builder.toString();
  }

  private String format(Object number) {
    if (number == null) {
      return "-";
    }
    if (number instanceof Number n) {
      return NUMBER_FORMAT.format(n.doubleValue());
    }
    return escape(number.toString());
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  public record ReportData(
      String runId,
      String symbol,
      RegimeTrend trend,
      String status,
      String note,
      Map<String, Object> oosMetrics,
      Map<String, Object> shadowMetrics,
      List<WindowMetrics> walkForward,
      Path reportDir) {}

  public record WindowMetrics(String windowId, MetricsSummary metrics) {}

  public record ReportFiles(Path html, Path pdf) {}
}
