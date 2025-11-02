package com.bottrading.web.mvc.model;

public record BotSummaryView(
    String id,
    String name,
    String symbol,
    String mode,
    String modeClass,
    String performanceFactor,
    String maxDrawdown,
    String pnl,
    String lastTrade) {}
