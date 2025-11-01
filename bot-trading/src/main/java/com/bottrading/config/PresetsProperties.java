package com.bottrading.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "presets")
public class PresetsProperties {

  private Promotion promotion = new Promotion();
  private Canary canary = new Canary();
  private boolean autoPromote = false;

  public Promotion getPromotion() {
    return promotion;
  }

  public void setPromotion(Promotion promotion) {
    this.promotion = promotion;
  }

  public Canary getCanary() {
    return canary;
  }

  public void setCanary(Canary canary) {
    this.canary = canary;
  }

  public boolean isAutoPromote() {
    return autoPromote;
  }

  public void setAutoPromote(boolean autoPromote) {
    this.autoPromote = autoPromote;
  }

  public static class Promotion {
    private double epsilonPf = 0.10;
    private double maxddCapPct = 8.0;
    private int minTradesOos = 150;
    private int shadowMinTrades = 50;
    private double shadowPfDropTolerance = 0.15;
    private double pfBaseline = 1.0;

    public double getEpsilonPf() {
      return epsilonPf;
    }

    public void setEpsilonPf(double epsilonPf) {
      this.epsilonPf = epsilonPf;
    }

    public double getMaxddCapPct() {
      return maxddCapPct;
    }

    public void setMaxddCapPct(double maxddCapPct) {
      this.maxddCapPct = maxddCapPct;
    }

    public int getMinTradesOos() {
      return minTradesOos;
    }

    public void setMinTradesOos(int minTradesOos) {
      this.minTradesOos = minTradesOos;
    }

    public int getShadowMinTrades() {
      return shadowMinTrades;
    }

    public void setShadowMinTrades(int shadowMinTrades) {
      this.shadowMinTrades = shadowMinTrades;
    }

    public double getShadowPfDropTolerance() {
      return shadowPfDropTolerance;
    }

    public void setShadowPfDropTolerance(double shadowPfDropTolerance) {
      this.shadowPfDropTolerance = shadowPfDropTolerance;
    }

    public double getPfBaseline() {
      return pfBaseline;
    }

    public void setPfBaseline(double pfBaseline) {
      this.pfBaseline = pfBaseline;
    }
  }

  public static class Canary {
    private List<Double> stages = new ArrayList<>(List.of(0.5, 0.75, 1.0));
    private int stageWindowTrades = 30;

    public List<Double> getStages() {
      return stages;
    }

    public void setStages(List<Double> stages) {
      this.stages = stages;
    }

    public int getStageWindowTrades() {
      return stageWindowTrades;
    }

    public void setStageWindowTrades(int stageWindowTrades) {
      this.stageWindowTrades = stageWindowTrades;
    }
  }
}
