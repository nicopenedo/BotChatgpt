package com.bottrading.research.nightly;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "research")
public class ResearchProperties {

  private Nightly nightly = new Nightly();

  public Nightly getNightly() {
    return nightly;
  }

  public void setNightly(Nightly nightly) {
    this.nightly = nightly;
  }

  public static class Nightly {
    private boolean enabled = false;
    private String startCron = "0 20 * * *";
    private String zone = "UTC";
    private Dataset dataset = new Dataset();
    private Ga ga = new Ga();
    private Gate gate = new Gate();
    private Reporting reporting = new Reporting();

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getStartCron() {
      return startCron;
    }

    public void setStartCron(String startCron) {
      this.startCron = startCron;
    }

    public String getZone() {
      return zone;
    }

    public void setZone(String zone) {
      this.zone = zone;
    }

    public Dataset getDataset() {
      return dataset;
    }

    public void setDataset(Dataset dataset) {
      this.dataset = dataset;
    }

    public Ga getGa() {
      return ga;
    }

    public void setGa(Ga ga) {
      this.ga = ga;
    }

    public Gate getGate() {
      return gate;
    }

    public void setGate(Gate gate) {
      this.gate = gate;
    }

    public Reporting getReporting() {
      return reporting;
    }

    public void setReporting(Reporting reporting) {
      this.reporting = reporting;
    }

    public static class Gate {
      private double epsilonPf = 0.10;
      private double maxddCapPct = 8.0;
      private int minTradesOos = 150;
      private int shadowMinTrades = 50;
      private double shadowPfDropTolerance = 0.15;
      private double pfBaseline = 1.0;

      public double getEpsilonPf() {
        return epsilonPf;
      }

      public void setEpsilonPf(double epsilonPf) {
        this.epsilonPf = epsilonPf;
      }

      public double getMaxddCapPct() {
        return maxddCapPct;
      }

      public void setMaxddCapPct(double maxddCapPct) {
        this.maxddCapPct = maxddCapPct;
      }

      public int getMinTradesOos() {
        return minTradesOos;
      }

      public void setMinTradesOos(int minTradesOos) {
        this.minTradesOos = minTradesOos;
      }

      public int getShadowMinTrades() {
        return shadowMinTrades;
      }

      public void setShadowMinTrades(int shadowMinTrades) {
        this.shadowMinTrades = shadowMinTrades;
      }

      public double getShadowPfDropTolerance() {
        return shadowPfDropTolerance;
      }

      public void setShadowPfDropTolerance(double shadowPfDropTolerance) {
        this.shadowPfDropTolerance = shadowPfDropTolerance;
      }

      public double getPfBaseline() {
        return pfBaseline;
      }

      public void setPfBaseline(double pfBaseline) {
        this.pfBaseline = pfBaseline;
      }
    }
  }

  public static class Dataset {
    private String symbol = "BTCUSDT";
    private String interval = "1m";
    private int historyDays = 180;
    private boolean useCache = true;

    public String getSymbol() {
      return symbol;
    }

    public void setSymbol(String symbol) {
      this.symbol = symbol;
    }

    public String getInterval() {
      return interval;
    }

    public void setInterval(String interval) {
      this.interval = interval;
    }

    public int getHistoryDays() {
      return historyDays;
    }

    public void setHistoryDays(int historyDays) {
      this.historyDays = historyDays;
    }

    public boolean isUseCache() {
      return useCache;
    }

    public void setUseCache(boolean useCache) {
      this.useCache = useCache;
    }
  }

  public static class Ga {
    private int population = 30;
    private int generations = 20;
    private long seed = 42L;
    private int minTrades = 150;
    private int minSamples = 100;
    private double complexityPenalty = 0.5;
    private int maxWorkers = Runtime.getRuntime().availableProcessors();
    private int trainDays = 90;
    private int validationDays = 30;
    private int testDays = 30;
    private BigDecimal slippageBps = BigDecimal.ZERO;
    private BigDecimal takerFeeBps = BigDecimal.ZERO;
    private BigDecimal makerFeeBps = BigDecimal.ZERO;
    private boolean useDynamicFees = false;

    public int getPopulation() {
      return population;
    }

    public void setPopulation(int population) {
      this.population = population;
    }

    public int getGenerations() {
      return generations;
    }

    public void setGenerations(int generations) {
      this.generations = generations;
    }

    public long getSeed() {
      return seed;
    }

    public void setSeed(long seed) {
      this.seed = seed;
    }

    public int getMinTrades() {
      return minTrades;
    }

    public void setMinTrades(int minTrades) {
      this.minTrades = minTrades;
    }

    public int getMinSamples() {
      return minSamples;
    }

    public void setMinSamples(int minSamples) {
      this.minSamples = minSamples;
    }

    public double getComplexityPenalty() {
      return complexityPenalty;
    }

    public void setComplexityPenalty(double complexityPenalty) {
      this.complexityPenalty = complexityPenalty;
    }

    public int getMaxWorkers() {
      return maxWorkers;
    }

    public void setMaxWorkers(int maxWorkers) {
      this.maxWorkers = maxWorkers;
    }

    public int getTrainDays() {
      return trainDays;
    }

    public void setTrainDays(int trainDays) {
      this.trainDays = trainDays;
    }

    public int getValidationDays() {
      return validationDays;
    }

    public void setValidationDays(int validationDays) {
      this.validationDays = validationDays;
    }

    public int getTestDays() {
      return testDays;
    }

    public void setTestDays(int testDays) {
      this.testDays = testDays;
    }

    public BigDecimal getSlippageBps() {
      return slippageBps;
    }

    public void setSlippageBps(BigDecimal slippageBps) {
      this.slippageBps = slippageBps;
    }

    public BigDecimal getTakerFeeBps() {
      return takerFeeBps;
    }

    public void setTakerFeeBps(BigDecimal takerFeeBps) {
      this.takerFeeBps = takerFeeBps;
    }

    public BigDecimal getMakerFeeBps() {
      return makerFeeBps;
    }

    public void setMakerFeeBps(BigDecimal makerFeeBps) {
      this.makerFeeBps = makerFeeBps;
    }

    public boolean isUseDynamicFees() {
      return useDynamicFees;
    }

    public void setUseDynamicFees(boolean useDynamicFees) {
      this.useDynamicFees = useDynamicFees;
    }
  }

  public static class Reporting {
    private String baseDir = "reports/nightly";
    private boolean includeCharts = true;

    public String getBaseDir() {
      return baseDir;
    }

    public void setBaseDir(String baseDir) {
      this.baseDir = baseDir;
    }

    public boolean isIncludeCharts() {
      return includeCharts;
    }

    public void setIncludeCharts(boolean includeCharts) {
      this.includeCharts = includeCharts;
    }
  }
}
