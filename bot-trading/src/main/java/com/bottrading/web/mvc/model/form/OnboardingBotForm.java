package com.bottrading.web.mvc.model.form;

import jakarta.validation.constraints.NotBlank;

public class OnboardingBotForm {

  @NotBlank(message = "Seleccioná un símbolo")
  private String symbol = "BTCUSDT";

  @NotBlank(message = "Elegí un intervalo")
  private String interval = "1h";

  @NotBlank(message = "Seleccioná un preset")
  private String preset = "Momentum Shadow";

  private boolean shadowMode = true;

  public String getSymbol() {
    return symbol;
  }

  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  public String getInterval() {
    return interval;
  }

  public void setInterval(String interval) {
    this.interval = interval;
  }

  public String getPreset() {
    return preset;
  }

  public void setPreset(String preset) {
    this.preset = preset;
  }

  public boolean isShadowMode() {
    return shadowMode;
  }

  public void setShadowMode(boolean shadowMode) {
    this.shadowMode = shadowMode;
  }
}
