package com.bottrading;

import com.bottrading.config.props.AlertsProps;
import com.bottrading.config.props.BanditProps;
import com.bottrading.config.props.BinanceProps;
import com.bottrading.config.props.SecurityProps;
import com.bottrading.config.props.TradingProps;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableConfigurationProperties({
  BinanceProps.class,
  TradingProps.class,
  BanditProps.class,
  AlertsProps.class,
  SecurityProps.class
})
@EnableScheduling
public class BotTradingApplication {

  public static void main(String[] args) {
    SpringApplication.run(BotTradingApplication.class, args);
  }
}
