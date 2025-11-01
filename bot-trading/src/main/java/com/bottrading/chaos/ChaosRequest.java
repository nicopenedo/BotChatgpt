package com.bottrading.chaos;

import com.bottrading.config.ChaosProperties.GapPattern;

public record ChaosRequest(
    Boolean enabled,
    Integer wsDropRatePct,
    Integer apiBurst429Seconds,
    Double latencyMultiplier,
    GapPattern candlesGapPattern,
    Long clockDriftMs,
    Integer maxDurationSec) {}
