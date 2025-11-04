package com.bottrading.web.report;

import com.bottrading.model.dto.report.AtrBandPoint;
import com.bottrading.model.dto.report.IndicatorPoint;
import com.bottrading.model.dto.report.SupertrendPoint;
import com.bottrading.service.market.MarketDataService;
import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
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
@RequestMapping
public class IndicatorController {

  private final MarketDataService marketDataService;

  public IndicatorController(MarketDataService marketDataService) {
    this.marketDataService = marketDataService;
  }

  @GetMapping("/api/market/vwap")
  @PreAuthorize("hasRole('VIEWER')")
  public List<IndicatorPoint> vwap(
      @RequestParam String symbol,
      @RequestParam(defaultValue = "1m") String interval,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String anchorTs) {
    return marketDataService.vwap(
        symbol, interval, parseInstant(from), parseInstant(to), parseInstant(anchorTs));
  }

  @GetMapping(value = "/api/market/vwap/export.csv", produces = "text/csv")
  @PreAuthorize("hasRole('VIEWER')")
  public ResponseEntity<Resource> vwapCsv(
      @RequestParam String symbol,
      @RequestParam(defaultValue = "1m") String interval,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String anchorTs) {
    List<IndicatorPoint> points =
        vwap(symbol, interval, from, to, anchorTs);
    return csv("vwap.csv", IndicatorCsv.points(points));
  }

  @GetMapping("/api/indicators/atr-bands")
  @PreAuthorize("hasRole('VIEWER')")
  public List<AtrBandPoint> atr(
      @RequestParam String symbol,
      @RequestParam(defaultValue = "1m") String interval,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "14") int period,
      @RequestParam(defaultValue = "1.0") double mult) {
    return marketDataService.atrBands(
        symbol,
        interval,
        parseInstant(from),
        parseInstant(to),
        period,
        BigDecimal.valueOf(mult));
  }

  @GetMapping(value = "/api/indicators/atr-bands/export.csv", produces = "text/csv")
  @PreAuthorize("hasRole('VIEWER')")
  public ResponseEntity<Resource> atrCsv(
      @RequestParam String symbol,
      @RequestParam(defaultValue = "1m") String interval,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "14") int period,
      @RequestParam(defaultValue = "1.0") double mult) {
    return csv(
        "atr-bands.csv",
        IndicatorCsv.atr(
            atr(symbol, interval, from, to, period, mult)));
  }

  @GetMapping("/api/indicators/supertrend")
  @PreAuthorize("hasRole('VIEWER')")
  public List<SupertrendPoint> supertrend(
      @RequestParam String symbol,
      @RequestParam(defaultValue = "1m") String interval,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "14") int atrPeriod,
      @RequestParam(defaultValue = "3.0") double multiplier) {
    return marketDataService.supertrend(
        symbol,
        interval,
        parseInstant(from),
        parseInstant(to),
        atrPeriod,
        BigDecimal.valueOf(multiplier));
  }

  @GetMapping(value = "/api/indicators/supertrend/export.csv", produces = "text/csv")
  @PreAuthorize("hasRole('VIEWER')")
  public ResponseEntity<Resource> supertrendCsv(
      @RequestParam String symbol,
      @RequestParam(defaultValue = "1m") String interval,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "14") int atrPeriod,
      @RequestParam(defaultValue = "3.0") double multiplier) {
    return csv(
        "supertrend.csv",
        IndicatorCsv.supertrend(
            supertrend(symbol, interval, from, to, atrPeriod, multiplier)));
  }

  private ResponseEntity<Resource> csv(String name, String payload) {
    InputStreamResource resource =
        new InputStreamResource(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + name)
        .body(resource);
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

  private static class IndicatorCsv {
    private static final String NEWLINE = "\n";

    static String points(List<IndicatorPoint> points) {
      StringBuilder sb = new StringBuilder("ts,value").append(NEWLINE);
      for (IndicatorPoint point : points) {
        sb.append(point.ts()).append(",").append(point.value()).append(NEWLINE);
      }
      return sb.toString();
    }

    static String atr(List<AtrBandPoint> points) {
      StringBuilder sb = new StringBuilder("ts,mid,upper,lower").append(NEWLINE);
      for (AtrBandPoint point : points) {
        sb.append(point.ts())
            .append(",")
            .append(point.mid())
            .append(",")
            .append(point.upper())
            .append(",")
            .append(point.lower())
            .append(NEWLINE);
      }
      return sb.toString();
    }

    static String supertrend(List<SupertrendPoint> points) {
      StringBuilder sb = new StringBuilder("ts,trend,line").append(NEWLINE);
      for (SupertrendPoint point : points) {
        sb.append(point.ts())
            .append(",")
            .append(point.trend())
            .append(",")
            .append(point.line())
            .append(NEWLINE);
      }
      return sb.toString();
    }
  }
}
