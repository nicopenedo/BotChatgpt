package com.bottrading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oco")
public class OcoProperties {

  private final ClientEmulation clientEmulation = new ClientEmulation();

  public ClientEmulation getClientEmulation() {
    return clientEmulation;
  }

  public static class ClientEmulation {
    private boolean enabled = true;
    private long cancelGraceMillis = 2000;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public long getCancelGraceMillis() {
      return cancelGraceMillis;
    }

    public void setCancelGraceMillis(long cancelGraceMillis) {
      this.cancelGraceMillis = cancelGraceMillis;
    }
  }
}
