package com.bottrading.config;

import com.bottrading.chaos.ChaosClock;
import com.bottrading.chaos.ChaosSuite;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

  @Bean
  public Clock systemClock(ChaosSuite chaosSuite) {
    return new ChaosClock(Clock.systemUTC(), chaosSuite);
  }
}
