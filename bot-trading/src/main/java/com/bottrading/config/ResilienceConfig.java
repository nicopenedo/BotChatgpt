package com.bottrading.config;

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceConfig {

  @Bean
  public RetryConfig defaultRetryConfig() {
    return RetryConfig.custom()
        .maxAttempts(3)
        .waitDuration(Duration.ofMillis(500))
        .retryExceptions(RuntimeException.class)
        .build();
  }

  @Bean
  public RateLimiterConfig defaultRateLimiterConfig() {
    return RateLimiterConfig.custom()
        .timeoutDuration(Duration.ofSeconds(2))
        .limitForPeriod(10)
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .build();
  }
}
