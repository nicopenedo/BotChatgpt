package com.bottrading.web.mvc.model;

import java.util.List;

public record PresetDetailView(
    String id,
    String name,
    String caption,
    String status,
    String statusClass,
    String regime,
    String regimeClass,
    String side,
    List<PresetMetricView> metrics,
    String params,
    String performanceChartJson) {}
