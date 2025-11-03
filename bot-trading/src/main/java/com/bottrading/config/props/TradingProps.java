package com.bottrading.config.props;

import java.math.BigDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "trading")
public class TradingProps {

  private Mode mode = Mode.SHADOW;

  @Valid @NotNull private Risk risk = new Risk();

  public Mode getMode() {
    return mode;
  }

  public void setMode(Mode mode) {
    this.mode = mode;
  }

  public Risk getRisk() {
    return risk;
  }

  public void setRisk(Risk risk) {
    this.risk = risk;
  }

  public enum Mode {
    SHADOW,
    LIVE
  }

  public static class Risk {

    private boolean globalPause = true;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @DecimalMax(value = "20.0", inclusive = true)
    private BigDecimal maxDailyDrawdownPct = BigDecimal.valueOf(3);

    @PositiveOrZero private int maxConcurrentPositions = 2;

    @PositiveOrZero private int maxDailyTrades = 10;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @DecimalMax(value = "1.0", inclusive = true)
    private BigDecimal canaryPct = BigDecimal.valueOf(0.10);

    public boolean isGlobalPause() {
      return globalPause;
    }

    public void setGlobalPause(boolean globalPause) {
      this.globalPause = globalPause;
    }

    public BigDecimal getMaxDailyDrawdownPct() {
      return maxDailyDrawdownPct;
    }

    public void setMaxDailyDrawdownPct(BigDecimal maxDailyDrawdownPct) {
      this.maxDailyDrawdownPct = maxDailyDrawdownPct;
    }

    public int getMaxConcurrentPositions() {
      return maxConcurrentPositions;
    }

    public void setMaxConcurrentPositions(int maxConcurrentPositions) {
      this.maxConcurrentPositions = maxConcurrentPositions;
    }

    public int getMaxDailyTrades() {
      return maxDailyTrades;
    }

    public void setMaxDailyTrades(int maxDailyTrades) {
      this.maxDailyTrades = maxDailyTrades;
    }

    public BigDecimal getCanaryPct() {
      return canaryPct;
    }

    public void setCanaryPct(BigDecimal canaryPct) {
      this.canaryPct = canaryPct;
    }
  }
}
