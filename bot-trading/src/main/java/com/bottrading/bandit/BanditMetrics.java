package com.bottrading.bandit;

import com.bottrading.saas.service.TenantMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class BanditMetrics {

  private final MeterRegistry meterRegistry;
  private final Map<String, DistributionSummary> rewardSummaries = new ConcurrentHashMap<>();
  private final Map<String, Counter> pullCounters = new ConcurrentHashMap<>();
  private final Map<String, Counter> blockedCounters = new ConcurrentHashMap<>();
  private final Map<String, AtomicReference<Double>> shareGauges = new ConcurrentHashMap<>();
  private final TenantMetrics tenantMetrics;

  public BanditMetrics(MeterRegistry meterRegistry, TenantMetrics tenantMetrics) {
    this.meterRegistry = meterRegistry;
    this.tenantMetrics = tenantMetrics;
  }

  public void incrementPull(BanditArmEntity arm) {
    counterFor(arm, "bandit.pull.count").increment();
  }

  public void recordReward(BanditArmEntity arm, double reward) {
    summaryFor(arm, "bandit.reward.avg").record(reward);
  }

  public void incrementBlocked(String reason) {
    blockedCounters
        .computeIfAbsent(
            reason,
            key ->
                meterRegistry.counter(
                    "bandit.blocked.count",
                    Tags.concat(tenantMetrics.tags(null), Tags.of("reason", key))))
        .increment();
  }

  public void updateCanaryShare(String symbol, double share) {
    shareGauges
        .computeIfAbsent(
            symbol,
            key -> {
              AtomicReference<Double> ref = new AtomicReference<>(0.0);
              meterRegistry.gauge(
                  "bandit.canary.share",
                  tenantMetrics.tags(key),
                  ref,
                  AtomicReference::get);
              return ref;
            })
        .set(share);
  }

  public void registerAlgorithm(String algorithm) {
    meterRegistry.gauge(
        "bandit.algorithm", Tags.concat(tenantMetrics.tags(null), Tags.of("value", algorithm)), 1);
  }

  private Counter counterFor(BanditArmEntity arm, String metric) {
    String key = metricKey(arm);
    return pullCounters.computeIfAbsent(
        metric + key,
        k ->
            meterRegistry.counter(
                metric,
                Tags.concat(
                    tenantMetrics.tags(arm.getSymbol()),
                    Tags.of(
                        "regime",
                        arm.getRegime(),
                        "side",
                        arm.getSide().name(),
                        "preset",
                        arm.getPresetId().toString()))));
  }

  private DistributionSummary summaryFor(BanditArmEntity arm, String metric) {
    String key = metricKey(arm);
    return rewardSummaries.computeIfAbsent(
        metric + key,
        k ->
            DistributionSummary.builder(metric)
                .tags(
                    Tags.concat(
                        tenantMetrics.tags(arm.getSymbol()),
                        Tags.of(
                            "regime",
                            arm.getRegime(),
                            "side",
                            arm.getSide().name(),
                            "preset",
                            arm.getPresetId().toString())))
                .register(meterRegistry));
  }

  private String metricKey(BanditArmEntity arm) {
    return arm.getSymbol() + arm.getRegime() + arm.getSide() + arm.getPresetId();
  }
}
