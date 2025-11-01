package com.bottrading.bandit;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class CanaryBudgetManager {

  private final BanditStore store;
  private final BanditProperties properties;
  private final Clock clock;
  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  public CanaryBudgetManager(BanditStore store, BanditProperties properties, Clock clock) {
    this.store = store;
    this.properties = properties;
    this.clock = clock;
  }

  public boolean canSelectCandidate(String symbol) {
    CacheEntry entry = load(symbol);
    if (entry == null) {
      return true;
    }
    if (entry.candidates >= properties.getCanary().getMaxTradesPerDay()) {
      return false;
    }
    long prospectiveTotal = entry.total + 1;
    long prospectiveCandidates = entry.candidates + 1;
    if (prospectiveTotal == 0) {
      return true;
    }
    double share = (double) prospectiveCandidates / (double) prospectiveTotal;
    return share <= properties.getCanary().getMaxSharePctPerDay();
  }

  public void registerPull(String symbol, boolean candidate) {
    cache.compute(
        symbol,
        (key, existing) -> {
          Instant now = Instant.now(clock);
          LocalDate today = now.atZone(java.time.ZoneOffset.UTC).toLocalDate();
          CacheEntry entry = existing;
          if (entry == null || !entry.day.equals(today)) {
            entry = snapshot(symbol, now);
          }
          entry.total += 1;
          if (candidate) {
            entry.candidates += 1;
          }
          return entry;
        });
  }

  private CacheEntry load(String symbol) {
    Instant now = Instant.now(clock);
    LocalDate today = now.atZone(java.time.ZoneOffset.UTC).toLocalDate();
    CacheEntry entry = cache.get(symbol);
    if (entry == null || !entry.day.equals(today) || entry.stale(now)) {
      entry = snapshot(symbol, now);
      cache.put(symbol, entry);
    }
    return entry;
  }

  private CacheEntry snapshot(String symbol, Instant now) {
    BanditStore.CanaryBudgetSnapshot snapshot = store.canarySnapshot(symbol, now);
    CacheEntry entry = new CacheEntry(snapshot.day(), snapshot.totalPulls(), snapshot.candidatePulls(), now);
    return entry;
  }

  private static final class CacheEntry {
    private final LocalDate day;
    private final Instant fetchedAt;
    private long total;
    private long candidates;

    private CacheEntry(LocalDate day, long total, long candidates, Instant fetchedAt) {
      this.day = day;
      this.total = total;
      this.candidates = candidates;
      this.fetchedAt = fetchedAt;
    }

    private boolean stale(Instant now) {
      return now.isAfter(fetchedAt.plusSeconds(30));
    }
  }
}
