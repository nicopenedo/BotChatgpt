package com.bottrading.service.anomaly;

import com.bottrading.service.risk.RiskFlag;

public enum AnomalyMetric {
  SLIPPAGE_BPS("slippage_bps", RiskFlag.ANOMALY_SLIPPAGE),
  FILL_RATE("fill_rate", RiskFlag.ANOMALY_FILL_RATE),
  QUEUE_TIME_MS("queue_time_ms", RiskFlag.ANOMALY_QUEUE_TIME),
  LATENCY_MS("latency_ms", RiskFlag.ANOMALY_LATENCY),
  API_ERROR_RATE("api_error_rate", RiskFlag.ANOMALY_API_ERRORS),
  WS_RECONNECTS("ws_reconnects", RiskFlag.ANOMALY_WS_RECONNECTS),
  SPREAD_BPS("spread_bps", RiskFlag.ANOMALY_SPREAD);

  private final String id;
  private final RiskFlag riskFlag;

  AnomalyMetric(String id, RiskFlag riskFlag) {
    this.id = id;
    this.riskFlag = riskFlag;
  }

  public String id() {
    return id;
  }

  public RiskFlag riskFlag() {
    return riskFlag;
  }
}
