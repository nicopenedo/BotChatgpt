package com.bottrading.config;

import com.bottrading.service.risk.TradingState;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class TradingStateInitializer {

  private final TradingState tradingState;
  private final TradingProperties tradingProperties;

  public TradingStateInitializer(TradingState tradingState, TradingProperties tradingProperties) {
    this.tradingState = tradingState;
    this.tradingProperties = tradingProperties;
  }

  @PostConstruct
  public void init() {
    tradingState.setLiveEnabled(tradingProperties.isLiveEnabled());
  }
}
