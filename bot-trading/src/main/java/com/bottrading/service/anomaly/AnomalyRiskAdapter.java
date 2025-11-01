package com.bottrading.service.anomaly;

import com.bottrading.service.risk.RiskFlag;
import java.time.Duration;

public interface AnomalyRiskAdapter {
  void applyPause(RiskFlag flag, Duration duration, String detail);
}
