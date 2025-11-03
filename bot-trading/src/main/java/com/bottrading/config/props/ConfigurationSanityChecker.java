package com.bottrading.config.props;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationSanityChecker {

  private final TradingProps tradingProps;
  private final BinanceProps binanceProps;

  public ConfigurationSanityChecker(TradingProps tradingProps, BinanceProps binanceProps) {
    this.tradingProps = tradingProps;
    this.binanceProps = binanceProps;
  }

  @PostConstruct
  public void validateLiveTradingRequirements() {
    if (tradingProps.getMode() != TradingProps.Mode.LIVE) {
      return;
    }

    if (isPlaceholder(binanceProps.getApiKey()) || isPlaceholder(binanceProps.getApiSecret())) {
      throw new IllegalStateException(
          "Binance API credentials must be configured when trading.mode=LIVE");
    }

    if (containsIgnoreCase(binanceProps.getBaseUrl(), "testnet")) {
      throw new IllegalStateException(
          "Binance base-url must point to the production endpoint when trading.mode=LIVE");
    }
  }

  private boolean isPlaceholder(String value) {
    if (value == null) {
      return true;
    }
    String normalized = value.trim();
    if (normalized.isEmpty()) {
      return true;
    }
    String lower = normalized.toLowerCase(Locale.ROOT);
    return lower.contains("fake") || lower.contains("changeme") || lower.contains("testnet");
  }

  private boolean containsIgnoreCase(String value, String token) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
  }
}
