package com.bottrading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "binance")
public record BinanceProperties(
    String apiKey,
    String apiSecret,
    String baseUrl) {}
