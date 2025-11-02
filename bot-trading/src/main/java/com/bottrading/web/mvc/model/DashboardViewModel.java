package com.bottrading.web.mvc.model;

import java.util.List;

public record DashboardViewModel(
    List<KpiCardView> kpis,
    String equityChartJson,
    String pnlChartJson,
    String heatmapChartJson,
    List<ActivityItemView> recentActivity,
    List<RiskCardView> riskCards) {}
