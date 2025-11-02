package com.bottrading.web.mvc.model;

import java.util.List;

public record BotDetailView(
    String id,
    String name,
    String symbol,
    String mode,
    String modeClass,
    String regime,
    String regimeClass,
    List<BotRuleView> rules,
    List<BotLogView> logs,
    String equityChartJson,
    String symbolChartJson,
    String slippageChartJson) {}
