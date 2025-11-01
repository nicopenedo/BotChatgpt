package com.bottrading.config;

import com.bottrading.service.risk.TradingState;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class TradingStateInitializer {

  private final TradingState tradingState;
  private final TradingProps tradingProperties;

  public TradingStateInitializer(TradingState tradingState, TradingProps tradingProperties) {
    this.tradingState = tradingState;
    this.tradingProperties = tradingProperties;
  }

  @PostConstruct
  public void init() {
    tradingState.setLiveEnabled(tradingProperties.isLiveEnabled());
  }
}
