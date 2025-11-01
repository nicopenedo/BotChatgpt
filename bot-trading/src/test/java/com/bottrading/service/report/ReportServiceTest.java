package com.bottrading.service.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.model.dto.report.AnnotationDto;
import com.bottrading.model.dto.report.HeatmapResponse;
import com.bottrading.model.dto.report.SummaryBucket;
import com.bottrading.model.dto.report.TradeDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=true")
class ReportServiceTest {

  @Autowired private ReportService reportService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setup() {
    jdbcTemplate.update("DELETE FROM trades");
    jdbcTemplate.update("DELETE FROM managed_orders");
    jdbcTemplate.update("DELETE FROM positions");
    jdbcTemplate.update(
        "INSERT INTO positions (id, symbol, side, entry_price, qty_init, qty_remaining, status, opened_at, closed_at) "
            + "VALUES (1, 'BTCUSDT', 'BUY', 100, 1, 0, 'CLOSED', ?, ?)",
        Instant.parse("2024-01-01T00:00:00Z"),
        Instant.parse("2024-01-02T00:00:00Z"));
    jdbcTemplate.update(
        "INSERT INTO trades (id, position_id, order_id, symbol, price, quantity, fee, side, executed_at) "
            + "VALUES (1, 1, 'O1', 'BTCUSDT', 110, 1, 0.1, 'SELL', ?)",
        Instant.parse("2024-01-01T10:00:00Z"));
    jdbcTemplate.update(
        "INSERT INTO managed_orders (id, position_id, client_order_id, type, side, price, quantity, status, created_at) "
            + "VALUES (1, 1, 'CO1', 'TAKE_PROFIT', 'SELL', 110, 1, 'FILLED', ?)",
        Instant.parse("2024-01-01T09:00:00Z"));
  }

  @Test
  void findTradesReturnsPnL() {
    List<TradeDto> trades =
        reportService
            .findTrades(
                "BTCUSDT",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-02T00:00:00Z"),
                null,
                null,
                org.springframework.data.domain.PageRequest.of(0, 10))
            .getContent();
    assertThat(trades).hasSize(1);
    assertThat(trades.get(0).pnl()).isGreaterThan(BigDecimal.ZERO);
  }

  @Test
  void summaryAggregatesRange() {
    List<SummaryBucket> summary =
        reportService.summarize(
            "BTCUSDT",
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-01-02T00:00:00Z"),
            "day");
    assertThat(summary).isNotEmpty();
    SummaryBucket bucket = summary.get(0);
    assertThat(bucket.trades()).isEqualTo(1);
    assertThat(bucket.netPnL()).isNotNull();
  }

  @Test
  void annotationsIncludeTakeProfit() {
    List<AnnotationDto> annotations =
        reportService.annotations(
            "BTCUSDT",
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-01-02T00:00:00Z"),
            true);
    assertThat(annotations).extracting(AnnotationDto::type).contains("TP");
  }

  @Test
  void heatmapProducesCells() {
    HeatmapResponse heatmap =
        reportService.heatmap(
            "BTCUSDT",
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-01-02T00:00:00Z"),
            "hour");
    assertThat(heatmap.cells()).isNotEmpty();
  }
}
