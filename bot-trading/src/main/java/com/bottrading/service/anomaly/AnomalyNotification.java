package com.bottrading.service.anomaly;

import java.time.Instant;

public record AnomalyNotification(
    String symbol,
    AnomalyMetric metric,
    AnomalySeverity severity,
    AnomalyAction action,
    double value,
    double mean,
    double zScore,
    Instant triggeredAt,
    Instant expiresAt,
    String detail) {}
