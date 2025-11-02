package com.bottrading.web.mvc.model;

public record PresetRegimeSummaryView(
    String name,
    String caption,
    String status,
    String statusClass,
    String pf,
    String sharpe,
    String maxDd,
    String trades) {}
