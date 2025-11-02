package com.bottrading.web.mvc.model;

public record RiskLimitSettingsView(double maxDailyLoss, double maxTradeLoss, double maxExposure, String timezone, boolean darkMode) {}
