package com.bottrading.throttle;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;

final class RateBudget {

  private static final Duration WINDOW_1S = Duration.ofSeconds(1);
  private static final Duration WINDOW_60S = Duration.ofSeconds(60);

  private final long baseBudget1s;
  private final long baseBudget60s;
  private final long window1sNanos;
  private final long window60sNanos;
  private final Deque<Bucket> window1s = new ArrayDeque<>();
  private final Deque<Bucket> window60s = new ArrayDeque<>();
  private final AtomicLong penaltyUntil = new AtomicLong();

  private long current1s;
  private long current60s;
  private double penaltyMultiplier = 1.0;

  RateBudget(long budget1s, long budget60s) {
    this.baseBudget1s = Math.max(0, budget1s);
    this.baseBudget60s = Math.max(0, budget60s);
    this.window1sNanos = WINDOW_1S.toNanos();
    this.window60sNanos = WINDOW_60S.toNanos();
  }

  long reserve(Endpoint endpoint, long now) {
    purge(now);
    long weight1s = endpoint.getWeight1s();
    long weight60s = endpoint.getWeight60s();
    if (weight1s < 0 || weight60s < 0) {
      throw new IllegalArgumentException("Endpoint weight must be positive");
    }
    long effective1s = effectiveBudget(baseBudget1s);
    long effective60s = effectiveBudget(baseBudget60s);
    if ((weight1s > 0 && effective1s == 0) || (weight60s > 0 && effective60s == 0)) {
      return Long.MAX_VALUE;
    }
    long wait1s = computeWait(weight1s, effective1s, window1s, current1s, window1sNanos, now);
    long wait60s =
        computeWait(weight60s, effective60s, window60s, current60s, window60sNanos, now);
    if (wait1s == 0 && wait60s == 0) {
      if (weight1s > 0) {
        window1s.addLast(new Bucket(now, weight1s));
        current1s += weight1s;
      }
      if (weight60s > 0) {
        window60s.addLast(new Bucket(now, weight60s));
        current60s += weight60s;
      }
      return 0;
    }
    return Math.max(wait1s, wait60s);
  }

  boolean hasBudget(Endpoint endpoint, long now) {
    purge(now);
    long weight1s = endpoint.getWeight1s();
    long weight60s = endpoint.getWeight60s();
    long effective1s = effectiveBudget(baseBudget1s);
    long effective60s = effectiveBudget(baseBudget60s);
    boolean oneSecondOk = weight1s == 0 || current1s + weight1s <= effective1s;
    boolean sixtySecondOk = weight60s == 0 || current60s + weight60s <= effective60s;
    return oneSecondOk && sixtySecondOk;
  }

  long remainingBudget1s(long now) {
    purge(now);
    long effective = effectiveBudget(baseBudget1s);
    return Math.max(0, effective - current1s);
  }

  long remainingBudget60s(long now) {
    purge(now);
    long effective = effectiveBudget(baseBudget60s);
    return Math.max(0, effective - current60s);
  }

  long applyPenalty(double multiplier, long now, long durationNanos) {
    purge(now);
    if (multiplier <= 0) {
      multiplier = 0.1;
    }
    penaltyMultiplier = Math.min(penaltyMultiplier, multiplier);
    long expiry = now + Math.max(0, durationNanos);
    long previous = penaltyUntil.get();
    if (expiry > previous) {
      penaltyUntil.set(expiry);
    }
    return penaltyUntil.get();
  }

  private void purge(long now) {
    purgeWindow(window1s, window1sNanos, now, true);
    purgeWindow(window60s, window60sNanos, now, false);
    if (penaltyMultiplier < 1.0 && now >= penaltyUntil.get()) {
      penaltyMultiplier = 1.0;
    }
  }

  private void purgeWindow(Deque<Bucket> window, long spanNanos, long now, boolean firstWindow) {
    while (!window.isEmpty()) {
      Bucket head = window.peekFirst();
      if (head.timestamp + spanNanos <= now) {
        window.removeFirst();
        if (firstWindow) {
          current1s -= head.weight;
        } else {
          current60s -= head.weight;
        }
      } else {
        break;
      }
    }
    if (current1s < 0) {
      current1s = 0;
    }
    if (current60s < 0) {
      current60s = 0;
    }
  }

  private long computeWait(
      long weight,
      long capacity,
      Deque<Bucket> window,
      long current,
      long span,
      long now) {
    if (weight == 0) {
      return 0;
    }
    if (current + weight <= capacity) {
      return 0;
    }
    long needed = current + weight - capacity;
    long waitUntil = now;
    long remaining = needed;
    for (Bucket bucket : window) {
      remaining -= bucket.weight;
      waitUntil = Math.max(waitUntil, bucket.timestamp + span);
      if (remaining <= 0) {
        break;
      }
    }
    return Math.max(0, waitUntil - now);
  }

  private long effectiveBudget(long base) {
    if (base <= 0) {
      return 0;
    }
    long capped = Math.round(base * penaltyMultiplier);
    return Math.max(1, capped);
  }

  private static final class Bucket {
    private final long timestamp;
    private final long weight;

    private Bucket(long timestamp, long weight) {
      this.timestamp = timestamp;
      this.weight = weight;
    }
  }
}
