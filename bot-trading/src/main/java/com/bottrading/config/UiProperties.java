package com.bottrading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ui")
public class UiProperties {

  private String grafanaUrl;

  public String getGrafanaUrl() {
    return grafanaUrl;
  }

  public void setGrafanaUrl(String grafanaUrl) {
    this.grafanaUrl = grafanaUrl;
  }
}
