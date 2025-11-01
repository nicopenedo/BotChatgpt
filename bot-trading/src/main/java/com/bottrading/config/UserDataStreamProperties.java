package com.bottrading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "binance.userdatastream")
public class UserDataStreamProperties {

  private int keepaliveMinutes = 30;

  public int getKeepaliveMinutes() {
    return keepaliveMinutes;
  }

  public void setKeepaliveMinutes(int keepaliveMinutes) {
    this.keepaliveMinutes = keepaliveMinutes;
  }
}
