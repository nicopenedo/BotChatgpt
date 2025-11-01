package com.bottrading.chaos;

import java.time.Instant;

public record ChaosStatus(
    boolean active,
    ChaosSettings settings,
    Instant startedAt,
    Instant endsAt,
    long secondsRemaining,
    boolean websocketHealthy) {}
