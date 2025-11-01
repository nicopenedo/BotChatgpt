package com.bottrading.config;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stop")
public class StopProperties {

  public enum Mode {
    PERCENT,
    ATR
  }

  private Mode mode = Mode.PERCENT;
  private BigDecimal slPct = BigDecimal.valueOf(0.6);
  private BigDecimal tpPct = BigDecimal.valueOf(1.2);
  private int atrPeriod = 14;
  private BigDecimal slAtrMult = BigDecimal.valueOf(1.5);
  private BigDecimal tpAtrMult = BigDecimal.valueOf(3.0);
  private boolean trailingEnabled = true;
  private BigDecimal trailingPct = BigDecimal.valueOf(0.5);
  private BigDecimal trailingAtrMult = BigDecimal.ONE;
  private boolean breakevenEnabled = true;
  private BigDecimal breakevenTriggerPct = BigDecimal.valueOf(0.4);
  private Map<String, StopSymbolProperties> symbols = new ConcurrentHashMap<>();

  public Mode getMode() {
    return mode;
  }

  public void setMode(Mode mode) {
    this.mode = mode;
  }

  public BigDecimal getSlPct() {
    return slPct;
  }

  public void setSlPct(BigDecimal slPct) {
    this.slPct = slPct;
  }

  public BigDecimal getTpPct() {
    return tpPct;
  }

  public void setTpPct(BigDecimal tpPct) {
    this.tpPct = tpPct;
  }

  public int getAtrPeriod() {
    return atrPeriod;
  }

  public void setAtrPeriod(int atrPeriod) {
    this.atrPeriod = atrPeriod;
  }

  public BigDecimal getSlAtrMult() {
    return slAtrMult;
  }

  public void setSlAtrMult(BigDecimal slAtrMult) {
    this.slAtrMult = slAtrMult;
  }

  public BigDecimal getTpAtrMult() {
    return tpAtrMult;
  }

  public void setTpAtrMult(BigDecimal tpAtrMult) {
    this.tpAtrMult = tpAtrMult;
  }

  public boolean isTrailingEnabled() {
    return trailingEnabled;
  }

  public void setTrailingEnabled(boolean trailingEnabled) {
    this.trailingEnabled = trailingEnabled;
  }

  public BigDecimal getTrailingPct() {
    return trailingPct;
  }

  public void setTrailingPct(BigDecimal trailingPct) {
    this.trailingPct = trailingPct;
  }

  public BigDecimal getTrailingAtrMult() {
    return trailingAtrMult;
  }

  public void setTrailingAtrMult(BigDecimal trailingAtrMult) {
    this.trailingAtrMult = trailingAtrMult;
  }

  public boolean isBreakevenEnabled() {
    return breakevenEnabled;
  }

  public void setBreakevenEnabled(boolean breakevenEnabled) {
    this.breakevenEnabled = breakevenEnabled;
  }

  public BigDecimal getBreakevenTriggerPct() {
    return breakevenTriggerPct;
  }

  public void setBreakevenTriggerPct(BigDecimal breakevenTriggerPct) {
    this.breakevenTriggerPct = breakevenTriggerPct;
  }

  public Map<String, StopSymbolProperties> getSymbols() {
    return symbols;
  }

  public void setSymbols(Map<String, StopSymbolProperties> symbols) {
    this.symbols = new ConcurrentHashMap<>(symbols);
  }

  public StopSymbolProperties getForSymbol(String symbol) {
    return symbols.getOrDefault(symbol, StopSymbolProperties.from(this));
  }

  public static class StopSymbolProperties {
    private Mode mode;
    private BigDecimal slPct;
    private BigDecimal tpPct;
    private BigDecimal slAtrMult;
    private BigDecimal tpAtrMult;
    private Boolean trailingEnabled;
    private BigDecimal trailingPct;
    private BigDecimal trailingAtrMult;
    private Boolean breakevenEnabled;
    private BigDecimal breakevenTriggerPct;

