package com.bottrading.saas.config;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "saas")
public class SaasProperties {

  private Billing billing = new Billing();
  private Plans plans = new Plans();
  private Onboarding onboarding = new Onboarding();
  private Security security = new Security();
  private Legal legal = new Legal();
  private Metrics metrics = new Metrics();

  public Billing getBilling() {
    return billing;
  }

  public void setBilling(Billing billing) {
    this.billing = billing;
  }

  public Plans getPlans() {
    return plans;
  }

  public void setPlans(Plans plans) {
    this.plans = plans;
  }

  public Onboarding getOnboarding() {
    return onboarding;
  }

  public void setOnboarding(Onboarding onboarding) {
    this.onboarding = onboarding;
  }

  public Security getSecurity() {
    return security;
  }

  public void setSecurity(Security security) {
    this.security = security;
  }

  public Legal getLegal() {
    return legal;
  }

  public void setLegal(Legal legal) {
    this.legal = legal;
  }

  public Metrics getMetrics() {
    return metrics;
  }

  public void setMetrics(Metrics metrics) {
    this.metrics = metrics;
  }

  public static class Billing {
    private String provider = "stripe";
    private int graceDays = 5;

    public String getProvider() {
      return provider;
    }

    public void setProvider(String provider) {
      this.provider = provider;
    }

    public int getGraceDays() {
      return graceDays;
    }

    public void setGraceDays(int graceDays) {
      this.graceDays = graceDays;
    }
  }

  public static class Plans {
    private Plan starter = new Plan();
    private Plan pro = new Plan();

    public Plan getStarter() {
      return starter;
    }

    public void setStarter(Plan starter) {
      this.starter = starter;
    }

    public Plan getPro() {
      return pro;
    }

    public void setPro(Plan pro) {
      this.pro = pro;
    }
  }

  public static class Plan {
    private int maxBots;
    private int maxSymbols;
    private BigDecimal canaryShareMax;
    private int dataRetentionDays;
    private Map<String, Boolean> featureFlags;
    private BigDecimal maxDailyDrawdownPct = BigDecimal.ZERO;
    private int maxConcurrentPositions = 0;
    private int maxDailyTrades = 0;
    private BigDecimal canaryPct = BigDecimal.ZERO;

    public int getMaxBots() {
      return maxBots;
    }

    public void setMaxBots(int maxBots) {
      this.maxBots = maxBots;
    }

    public int getMaxSymbols() {
      return maxSymbols;
    }

    public void setMaxSymbols(int maxSymbols) {
      this.maxSymbols = maxSymbols;
    }

    public BigDecimal getCanaryShareMax() {
      return canaryShareMax;
    }

    public void setCanaryShareMax(BigDecimal canaryShareMax) {
      this.canaryShareMax = canaryShareMax;
    }

    public int getDataRetentionDays() {
      return dataRetentionDays;
    }

    public void setDataRetentionDays(int dataRetentionDays) {
      this.dataRetentionDays = dataRetentionDays;
    }

    public Map<String, Boolean> getFeatureFlags() {
      return featureFlags;
    }

    public void setFeatureFlags(Map<String, Boolean> featureFlags) {
      this.featureFlags = featureFlags;
    }

    public BigDecimal getMaxDailyDrawdownPct() {
      return maxDailyDrawdownPct;
    }

    public void setMaxDailyDrawdownPct(BigDecimal maxDailyDrawdownPct) {
      this.maxDailyDrawdownPct = maxDailyDrawdownPct;
    }

    public int getMaxConcurrentPositions() {
      return maxConcurrentPositions;
    }

    public void setMaxConcurrentPositions(int maxConcurrentPositions) {
      this.maxConcurrentPositions = maxConcurrentPositions;
    }

    public int getMaxDailyTrades() {
      return maxDailyTrades;
    }

    public void setMaxDailyTrades(int maxDailyTrades) {
      this.maxDailyTrades = maxDailyTrades;
    }

    public BigDecimal getCanaryPct() {
      return canaryPct;
    }

    public void setCanaryPct(BigDecimal canaryPct) {
      this.canaryPct = canaryPct;
    }
  }

  public static class Onboarding {
    private int shadowTradesMin = 40;

    public int getShadowTradesMin() {
      return shadowTradesMin;
    }

    public void setShadowTradesMin(int shadowTradesMin) {
      this.shadowTradesMin = shadowTradesMin;
    }
  }

  public static class Security {
    private boolean enforceNoWithdrawal = true;
    private boolean requireIpAllowlist = true;
    private String kmsMasterKey;

    public boolean isEnforceNoWithdrawal() {
      return enforceNoWithdrawal;
    }

    public void setEnforceNoWithdrawal(boolean enforceNoWithdrawal) {
      this.enforceNoWithdrawal = enforceNoWithdrawal;
    }

    public boolean isRequireIpAllowlist() {
      return requireIpAllowlist;
    }

    public void setRequireIpAllowlist(boolean requireIpAllowlist) {
      this.requireIpAllowlist = requireIpAllowlist;
    }

    public String getKmsMasterKey() {
      return kmsMasterKey;
    }

    public void setKmsMasterKey(String kmsMasterKey) {
      this.kmsMasterKey = kmsMasterKey;
    }
  }

  public static class Legal {
    private String termsVersion;
    private String riskVersion;
    private int exportTokenTtlMinutes = 15;
    private int deletionMinHours = 24;
    private int deletionMaxHours = 72;
    private java.util.List<String> sanctionedCountries = java.util.List.of();

    public String getTermsVersion() {
      return termsVersion;
    }

    public void setTermsVersion(String termsVersion) {
      this.termsVersion = termsVersion;
    }

    public String getRiskVersion() {
      return riskVersion;
    }

    public void setRiskVersion(String riskVersion) {
      this.riskVersion = riskVersion;
    }

    public int getExportTokenTtlMinutes() {
      return exportTokenTtlMinutes;
    }

    public void setExportTokenTtlMinutes(int exportTokenTtlMinutes) {
      this.exportTokenTtlMinutes = exportTokenTtlMinutes;
    }

    public int getDeletionMinHours() {
      return deletionMinHours;
    }

    public void setDeletionMinHours(int deletionMinHours) {
      this.deletionMinHours = deletionMinHours;
    }

    public int getDeletionMaxHours() {
      return deletionMaxHours;
    }

    public void setDeletionMaxHours(int deletionMaxHours) {
      this.deletionMaxHours = deletionMaxHours;
    }

    public java.util.List<String> getSanctionedCountries() {
      return sanctionedCountries;
    }

    public void setSanctionedCountries(java.util.List<String> sanctionedCountries) {
      this.sanctionedCountries = sanctionedCountries;
    }
  }

  public static class Metrics {
    private long planCacheTtlSeconds = 300;

    public long getPlanCacheTtlSeconds() {
      return planCacheTtlSeconds;
    }

    public void setPlanCacheTtlSeconds(long planCacheTtlSeconds) {
      this.planCacheTtlSeconds = planCacheTtlSeconds;
    }
  }
}
