package com.bottrading.saas.service;

import com.bottrading.saas.config.SaasProperties;
import com.bottrading.saas.model.entity.TenantEntity;
import com.bottrading.saas.repository.TenantRepository;
import com.bottrading.saas.security.TenantContext;
import io.micrometer.core.instrument.Tags;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TenantMetrics {

  private final TenantRepository tenantRepository;
  private final Clock clock;
  private final Duration planCacheTtl;
  private final ConcurrentMap<UUID, CacheEntry> planCache = new ConcurrentHashMap<>();

  @Autowired
  public TenantMetrics(TenantRepository tenantRepository, SaasProperties properties) {
    this(tenantRepository, properties, Clock.systemUTC());
  }

  public TenantMetrics(TenantRepository tenantRepository, SaasProperties properties, Clock clock) {
    this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository");
    this.clock = Objects.requireNonNull(clock, "clock");
    SaasProperties.Metrics metrics = Objects.requireNonNull(properties, "properties").getMetrics();
    long ttlSeconds = metrics != null ? Math.max(1L, metrics.getPlanCacheTtlSeconds()) : 300L;
    this.planCacheTtl = Duration.ofSeconds(ttlSeconds);
  }

  public Tags tags(String symbol) {
    UUID tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      return Tags.of("tenant", "global", "plan", "global", "symbol", symbol != null ? symbol : "global");
    }
    String plan = resolvePlan(tenantId);
    return Tags.of("tenant", tenantId.toString(), "plan", plan, "symbol", symbol != null ? symbol : "global");
  }

  public void evict(UUID tenantId) {
    if (tenantId != null) {
      planCache.remove(tenantId);
    }
  }

  private String resolvePlan(UUID tenantId) {
    Instant now = Instant.now(clock);
    CacheEntry cached = planCache.get(tenantId);
    if (cached != null && cached.expiresAt().isAfter(now)) {
      return cached.plan();
    }
    TenantEntity tenant = tenantRepository.findById(tenantId).orElse(null);
    String plan = tenant != null && tenant.getPlan() != null ? tenant.getPlan().name().toLowerCase() : "unknown";
    planCache.put(tenantId, new CacheEntry(plan, now.plus(planCacheTtl)));
    return plan;
  }

  private record CacheEntry(String plan, Instant expiresAt) {}
}
