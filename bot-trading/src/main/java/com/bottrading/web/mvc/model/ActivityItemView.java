package com.bottrading.web.mvc.model;

public record ActivityItemView(
    String type,
    String typeClass,
    String bot,
    String description,
    String timestamp,
    String pnl) {}
