package com.bottrading.research.io;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.model.dto.Kline;
import com.bottrading.research.backtest.EquityPoint;
import com.bottrading.research.backtest.MetricsSummary;
import com.bottrading.research.backtest.TradeRecord;
import com.bottrading.strategy.SignalSide;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChartExporterTest {

  @TempDir Path tempDir;

  @Test
  void exportsChartsAndCsvArtifacts() throws IOException {
    ChartExporter exporter = new ChartExporter(new CsvWriter());

    List<Kline> klines =
        List.of(
            new Kline(
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-01T00:01:00Z"),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(105),
                BigDecimal.valueOf(99),
                BigDecimal.valueOf(104),
                BigDecimal.valueOf(1500)),
            new Kline(
                Instant.parse("2024-01-01T00:01:00Z"),
                Instant.parse("2024-01-01T00:02:00Z"),
                BigDecimal.valueOf(104),
                BigDecimal.valueOf(108),
                BigDecimal.valueOf(103),
                BigDecimal.valueOf(106),
                BigDecimal.valueOf(1700)));

    TradeRecord trade =
        new TradeRecord(
            Instant.parse("2024-01-01T00:00:30Z"),
            BigDecimal.valueOf(101),
            Instant.parse("2024-01-01T00:01:30Z"),
            BigDecimal.valueOf(107),
            BigDecimal.valueOf(0.5),
            BigDecimal.valueOf(3.0),
            true,
            SignalSide.BUY,
            "entry",
            "exit",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            null,
            null);

    List<EquityPoint> equity =
        List.of(
            new EquityPoint(Instant.parse("2024-01-01T00:00:00Z"), BigDecimal.valueOf(1000)),
            new EquityPoint(Instant.parse("2024-01-01T00:01:00Z"), BigDecimal.valueOf(1015)),
            new EquityPoint(Instant.parse("2024-01-01T00:02:00Z"), BigDecimal.valueOf(990)));

    MetricsSummary metrics =
        new MetricsSummary(
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.valueOf(5),
            BigDecimal.valueOf(1.5),
            BigDecimal.valueOf(0.55),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            3,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO);

    exporter.exportAll(tempDir, "BTCUSDT", "run-1", klines, List.of(trade), equity, metrics);

    assertThat(tempDir.resolve("price_with_trades.png")).exists();
    assertThat(tempDir.resolve("equity_curve.png")).exists();
    assertThat(tempDir.resolve("drawdown.png")).exists();

    Path equityCsv = tempDir.resolve("equity.csv");
    Path drawdownCsv = tempDir.resolve("drawdown.csv");
    assertThat(equityCsv).exists();
    assertThat(drawdownCsv).exists();

    List<String> equityLines = Files.readAllLines(equityCsv);
    List<String> drawdownLines = Files.readAllLines(drawdownCsv);
    assertThat(equityLines).isNotEmpty();
    assertThat(equityLines.getFirst()).isEqualTo("timestamp,equity");
    assertThat(drawdownLines).isNotEmpty();
    assertThat(drawdownLines.getFirst()).isEqualTo("timestamp,drawdown_pct");
  }
}
