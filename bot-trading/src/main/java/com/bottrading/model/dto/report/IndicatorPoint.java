package com.bottrading.model.dto.report;

import java.math.BigDecimal;
import java.time.Instant;

public record IndicatorPoint(Instant ts, BigDecimal value) {}
