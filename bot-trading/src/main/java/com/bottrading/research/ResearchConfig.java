package com.bottrading.research;

import com.bottrading.research.backtest.BacktestEngine;
import com.bottrading.research.backtest.ReportWriter;
import com.bottrading.research.io.ChartExporter;
import com.bottrading.research.io.CsvWriter;
import com.bottrading.research.io.DataLoader;
import com.bottrading.research.io.JsonWriter;
import com.bottrading.research.io.KlineCache;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.strategy.StrategyFactory;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResearchConfig {

  @Bean
  public KlineCache klineCache() {
    return new KlineCache(Path.of("research-cache"));
  }

  @Bean
  public DataLoader dataLoader(BinanceClient binanceClient, KlineCache cache) {
    return new DataLoader(binanceClient, cache);
  }

  @Bean
  public CsvWriter csvWriter() {
    return new CsvWriter();
  }

  @Bean
  public JsonWriter jsonWriter() {
    return new JsonWriter();
  }

  @Bean
  public ChartExporter chartExporter(CsvWriter csvWriter) {
    return new ChartExporter(csvWriter);
  }

  @Bean
  public ReportWriter reportWriter(CsvWriter csvWriter, JsonWriter jsonWriter, ChartExporter chartExporter) {
    return new ReportWriter(csvWriter, jsonWriter, chartExporter);
  }

  @Bean
  public BacktestEngine backtestEngine(
      DataLoader dataLoader, StrategyFactory strategyFactory, ReportWriter reportWriter) {
    return new BacktestEngine(dataLoader, strategyFactory, reportWriter, BigDecimal.valueOf(10000));
  }
}
