package com.bottrading.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sizing")
public class SizingProperties {

  public enum SlippageModel {
    ATR,
    FIXEDBPS
  }

  private BigDecimal minNotionalBufferPct = BigDecimal.valueOf(1.5);
  private boolean icebergEnabled = false;
  private SlippageModel slippageModel = SlippageModel.ATR;
  private BigDecimal slippageFixedBps = BigDecimal.valueOf(2);

  public BigDecimal getMinNotionalBufferPct() {
    return minNotionalBufferPct;
  }

  public void setMinNotionalBufferPct(BigDecimal minNotionalBufferPct) {
    this.minNotionalBufferPct = minNotionalBufferPct;
  }

  public boolean isIcebergEnabled() {
    return icebergEnabled;
  }

  public void setIcebergEnabled(boolean icebergEnabled) {
    this.icebergEnabled = icebergEnabled;
  }

  public SlippageModel getSlippageModel() {
    return slippageModel;
  }

  public void setSlippageModel(SlippageModel slippageModel) {
    this.slippageModel = slippageModel;
  }

  public BigDecimal getSlippageFixedBps() {
    return slippageFixedBps;
  }

  public void setSlippageFixedBps(BigDecimal slippageFixedBps) {
    this.slippageFixedBps = slippageFixedBps;
  }
}
