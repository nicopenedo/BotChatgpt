package com.bottrading.model.dto.report;

import java.math.BigDecimal;
import java.time.Instant;

public record SupertrendPoint(Instant ts, String trend, BigDecimal line) {}