    public static StopSymbolProperties from(StopProperties source) {
      StopSymbolProperties props = new StopSymbolProperties();
      props.mode = source.mode;
      props.slPct = source.slPct;
      props.tpPct = source.tpPct;
      props.slAtrMult = source.slAtrMult;
      props.tpAtrMult = source.tpAtrMult;
      props.trailingEnabled = source.trailingEnabled;
      props.trailingPct = source.trailingPct;
      props.trailingAtrMult = source.trailingAtrMult;
      props.breakevenEnabled = source.breakevenEnabled;
      props.breakevenTriggerPct = source.breakevenTriggerPct;
      return props;
    }

    public Mode getModeOrDefault(StopProperties defaults) {
      return mode != null ? mode : defaults.mode;
    }

    public BigDecimal getSlPctOrDefault(StopProperties defaults) {
      return slPct != null ? slPct : defaults.slPct;
    }

    public BigDecimal getTpPctOrDefault(StopProperties defaults) {
      return tpPct != null ? tpPct : defaults.tpPct;
    }

    public BigDecimal getSlAtrMultOrDefault(StopProperties defaults) {
      return slAtrMult != null ? slAtrMult : defaults.slAtrMult;
    }

    public BigDecimal getTpAtrMultOrDefault(StopProperties defaults) {
      return tpAtrMult != null ? tpAtrMult : defaults.tpAtrMult;
    }

    public boolean isTrailingEnabledOrDefault(StopProperties defaults) {
      return trailingEnabled != null ? trailingEnabled : defaults.trailingEnabled;
    }

    public BigDecimal getTrailingPctOrDefault(StopProperties defaults) {
      return trailingPct != null ? trailingPct : defaults.trailingPct;
    }

    public BigDecimal getTrailingAtrMultOrDefault(StopProperties defaults) {
      return trailingAtrMult != null ? trailingAtrMult : defaults.trailingAtrMult;
    }

    public boolean isBreakevenEnabledOrDefault(StopProperties defaults) {
      return breakevenEnabled != null ? breakevenEnabled : defaults.breakevenEnabled;
    }

    public BigDecimal getBreakevenTriggerPctOrDefault(StopProperties defaults) {
      return breakevenTriggerPct != null ? breakevenTriggerPct : defaults.breakevenTriggerPct;
    }

    public Mode getMode() {
      return mode;
    }

    public void setMode(Mode mode) {
      this.mode = mode;
    }

    public BigDecimal getSlPct() {
      return slPct;
    }

    public void setSlPct(BigDecimal slPct) {
      this.slPct = slPct;
    }

    public BigDecimal getTpPct() {
      return tpPct;
    }

    public void setTpPct(BigDecimal tpPct) {
      this.tpPct = tpPct;
    }

    public BigDecimal getSlAtrMult() {
      return slAtrMult;
    }

    public void setSlAtrMult(BigDecimal slAtrMult) {
      this.slAtrMult = slAtrMult;
    }

    public BigDecimal getTpAtrMult() {
      return tpAtrMult;
    }

    public void setTpAtrMult(BigDecimal tpAtrMult) {
      this.tpAtrMult = tpAtrMult;
    }

    public Boolean getTrailingEnabled() {
      return trailingEnabled;
    }

    public void setTrailingEnabled(Boolean trailingEnabled) {
      this.trailingEnabled = trailingEnabled;
    }

    public BigDecimal getTrailingPct() {
      return trailingPct;
    }

    public void setTrailingPct(BigDecimal trailingPct) {
      this.trailingPct = trailingPct;
    }

    public BigDecimal getTrailingAtrMult() {
      return trailingAtrMult;
    }

    public void setTrailingAtrMult(BigDecimal trailingAtrMult) {
      this.trailingAtrMult = trailingAtrMult;
    }

    public Boolean getBreakevenEnabled() {
      return breakevenEnabled;
    }

    public void setBreakevenEnabled(Boolean breakevenEnabled) {
      this.breakevenEnabled = breakevenEnabled;
    }

    public BigDecimal getBreakevenTriggerPct() {
      return breakevenTriggerPct;
    }

    public void setBreakevenTriggerPct(BigDecimal breakevenTriggerPct) {
      this.breakevenTriggerPct = breakevenTriggerPct;
    }
  }
}
