package com.bottrading.model.dto.report;

import java.math.BigDecimal;

public record HeatmapCell(int x, int y, long trades, BigDecimal netPnl, BigDecimal winRate) {}
