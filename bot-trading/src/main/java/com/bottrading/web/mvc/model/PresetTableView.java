package com.bottrading.web.mvc.model;

public record PresetTableView(
    String id,
    String name,
    String regime,
    String regimeClass,
    String mode,
    String pf,
    String sharpe,
    String maxDd,
    String trades) {}
