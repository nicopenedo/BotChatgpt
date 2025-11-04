package com.bottrading.research.ga;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.BitmapEncoder.BitmapFormat;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.style.Styler.LegendPosition;

public class GaProgressReporter implements GaListener {

  private final Path csvPath;
  private final Path chartPath;
  private final boolean plotEnabled;
  private final List<Integer> generations = new ArrayList<>();
  private final List<Double> maxFitness = new ArrayList<>();
  private final List<Double> avgFitness = new ArrayList<>();
  private final List<Double> minFitness = new ArrayList<>();
  private final List<Double> diversity = new ArrayList<>();
  private final List<Double> maxProfitRisk = new ArrayList<>();
  private final List<Double> avgProfitRisk = new ArrayList<>();

  public GaProgressReporter(Path baseDir, String runId, boolean plotEnabled) {
    Path dir = baseDir.resolve(runId);
    this.csvPath = dir.resolve("ga_progress.csv");
    this.chartPath = dir.resolve("ga_progress.png");
    this.plotEnabled = plotEnabled;
  }

  @Override
  public void onGeneration(GenStats stats) {
    generations.add(stats.generation());
    maxFitness.add(stats.maxFitness());
    avgFitness.add(stats.avgFitness());
    minFitness.add(stats.minFitness());
    diversity.add(stats.diversity());
    maxProfitRisk.add(stats.maxProfitRiskRatio());
    avgProfitRisk.add(stats.averageProfitRiskRatio());
    try {
      appendCsv(stats);
      if (plotEnabled) {
        updateChart();
      }
    } catch (IOException ex) {
      // swallow reporting errors
    }
  }

  private void appendCsv(GenStats stats) throws IOException {
    if (csvPath.getParent() != null && !Files.exists(csvPath.getParent())) {
      Files.createDirectories(csvPath.getParent());
    }
    boolean writeHeader = !Files.exists(csvPath);
    try (OutputStreamWriter writer =
        new OutputStreamWriter(
            Files.newOutputStream(
                csvPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND),
            StandardCharsets.UTF_8)) {
      if (writeHeader) {
        writer.write(
            "gen,maxFitness,avgFitness,minFitness,diversity,maxProfitRiskRatio,avgProfitRiskRatio,bestGenomeSummary\n");
      }
      writer.write(
          String.format(
              Locale.US,
              "%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%s%n",
              stats.generation(),
              stats.maxFitness(),
              stats.avgFitness(),
              stats.minFitness(),
              stats.diversity(),
              stats.maxProfitRiskRatio(),
              stats.averageProfitRiskRatio(),
              quote(stats.bestGenomeSummary())));
    }
  }

  private void updateChart() throws IOException {
    XYChart chart =
        new XYChartBuilder()
            .width(1280)
            .height(720)
            .title("Progreso del Algoritmo Genético")
            .xAxisTitle("Generación")
            .yAxisTitle("Fitness")
            .build();
    chart.getStyler().setLegendPosition(LegendPosition.OutsideE);
    chart.getStyler().setDatePattern(null);
    chart.addSeries("Máximo Fitness", generations, maxFitness).setLineColor(new java.awt.Color(243, 156, 18));
    chart.addSeries("Fitness Promedio", generations, avgFitness).setLineColor(new java.awt.Color(241, 196, 15));
    chart.addSeries("Mínimo Fitness", generations, minFitness).setLineColor(new java.awt.Color(39, 174, 96));
    chart.addSeries("Diversidad Genética", generations, diversity).setLineColor(new java.awt.Color(52, 152, 219));
    chart.addSeries("maxProfitRiskRatio", generations, maxProfitRisk).setLineColor(new java.awt.Color(155, 89, 182));
    chart.addSeries("averageProfitRiskRatio", generations, avgProfitRisk).setLineColor(new java.awt.Color(187, 143, 206));
    if (chartPath.getParent() != null && !Files.exists(chartPath.getParent())) {
      Files.createDirectories(chartPath.getParent());
    }
    BitmapEncoder.saveBitmapWithDPI(
        chart, chartPath.toString().replaceFirst("\\.png$", ""), BitmapFormat.PNG, 150);
  }

  private String quote(String value) {
    if (value == null) {
      return "";
    }
    return "\"" + value.replace("\"", "'") + "\"";
  }
}
