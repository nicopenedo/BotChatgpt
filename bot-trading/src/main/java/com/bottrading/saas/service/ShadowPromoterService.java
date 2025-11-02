package com.bottrading.saas.service;

import com.bottrading.saas.config.SaasProperties;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ShadowPromoterService {

  private final SaasProperties saasProperties;

  public ShadowPromoterService(SaasProperties saasProperties) {
    this.saasProperties = saasProperties;
  }

  public boolean shouldPromote(Map<String, BigDecimal> kpis) {
    if (kpis == null || kpis.isEmpty()) {
      return false;
    }
    BigDecimal pf = kpis.getOrDefault("profitFactor", BigDecimal.ZERO);
    BigDecimal maxDd = kpis.getOrDefault("maxDrawdown", BigDecimal.TEN);
    BigDecimal trades = kpis.getOrDefault("trades", BigDecimal.ZERO);
    return pf.compareTo(BigDecimal.ONE) >= 0
        && maxDd.compareTo(new BigDecimal("0.08")) < 0
        && trades.intValue() >= saasProperties.getOnboarding().getShadowTradesMin();
  }
}
