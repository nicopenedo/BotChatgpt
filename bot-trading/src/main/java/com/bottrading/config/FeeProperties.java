package com.bottrading.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fees")
public class FeeProperties {

  private int cacheMinutes = 30;
  private boolean payWithBnb = true;
  private BigDecimal bnbMinDaysBuffer = BigDecimal.valueOf(7);
  private BigDecimal bnbMinTopupBnb = BigDecimal.valueOf(0.05);
  private BigDecimal bnbMaxTopupBnb = BigDecimal.valueOf(0.2);

  public int getCacheMinutes() {
    return cacheMinutes;
  }

  public void setCacheMinutes(int cacheMinutes) {
    this.cacheMinutes = cacheMinutes;
  }

  public boolean isPayWithBnb() {
    return payWithBnb;
  }

  public void setPayWithBnb(boolean payWithBnb) {
    this.payWithBnb = payWithBnb;
  }

  public BigDecimal getBnbMinDaysBuffer() {
    return bnbMinDaysBuffer;
  }

  public void setBnbMinDaysBuffer(BigDecimal bnbMinDaysBuffer) {
    this.bnbMinDaysBuffer = bnbMinDaysBuffer;
  }

  public BigDecimal getBnbMinTopupBnb() {
    return bnbMinTopupBnb;
  }

  public void setBnbMinTopupBnb(BigDecimal bnbMinTopupBnb) {
    this.bnbMinTopupBnb = bnbMinTopupBnb;
  }

  public BigDecimal getBnbMaxTopupBnb() {
    return bnbMaxTopupBnb;
  }

  public void setBnbMaxTopupBnb(BigDecimal bnbMaxTopupBnb) {
    this.bnbMaxTopupBnb = bnbMaxTopupBnb;
  }
}
