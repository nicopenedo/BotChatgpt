package com.bottrading.config.props;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "bandit")
public class BanditProps {

  @NotNull private Strategy strategy = Strategy.THOMPSON;

  @NotNull
  @DecimalMin(value = "0.0", inclusive = true)
  @DecimalMax(value = "1.0", inclusive = true)
  private BigDecimal explorationPct = BigDecimal.valueOf(0.10);

  @NotEmpty
  private List<String> contextFeatures =
      new ArrayList<>(List.of("regime", "volatility", "hour", "spread", "slippage"));

  public Strategy getStrategy() {
    return strategy;
  }

  public void setStrategy(Strategy strategy) {
    this.strategy = strategy;
  }

  public BigDecimal getExplorationPct() {
    return explorationPct;
  }

  public void setExplorationPct(BigDecimal explorationPct) {
    this.explorationPct = explorationPct;
  }

  public List<String> getContextFeatures() {
    return contextFeatures;
  }

  public void setContextFeatures(List<String> contextFeatures) {
    if (contextFeatures == null || contextFeatures.isEmpty()) {
      this.contextFeatures = new ArrayList<>();
    } else {
      this.contextFeatures = new ArrayList<>(contextFeatures);
    }
  }

  public enum Strategy {
    THOMPSON,
    UCB
  }
}
