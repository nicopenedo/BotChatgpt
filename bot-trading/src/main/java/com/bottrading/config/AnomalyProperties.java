package com.bottrading.config;

import com.bottrading.service.anomaly.AnomalyAction;
import com.bottrading.service.anomaly.AnomalySeverity;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "anomaly")
public class AnomalyProperties {

  private boolean enabled = true;
  private int window = 300;
  private int minSamples = 200;
  private int coolDownSec = 900;
  private boolean esdEnabled = false;
  private ZscoreThresholds zscore = new ZscoreThresholds();
  private Map<AnomalySeverity, AnomalyAction> actions = defaultActions();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getWindow() {
    return window;
  }

  public void setWindow(int window) {
    this.window = window;
  }

  public int getMinSamples() {
    return minSamples;
  }

  public void setMinSamples(int minSamples) {
    this.minSamples = minSamples;
  }

  public int getCoolDownSec() {
    return coolDownSec;
  }

  public void setCoolDownSec(int coolDownSec) {
    this.coolDownSec = coolDownSec;
  }

  public boolean isEsdEnabled() {
    return esdEnabled;
  }

  public void setEsdEnabled(boolean esdEnabled) {
    this.esdEnabled = esdEnabled;
  }

  public ZscoreThresholds getZscore() {
    return zscore;
  }

  public void setZscore(ZscoreThresholds zscore) {
    this.zscore = zscore;
  }

  public Map<AnomalySeverity, AnomalyAction> getActions() {
    return actions;
  }

  public void setActions(Map<String, String> raw) {
    if (raw == null) {
      this.actions = defaultActions();
      return;
    }
    EnumMap<AnomalySeverity, AnomalyAction> map = new EnumMap<>(AnomalySeverity.class);
    raw.forEach(
        (key, value) -> {
          try {
            AnomalySeverity severity = AnomalySeverity.valueOf(key.toUpperCase(Locale.ROOT));
            AnomalyAction action = AnomalyAction.valueOf(value.toUpperCase(Locale.ROOT));
            map.put(severity, action);
          } catch (IllegalArgumentException ignored) {
          }
        });
    this.actions = map.isEmpty() ? defaultActions() : map;
  }

  public AnomalyAction actionFor(AnomalySeverity severity) {
    if (severity == null) {
      return AnomalyAction.NONE;
    }
    if (severity == AnomalySeverity.WARN) {
      return AnomalyAction.ALERT;
    }
    return actions.getOrDefault(severity, AnomalyAction.NONE);
  }

  private EnumMap<AnomalySeverity, AnomalyAction> defaultActions() {
    EnumMap<AnomalySeverity, AnomalyAction> defaults = new EnumMap<>(AnomalySeverity.class);
    defaults.put(AnomalySeverity.MEDIUM, AnomalyAction.SWITCH_TO_MARKET);
    defaults.put(AnomalySeverity.HIGH, AnomalyAction.SIZE_DOWN_50);
    defaults.put(AnomalySeverity.SEVERE, AnomalyAction.PAUSE);
    return defaults;
  }

  public static class ZscoreThresholds {
    private double warn = 2.5;
    private double mitigate = 3.5;
    private Double high;
    private Double severe;

    public double getWarn() {
      return warn;
    }

    public void setWarn(double warn) {
      this.warn = warn;
    }

    public double getMitigate() {
      return mitigate;
    }

    public void setMitigate(double mitigate) {
      this.mitigate = mitigate;
    }

    public double getHigh() {
      if (high != null) {
        return high;
      }
      return mitigate * 1.3;
    }

    public void setHigh(Double high) {
      this.high = high;
    }

    public double getSevere() {
      if (severe != null) {
        return severe;
      }
      return mitigate * 1.8;
    }

    public void setSevere(Double severe) {
      this.severe = severe;
    }
  }
}
