package com.bottrading.web.mvc.model;

public record LeaderboardEntryView(
    String name,
    String type,
    String typeClass,
    String pf,
    String pnl,
    String winRate,
    String maxDd) {}
