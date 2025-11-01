package com.bottrading.bandit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CanaryBudgetManagerTest {

  @Test
  void enforcesShareAndCountLimits() {
    BanditProperties properties = new BanditProperties();
    properties.getCanary().setMaxTradesPerDay(2);
    properties.getCanary().setMaxSharePctPerDay(0.25);
    Clock clock = Clock.fixed(Instant.parse("2024-02-01T00:00:00Z"), ZoneOffset.UTC);
    StubStore store = new StubStore(properties, clock);
    String symbol = "BTCUSDT";
    store.setSnapshot(symbol, snapshot(clock, 4, 1));

    CanaryBudgetManager manager = new CanaryBudgetManager(store, properties, clock);

    assertThat(manager.canSelectCandidate(symbol)).isFalse();

    store.setSnapshot(symbol, snapshot(clock, 2, 0));
    assertThat(manager.canSelectCandidate(symbol)).isTrue();

    manager.registerPull(symbol, true);
    assertThat(manager.canSelectCandidate(symbol)).isFalse();
  }

  private BanditStore.CanaryBudgetSnapshot snapshot(Clock clock, long total, long candidate) {
    LocalDate day = Instant.now(clock).atZone(ZoneOffset.UTC).toLocalDate();
    return new BanditStore.CanaryBudgetSnapshot(total, candidate, day);
  }

  private static class StubStore extends BanditStore {
    private final Map<String, CanaryBudgetSnapshot> snapshots = new HashMap<>();
    StubStore(BanditProperties properties, Clock clock) {
      super(null, null, null, clock, properties);
    }

    void setSnapshot(String symbol, CanaryBudgetSnapshot snapshot) {
      snapshots.put(symbol, snapshot);
    }

    @Override
    public CanaryBudgetSnapshot canarySnapshot(String symbol, Instant reference) {
      return snapshots.getOrDefault(
          symbol,
          new CanaryBudgetSnapshot(0L, 0L, reference.atZone(ZoneOffset.UTC).toLocalDate()));
    }
  }
}
