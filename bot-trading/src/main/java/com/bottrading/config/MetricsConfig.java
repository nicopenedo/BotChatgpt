package com.bottrading.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

  @Bean
  public MeterRegistryCustomizer meterRegistryCustomizer() {
    return new MeterRegistryCustomizer();
  }

  public static class MeterRegistryCustomizer implements org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer<MeterRegistry> {
    @Override
    public void customize(MeterRegistry registry) {
      registry.config().commonTags("app", "bot-trading");
    }
  }
}
