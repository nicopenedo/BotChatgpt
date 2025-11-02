package com.bottrading.saas;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.saas.config.SaasProperties;
import com.bottrading.saas.service.ShadowPromoterService;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShadowPromoterServiceTest {

  private ShadowPromoterService service;

  @BeforeEach
  void setup() {
    SaasProperties properties = new SaasProperties();
    properties.getOnboarding().setShadowTradesMin(40);
    service = new ShadowPromoterService(properties);
  }

  @Test
  void promotesWhenThresholdsMet() {
    boolean result =
        service.shouldPromote(
            Map.of(
                "profitFactor", new BigDecimal("1.5"),
                "maxDrawdown", new BigDecimal("0.05"),
                "trades", new BigDecimal("50")));
    assertThat(result).isTrue();
  }

  @Test
  void rejectsWhenBelowThreshold() {
    boolean result =
        service.shouldPromote(
            Map.of(
                "profitFactor", new BigDecimal("0.8"),
                "maxDrawdown", new BigDecimal("0.05"),
                "trades", new BigDecimal("50")));
    assertThat(result).isFalse();
  }
}
