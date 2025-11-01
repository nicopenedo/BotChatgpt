package com.bottrading.model.dto.report;

import java.math.BigDecimal;
import java.time.Instant;

public record AtrBandPoint(Instant ts, BigDecimal mid, BigDecimal upper, BigDecimal lower) {}
