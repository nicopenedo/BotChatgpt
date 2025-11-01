package com.bottrading.research.backtest.realistic;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** Configuration container for the realistic execution backtester. */
public class RealisticBacktestConfig {

  @JsonProperty("backtest.exec")
  private ExecutionConfig execution = new ExecutionConfig();

  @JsonProperty("backtest.lob")
  private LobConfig lob = new LobConfig();

  @JsonProperty("backtest.tca")
  private TcaConfig tca = new TcaConfig();

  public ExecutionConfig execution() {
    return execution;
  }

  public LobConfig lob() {
    return lob;
  }

  public TcaConfig tca() {
    return tca;
  }

  public static class ExecutionConfig {

    private ExecutionMode mode = ExecutionMode.LIMIT;
    private LimitConfig limit = new LimitConfig();
    private MarketConfig market = new MarketConfig();
    private TwapConfig twap = new TwapConfig();
    private PovConfig pov = new PovConfig();

    @JsonProperty("limit.ttlMs")
    public void setLimitTtl(long ttl) {
      limit.ttlMs = ttl;
    }

    @JsonProperty("limit.bufferBps")
    public void setLimitBuffer(BigDecimal buffer) {
      limit.bufferBps = buffer;
    }

    @JsonProperty("limit.latencyMs")
    public void setLimitLatency(long latency) {
      limit.latencyMs = latency;
    }

    @JsonProperty("limit.fallbackToMarket")
    public void setFallback(boolean fallback) {
      limit.fallbackToMarket = fallback;
    }

    @JsonProperty("market.baseSlippageBps")
    public void setMarketSlippage(BigDecimal slippage) {
      market.baseSlippageBps = slippage;
    }

    @JsonProperty("twap.slices")
    public void setTwapSlices(int slices) {
      twap.slices = slices;
    }

    @JsonProperty("pov.targetPct")
    public void setPovTarget(BigDecimal pct) {
      pov.targetParticipation = pct;
    }

    public ExecutionMode mode() {
      return mode;
    }

    public void setMode(ExecutionMode mode) {
      this.mode = mode;
    }

    public LimitConfig limit() {
      return limit;
    }

    public MarketConfig market() {
      return market;
    }

    public TwapConfig twap() {
      return twap;
    }

    public PovConfig pov() {
      return pov;
    }
  }

  public static class LimitConfig {
    private long ttlMs = 3000;
    private long latencyMs = 80;
    private BigDecimal bufferBps = BigDecimal.ONE;
    private boolean fallbackToMarket = true;

    public long ttlMs() {
      return ttlMs;
    }

    public void setLimitTtl(long ttlMs) {
      this.ttlMs = ttlMs;
    }

    public long latencyMs() {
      return latencyMs;
    }

    public void setLimitLatency(long latencyMs) {
      this.latencyMs = latencyMs;
    }

    public BigDecimal bufferBps() {
      return bufferBps;
    }

    public void setLimitBuffer(BigDecimal bufferBps) {
      this.bufferBps = bufferBps;
    }

    public boolean fallbackToMarket() {
      return fallbackToMarket;
    }

    public void setFallbackToMarket(boolean fallbackToMarket) {
      this.fallbackToMarket = fallbackToMarket;
    }
  }

  public static class MarketConfig {
    private BigDecimal baseSlippageBps = BigDecimal.valueOf(1.0);
    private BigDecimal spreadWeight = BigDecimal.valueOf(0.5);
    private BigDecimal hourWeight = BigDecimal.valueOf(0.2);
    private BigDecimal volumeWeight = BigDecimal.valueOf(0.3);

    public BigDecimal baseSlippageBps() {
      return baseSlippageBps;
    }

    public BigDecimal spreadWeight() {
      return spreadWeight;
    }

    public BigDecimal hourWeight() {
      return hourWeight;
    }

    public BigDecimal volumeWeight() {
      return volumeWeight;
    }
  }

  public static class TwapConfig {
    private int slices = 3;

    public int slices() {
      return slices;
    }

    public void setTwapSlices(int slices) {
      this.slices = slices;
    }
  }

  public static class PovConfig {
    private BigDecimal targetParticipation = BigDecimal.valueOf(0.05);

    public BigDecimal targetParticipation() {
      return targetParticipation;
    }

    public void setPovTarget(BigDecimal targetParticipation) {
      this.targetParticipation = targetParticipation;
    }
  }

  public static class LobConfig {
    private DepthModel depthModel = DepthModel.SIMPLE;
    private Map<String, DepthParameters> depthPerSymbol = new HashMap<>();

    @JsonProperty("depthModel")
    public DepthModel depthModel() {
      return depthModel;
    }

    public void setDepthModel(DepthModel depthModel) {
      this.depthModel = depthModel;
    }

    public Map<String, DepthParameters> depthPerSymbol() {
      return depthPerSymbol;
    }

    public void setDepthPerSymbol(Map<String, DepthParameters> depthPerSymbol) {
      this.depthPerSymbol = depthPerSymbol;
    }
  }

  public static class DepthParameters {
    private BigDecimal advQty = BigDecimal.valueOf(1000);
    private BigDecimal baseDepth = BigDecimal.valueOf(300);
    private BigDecimal shape = BigDecimal.valueOf(1.5);

    public BigDecimal advQty() {
      return advQty;
    }

    public void setAdvQty(BigDecimal advQty) {
      this.advQty = advQty;
    }

    public BigDecimal baseDepth() {
      return baseDepth;
    }

    public void setBaseDepth(BigDecimal baseDepth) {
      this.baseDepth = baseDepth;
    }

    public BigDecimal shape() {
      return shape;
    }

    public void setShape(BigDecimal shape) {
      this.shape = shape;
    }
  }

  public static class TcaConfig {
    private BigDecimal spreadWeight = BigDecimal.valueOf(0.6);
    private BigDecimal hourWeight = BigDecimal.valueOf(0.3);
    private BigDecimal quantityWeight = BigDecimal.valueOf(0.5);
    private BigDecimal volumeWeight = BigDecimal.valueOf(0.2);

    public BigDecimal spreadWeight() {
      return spreadWeight;
    }

    public void setSpreadWeight(BigDecimal spreadWeight) {
      this.spreadWeight = spreadWeight;
    }

    public BigDecimal hourWeight() {
      return hourWeight;
    }

    public void setHourWeight(BigDecimal hourWeight) {
      this.hourWeight = hourWeight;
    }

    public BigDecimal quantityWeight() {
      return quantityWeight;
    }

    public void setQuantityWeight(BigDecimal quantityWeight) {
      this.quantityWeight = quantityWeight;
    }

    public BigDecimal volumeWeight() {
      return volumeWeight;
    }

    public void setVolumeWeight(BigDecimal volumeWeight) {
      this.volumeWeight = volumeWeight;
    }
  }

  public enum ExecutionMode {
    LIMIT,
    MARKET,
    TWAP,
    POV
  }

  public enum DepthModel {
    SIMPLE,
    CALIBRATED
  }
}
