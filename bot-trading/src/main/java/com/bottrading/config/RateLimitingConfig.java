package com.bottrading.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class RateLimitingConfig {

  @Bean
  public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(
      TradingProps properties) {
    FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new RateLimitingFilter(properties));
    registration.addUrlPatterns("/api/trade/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
    return registration;
  }
}
