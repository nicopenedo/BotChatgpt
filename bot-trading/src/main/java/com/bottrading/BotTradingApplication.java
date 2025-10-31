package com.bottrading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BotTradingApplication {

  public static void main(String[] args) {
    SpringApplication.run(BotTradingApplication.class, args);
  }
}
