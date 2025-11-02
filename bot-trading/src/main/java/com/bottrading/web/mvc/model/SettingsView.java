package com.bottrading.web.mvc.model;

import java.util.List;

public record SettingsView(
    String apiKey,
    String webhookSecret,
    NotificationSettingsView notifications,
    RiskLimitSettingsView riskLimits,
    List<String> timezones) {}
