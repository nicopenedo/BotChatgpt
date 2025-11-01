package com.bottrading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "binance")
public record BinanceProperties(
    String apiKey,
    String apiSecret,
    String baseUrl,
    String streamUrl) {

  public BinanceProperties {
    if (streamUrl == null || streamUrl.isBlank()) {
      if (baseUrl != null && baseUrl.contains("testnet")) {
        streamUrl = "wss://testnet.binance.vision/ws/";
      } else {
        streamUrl = "wss://stream.binance.com:9443/ws/";
      }
    }
  }
}
