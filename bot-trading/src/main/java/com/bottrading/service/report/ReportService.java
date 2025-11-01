package com.bottrading.service.report;

import com.bottrading.model.dto.report.AnnotationDto;
import com.bottrading.model.dto.report.HeatmapResponse;
import com.bottrading.model.dto.report.SummaryBucket;
import com.bottrading.model.dto.report.TimePoint;
import com.bottrading.model.dto.report.TradeDto;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReportService {

  Page<TradeDto> findTrades(
      String symbol,
      Instant from,
      Instant to,
      String side,
      String status,
      Pageable pageable);

  List<SummaryBucket> summarize(String symbol, Instant from, Instant to, String groupBy);

  List<TimePoint> equityCurve(String symbol, Instant from, Instant to);

  List<TimePoint> drawdownCurve(String symbol, Instant from, Instant to);

  List<AnnotationDto> annotations(String symbol, Instant from, Instant to, boolean includeAdvanced);

  HeatmapResponse heatmap(String symbol, Instant from, Instant to, String bucket);
}
