package com.bottrading.throttle;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

final class BackoffPolicy {

  private static final long MAX_BACKOFF_MS = 60_000;

  private final long baseRateLimitMs;
  private final long baseServerErrorMs;
  private final AtomicInteger consecutiveRateLimits = new AtomicInteger();
  private final AtomicInteger consecutiveServerErrors = new AtomicInteger();

  BackoffPolicy(ThrottleProperties properties) {
    this.baseRateLimitMs = Math.max(1, properties.getOn429().getBackoffMs());
    this.baseServerErrorMs = Math.max(1, properties.getOn5xx().getBackoffMs());
  }

  Optional<BackoffDecision> onError(Throwable throwable) {
    int status = extractStatus(throwable);
    if (status == 429 || status == 418) {
      int attempt = consecutiveRateLimits.incrementAndGet();
      consecutiveServerErrors.set(0);
      return Optional.of(backoffForRateLimit(attempt));
    }
    if (status >= 500 && status < 600) {
      int attempt = consecutiveServerErrors.incrementAndGet();
      consecutiveRateLimits.set(0);
      return Optional.of(backoffForServerError(attempt));
    }
    reset();
    return Optional.empty();
  }

  void onSuccess() {
    reset();
  }

  private BackoffDecision backoffForRateLimit(int attempt) {
    long delayMs = Math.min(MAX_BACKOFF_MS, baseRateLimitMs * (1L << Math.min(attempt - 1, 6)));
    delayMs += jitter(delayMs);
    double multiplier = Math.max(0.1, Math.pow(0.5, attempt));
    return new BackoffDecision(Duration.ofMillis(delayMs), multiplier);
  }

  private BackoffDecision backoffForServerError(int attempt) {
    long delayMs = Math.min(MAX_BACKOFF_MS, baseServerErrorMs * attempt);
    delayMs += jitter(delayMs);
    double multiplier = Math.max(0.3, 1.0 - (attempt * 0.15));
    return new BackoffDecision(Duration.ofMillis(delayMs), multiplier);
  }

  private void reset() {
    consecutiveRateLimits.set(0);
    consecutiveServerErrors.set(0);
  }

  private long jitter(long base) {
    long half = Math.max(1, base / 2);
    return ThreadLocalRandom.current().nextLong(half);
  }

  private int extractStatus(Throwable throwable) {
    Throwable cursor = throwable;
    while (cursor != null) {
      OptionalInt status = extractStatusFrom(cursor);
      if (status.isPresent()) {
        return status.getAsInt();
      }
      cursor = cursor.getCause();
    }
    return -1;
  }

  private OptionalInt extractStatusFrom(Throwable throwable) {
    try {
      Method method = throwable.getClass().getMethod("getHttpStatusCode");
      Object value = method.invoke(throwable);
      if (value instanceof Number number) {
        return OptionalInt.of(number.intValue());
      }
    } catch (ReflectiveOperationException ignored) {
    }
    return OptionalInt.empty();
  }

  record BackoffDecision(Duration delay, double rateMultiplier) {}
}
