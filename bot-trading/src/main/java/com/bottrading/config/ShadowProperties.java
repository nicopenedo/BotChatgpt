package com.bottrading.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shadow")
public class ShadowProperties {

  private boolean enabled = true;
  private BigDecimal slippageBps = BigDecimal.valueOf(2);
  private BigDecimal divergencePctThreshold = BigDecimal.valueOf(15);
  private int divergenceMinTrades = 10;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public BigDecimal getSlippageBps() {
    return slippageBps;
  }

  public void setSlippageBps(BigDecimal slippageBps) {
    this.slippageBps = slippageBps;
  }

  public BigDecimal getDivergencePctThreshold() {
    return divergencePctThreshold;
  }

  public void setDivergencePctThreshold(BigDecimal divergencePctThreshold) {
    this.divergencePctThreshold = divergencePctThreshold;
  }

  public int getDivergenceMinTrades() {
    return divergenceMinTrades;
  }

  public void setDivergenceMinTrades(int divergenceMinTrades) {
    this.divergenceMinTrades = divergenceMinTrades;
  }
}
