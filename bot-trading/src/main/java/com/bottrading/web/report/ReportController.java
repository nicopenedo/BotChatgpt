package com.bottrading.web.report;

import com.bottrading.model.dto.report.AnnotationDto;
import com.bottrading.model.dto.report.HeatmapResponse;
import com.bottrading.model.dto.report.PnlAttributionGroup;
import com.bottrading.model.dto.report.PnlAttributionMetrics;
import com.bottrading.model.dto.report.PnlAttributionSymbolStats;
import com.bottrading.model.dto.report.SummaryBucket;
import com.bottrading.model.dto.report.TimePoint;
import com.bottrading.model.dto.report.TradeDto;
import com.bottrading.service.report.ReportService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

  private final ReportService reportService;

  public ReportController(ReportService reportService) {
    this.reportService = reportService;
  }

  @GetMapping("/trades")
  @PreAuthorize("hasRole('VIEWER')")
  public Page<TradeDto> trades(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String side,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(defaultValue = "executedAt,desc") String sort) {
    Pageable pageable = PageRequest.of(page, size, parseSort(sort));
    return reportService.findTrades(
        symbol, parseInstant(from), parseInstant(to), side, status, pageable);
  }

  @GetMapping("/summary")
  @PreAuthorize("hasRole('VIEWER')")
  public List<SummaryBucket> summary(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "day") String groupBy) {
    return reportService.summarize(symbol, parseInstant(from), parseInstant(to), groupBy);
  }

  @GetMapping("/equity")
  @PreAuthorize("hasRole('VIEWER')")
  public List<TimePoint> equity(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    return reportService.equityCurve(symbol, parseInstant(from), parseInstant(to));
  }

  @GetMapping("/drawdown")
  @PreAuthorize("hasRole('VIEWER')")
  public List<TimePoint> drawdown(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    return reportService.drawdownCurve(symbol, parseInstant(from), parseInstant(to));
  }

  @GetMapping("/annotations")
  @PreAuthorize("hasRole('VIEWER')")
  public List<AnnotationDto> annotations(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "false") boolean includeAdvanced) {
    return reportService.annotations(
        symbol, parseInstant(from), parseInstant(to), includeAdvanced);
  }

  @GetMapping("/heatmap")
  @PreAuthorize("hasRole('VIEWER')")
  public HeatmapResponse heatmap(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "hour") String bucket) {
    return reportService.heatmap(symbol, parseInstant(from), parseInstant(to), bucket);
  }

  @GetMapping("/pnl-attr/breakdown")
  @PreAuthorize("hasRole('VIEWER')")
  public List<PnlAttributionGroup> pnlAttributionBreakdown(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    return reportService.pnlAttributionBreakdown(symbol, parseInstant(from), parseInstant(to));
  }

  @GetMapping("/pnl-attr/stats")
  @PreAuthorize("hasRole('VIEWER')")
  public List<PnlAttributionSymbolStats> pnlAttributionStats(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    return reportService.pnlAttributionSymbolStats(symbol, parseInstant(from), parseInstant(to));
  }

  @GetMapping("/pnl-attr/metrics")
  @PreAuthorize("hasRole('VIEWER')")
  public PnlAttributionMetrics pnlAttributionMetrics(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    return reportService.pnlAttributionMetrics(symbol, parseInstant(from), parseInstant(to));
  }

  @GetMapping(value = "/heatmap/export.csv", produces = "text/csv")
  @PreAuthorize("hasRole('VIEWER')")
  public ResponseEntity<Resource> heatmapCsv(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "hour") String bucket) {
    HeatmapResponse heatmap = reportService.heatmap(symbol, parseInstant(from), parseInstant(to), bucket);
    return buildCsvResponse("heatmap.csv", CsvWriter.heatmap(heatmap));
  }

  @GetMapping(value = "/trades/export.csv", produces = "text/csv")
  @PreAuthorize("hasRole('VIEWER')")
  public ResponseEntity<Resource> exportTradesCsv(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String side,
      @RequestParam(required = false) String status) {
    List<TradeDto> trades =
        reportService
            .findTrades(symbol, parseInstant(from), parseInstant(to), side, status, PageRequest.of(0, 10_000))
            .getContent();
    return buildCsvResponse("trades.csv", CsvWriter.trades(trades));
  }

  @GetMapping(value = "/trades/export.json", produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('VIEWER')")
  public List<TradeDto> exportTradesJson(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String side,
      @RequestParam(required = false) String status) {
    return reportService
        .findTrades(symbol, parseInstant(from), parseInstant(to), side, status, PageRequest.of(0, 10_000))
        .getContent();
  }

  @GetMapping(value = "/summary/export.csv", produces = "text/csv")
  @PreAuthorize("hasRole('VIEWER')")
  public ResponseEntity<Resource> exportSummaryCsv(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "day") String groupBy) {
    return buildCsvResponse(
        "summary.csv",
        CsvWriter.summary(reportService.summarize(symbol, parseInstant(from), parseInstant(to), groupBy)));
  }

  @GetMapping(value = "/summary/export.json", produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('VIEWER')")
  public List<SummaryBucket> exportSummaryJson(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "day") String groupBy) {
    return reportService.summarize(symbol, parseInstant(from), parseInstant(to), groupBy);
  }

  @GetMapping(value = "/equity/export.csv", produces = "text/csv")
  @PreAuthorize("hasRole('VIEWER')")
  public ResponseEntity<Resource> exportEquityCsv(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    return buildCsvResponse(
        "equity.csv", CsvWriter.points(reportService.equityCurve(symbol, parseInstant(from), parseInstant(to))));
  }

  @GetMapping(value = "/equity/export.json", produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('VIEWER')")
  public List<TimePoint> exportEquityJson(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    return reportService.equityCurve(symbol, parseInstant(from), parseInstant(to));
  }

  private Instant parseInstant(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException("Invalid timestamp: " + value, ex);
    }
  }

  private Sort parseSort(String value) {
    if (!StringUtils.hasText(value)) {
      return Sort.by(Sort.Order.desc("executedAt"));
    }
    String[] tokens = value.split(",");
    if (tokens.length == 1) {
      return Sort.by(tokens[0]);
    }
    return Sort.by(new Sort.Order(Sort.Direction.fromOptionalString(tokens[1]).orElse(Sort.Direction.DESC), tokens[0]));
  }

  private ResponseEntity<Resource> buildCsvResponse(String filename, String csv) {
    ByteArrayInputStream bais = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    InputStreamResource resource = new InputStreamResource(bais);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
        .contentType(MediaType.parseMediaType("text/csv"))
        .body(resource);
  }

  private static class CsvWriter {
    private static final String NEWLINE = "\n";

    static String trades(List<TradeDto> trades) {
      StringBuilder sb = new StringBuilder();
      sb.append(
              "id,executedAt,symbol,side,price,quantity,fee,feesBps,pnl,pnlNet,signalEdge,pnlR,slippageBps,slippageCost,"
                  + "timingBps,timingCost,clientOrderId,decisionKey,decisionNote")
          .append(NEWLINE);
      for (TradeDto trade : trades) {
        sb.append(trade.id()).append(',')
            .append(toString(trade.executedAt())).append(',')
            .append(nullToEmpty(trade.symbol())).append(',')
            .append(nullToEmpty(trade.side())).append(',')
            .append(toString(trade.price())).append(',')
            .append(toString(trade.quantity())).append(',')
            .append(toString(trade.fee())).append(',')
            .append(toString(trade.feesBps())).append(',')
            .append(toString(trade.pnl())).append(',')
            .append(toString(trade.pnlNet())).append(',')
            .append(toString(trade.signalEdge())).append(',')
            .append(toString(trade.pnlR())).append(',')
            .append(toString(trade.slippageBps())).append(',')
            .append(toString(trade.slippageCost())).append(',')
            .append(toString(trade.timingBps())).append(',')
            .append(toString(trade.timingCost())).append(',')
            .append(nullToEmpty(trade.clientOrderId())).append(',')
            .append(nullToEmpty(trade.decisionKey())).append(',')
            .append(escapeCsv(trade.decisionNote()))
            .append(NEWLINE);
      }
      return sb.toString();
    }

    static String summary(List<SummaryBucket> buckets) {
      StringBuilder sb = new StringBuilder();
      sb.append(
              "label,periodStart,periodEnd,trades,wins,losses,grossPnL,netPnL,fees,winRate,profitFactor,maxDrawdown,sharpe,sortino")
          .append(NEWLINE);
      for (SummaryBucket bucket : buckets) {
        sb.append(nullToEmpty(bucket.label())).append(',')
            .append(toString(bucket.periodStart())).append(',')
            .append(toString(bucket.periodEnd())).append(',')
            .append(bucket.trades()).append(',')
            .append(bucket.wins()).append(',')
            .append(bucket.losses()).append(',')
            .append(toString(bucket.grossPnL())).append(',')
            .append(toString(bucket.netPnL())).append(',')
            .append(toString(bucket.fees())).append(',')
            .append(toString(bucket.winRate())).append(',')
            .append(toString(bucket.profitFactor())).append(',')
            .append(toString(bucket.maxDrawdown())).append(',')
            .append(toString(bucket.sharpe())).append(',')
            .append(toString(bucket.sortino()))
            .append(NEWLINE);
      }
      return sb.toString();
    }

    static String points(List<TimePoint> points) {
      StringBuilder sb = new StringBuilder();
      sb.append("ts,value").append(NEWLINE);
      for (TimePoint point : points) {
        sb.append(toString(point.ts())).append(',').append(toString(point.value())).append(NEWLINE);
      }
      return sb.toString();
    }

    static String heatmap(HeatmapResponse heatmap) {
      StringBuilder sb = new StringBuilder();
      sb.append("bucketX,bucketY,trades,netPnl,winRate").append(NEWLINE);
      if (heatmap != null && heatmap.cells() != null) {
        for (var cell : heatmap.cells()) {
          sb.append(cell.x())
              .append(',')
              .append(cell.y())
              .append(',')
              .append(cell.trades())
              .append(',')
              .append(toString(cell.netPnl()))
              .append(',')
              .append(toString(cell.winRate()))
              .append(NEWLINE);
        }
      }
      return sb.toString();
    }

    private static String toString(Object value) {
      return value == null ? "" : value.toString();
    }

    private static String nullToEmpty(String value) {
      return value == null ? "" : value;
    }

    private static String escapeCsv(String value) {
      if (value == null) {
        return "";
      }
      if (value.contains(",") || value.contains("\"")) {
        return '"' + value.replace("\"", """") + '"';
      }
      return value;
    }
  }
}
