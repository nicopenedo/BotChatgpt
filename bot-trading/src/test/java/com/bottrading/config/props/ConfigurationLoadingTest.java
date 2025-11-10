package com.bottrading.config.props;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class ConfigurationLoadingTest {

  @DynamicPropertySource
  static void importDotEnv(DynamicPropertyRegistry registry) {
    String envPath = Path.of(".env.example").toAbsolutePath().toString();
    registry.add(
        "spring.config.import",
        () -> "optional:file:.env[.properties],optional:file:" + envPath + "[.properties]");
  }

  @Autowired private BinanceProps binanceProps;

  @Autowired private TradingProps tradingProps;

  @Autowired private BanditProps banditProps;

  @Autowired private AlertsProps alertsProps;

  @Autowired private SecurityProps securityProps;

  @Test
  void contextLoadsAndBindsProperties() {
    assertThat(binanceProps.getBaseUrl()).isEqualTo("https://testnet.binance.vision");
    assertThat(tradingProps.getMode()).isEqualTo(TradingProps.Mode.SHADOW);
    assertThat(tradingProps.getRisk().getMaxDailyDrawdownPct()).isNotNull();
    assertThat(banditProps.getContextFeatures()).isNotEmpty();
    assertThat(alertsProps.isEmailEnabled()).isTrue();
    assertThat(securityProps.getSecret()).isNotBlank();
  }
}
