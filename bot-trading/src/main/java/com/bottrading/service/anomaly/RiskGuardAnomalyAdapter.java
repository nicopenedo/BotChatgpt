package com.bottrading.service.anomaly;

import com.bottrading.service.risk.RiskFlag;
import com.bottrading.service.risk.RiskGuard;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RiskGuardAnomalyAdapter implements AnomalyRiskAdapter {

  private final RiskGuard riskGuard;

  public RiskGuardAnomalyAdapter(RiskGuard riskGuard) {
    this.riskGuard = riskGuard;
  }

  @Override
  public void applyPause(RiskFlag flag, Duration duration, String detail) {
    riskGuard.applyTemporaryFlag(flag, duration, detail);
  }
}
