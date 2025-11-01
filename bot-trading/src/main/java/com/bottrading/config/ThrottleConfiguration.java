package com.bottrading.config;

import com.bottrading.throttle.Throttle;
import com.bottrading.throttle.ThrottleProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ThrottleConfiguration {

  @Bean
  public Throttle throttle(ThrottleProperties properties, MeterRegistry meterRegistry) {
    return new Throttle(properties, meterRegistry);
  }
}
