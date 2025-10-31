package com.bottrading.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

  public static final String EXCHANGE_INFO_CACHE = "exchangeInfo";
  public static final String COMMISSION_CACHE = "tradingCommission";

  @Bean
  public Caffeine<Object, Object> caffeine() {
    return Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(30));
  }

  @Bean
  public CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
    CaffeineCacheManager manager = new CaffeineCacheManager(EXCHANGE_INFO_CACHE, COMMISSION_CACHE);
    manager.setCaffeine(caffeine);
    return manager;
  }
}
