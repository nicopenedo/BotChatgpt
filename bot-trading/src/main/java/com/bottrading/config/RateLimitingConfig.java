package com.bottrading.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class RateLimitingConfig {

  @Bean
  public RateLimitingFilter rateLimitingFilter(TradingProps props) {
    return new RateLimitingFilter(props);
  }

  @Bean
  public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(
      RateLimitingFilter filter) {
    FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(filter);
    registration.addUrlPatterns("/api/trade/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
    return registration;
  }
}
