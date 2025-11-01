package com.bottrading.config;

import com.bottrading.config.TradingProps;
import com.giffing.bucket4j.spring.boot.starter.context.FilterConfiguration;
import com.giffing.bucket4j.spring.boot.starter.context.filters.ServletRequestFilterConfiguration;
import com.giffing.bucket4j.spring.boot.starter.context.metrics.MetricTagSupplier;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.Collections;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitingConfig {

  @Bean
  public FilterConfiguration servletFilterConfiguration(TradingProps properties) {
    Bandwidth bandwidth =
        Bandwidth.classic(
            properties.getMaxOrdersPerMinute(),
            Refill.greedy(properties.getMaxOrdersPerMinute(), Duration.ofMinutes(1)));
    BucketConfiguration bucketConfiguration =
        BucketConfiguration.builder().addLimit(bandwidth).build();

    ServletRequestFilterConfiguration configuration = new ServletRequestFilterConfiguration();
    configuration.setCacheName("http-rate-limit");
    configuration.setFilterMethod(ServletRequestFilterConfiguration.FilterMethod.SERVLET);
    configuration.setUrl("/api/trade/*");
    configuration.setStrategy(ServletRequestFilterConfiguration.StrategyType.SINGLE);
    configuration.setMetricsTags(Collections.singletonList(MetricTagSupplier.httpMethod()));
    configuration.setBucketConfiguration(bucketConfiguration);
    return configuration;
  }
}
