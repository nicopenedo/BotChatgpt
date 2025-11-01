package com.bottrading.executor;

public record KlineEvent(String symbol, String interval, long closeTime, boolean isFinal) {}
