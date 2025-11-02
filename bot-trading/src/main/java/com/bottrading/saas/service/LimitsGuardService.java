package com.bottrading.saas.service;

import com.bottrading.saas.model.entity.TenantLimitsEntity;
import com.bottrading.saas.repository.TenantLimitsRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LimitsGuardService {

  public enum ViolationType {
    BOTS,
    SYMBOLS,
    TRADES,
    CANARY
  }

  private final TenantLimitsRepository tenantLimitsRepository;

  public LimitsGuardService(TenantLimitsRepository tenantLimitsRepository) {
    this.tenantLimitsRepository = tenantLimitsRepository;
  }

  public boolean canOpenBot(UUID tenantId, int activeBots) {
    TenantLimitsEntity limits = tenantLimitsRepository.findById(tenantId).orElse(null);
    return limits == null || activeBots < limits.getMaxBots();
  }

  public boolean canTradeSymbol(UUID tenantId, int activeSymbols) {
    TenantLimitsEntity limits = tenantLimitsRepository.findById(tenantId).orElse(null);
    return limits == null || activeSymbols <= limits.getMaxSymbols();
  }

  public boolean canOpenTrade(UUID tenantId, int tradesToday) {
    TenantLimitsEntity limits = tenantLimitsRepository.findById(tenantId).orElse(null);
    return limits == null || tradesToday < limits.getMaxTradesPerDay();
  }

  public boolean withinCanary(UUID tenantId, BigDecimal requestedShare) {
    TenantLimitsEntity limits = tenantLimitsRepository.findById(tenantId).orElse(null);
    return limits == null || requestedShare.compareTo(limits.getCanaryShareMax()) <= 0;
  }

  public BigDecimal maxDrawdownPct(UUID tenantId) {
    TenantLimitsEntity limits = tenantLimitsRepository.findById(tenantId).orElse(null);
    return limits != null ? limits.getMaxDailyDrawdownPct() : BigDecimal.ZERO;
  }

  public int maxConcurrentPositions(UUID tenantId) {
    TenantLimitsEntity limits = tenantLimitsRepository.findById(tenantId).orElse(null);
    return limits != null ? limits.getMaxConcurrentPositions() : Integer.MAX_VALUE;
  }

  public int maxDailyTrades(UUID tenantId) {
    TenantLimitsEntity limits = tenantLimitsRepository.findById(tenantId).orElse(null);
    return limits != null ? limits.getMaxTradesPerDay() : Integer.MAX_VALUE;
  }
}
