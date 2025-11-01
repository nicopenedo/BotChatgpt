package com.bottrading.model.dto.report;

import java.util.List;

public record HeatmapResponse(List<String> xLabels, List<String> yLabels, List<HeatmapCell> cells) {}
