package com.bottrading.throttle;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ThrottleTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private Throttle throttle;

  @AfterEach
  void tearDown() {
    if (throttle != null) {
      throttle.shutdown();
    }
    registry.close();
  }

  @Test
  void shouldDelayWhenBudgetExhausted() throws Exception {
    ThrottleProperties properties = new ThrottleProperties();
    properties.setWindow1s(2);
    properties.setWindow60s(100);
    properties.getQueue().setMaxDepthGlobal(10);
    properties.getQueue().setMaxDepthPerSymbol(10);

    throttle = new Throttle(properties, registry);

    CountDownLatch latch = new CountDownLatch(3);
    List<Long> startTimes = new CopyOnWriteArrayList<>();
    List<java.util.concurrent.CompletableFuture<Integer>> futures = new ArrayList<>();

    for (int i = 0; i < 3; i++) {
      int value = i;
      java.util.concurrent.CompletableFuture<Integer> future =
          throttle
              .submit(
                  Endpoint.PRICE_TICKER,
                  "BTCUSDT",
                  () -> {
                    startTimes.add(System.nanoTime());
                    latch.countDown();
                    return value;
                  })
              .toCompletableFuture();
      futures.add(future);
    }

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    for (int i = 0; i < futures.size(); i++) {
      assertThat(futures.get(i).get(2, TimeUnit.SECONDS)).isEqualTo(i);
    }
    long deltaMs = TimeUnit.NANOSECONDS.toMillis(startTimes.get(2) - startTimes.get(0));
    assertThat(deltaMs).isGreaterThanOrEqualTo(900);
  }

  @Test
  void shouldRetryOnRateLimit() throws Exception {
    ThrottleProperties properties = new ThrottleProperties();
    properties.setWindow1s(10);
    properties.setWindow60s(100);
    properties.getQueue().setMaxDepthGlobal(10);
    properties.getQueue().setMaxDepthPerSymbol(10);
    properties.getOn429().setBackoffMs(100);

    throttle = new Throttle(properties, registry);

    AtomicInteger attempts = new AtomicInteger();
    List<Long> times = new CopyOnWriteArrayList<>();

    String result =
        throttle
            .submit(
                Endpoint.PRICE_TICKER,
                "ETHUSDT",
                () -> {
                  times.add(System.nanoTime());
                  if (attempts.getAndIncrement() == 0) {
                    throw new RateLimitedException();
                  }
                  return "ok";
                })
            .toCompletableFuture()
            .get(5, TimeUnit.SECONDS);

    assertThat(result).isEqualTo("ok");
    assertThat(attempts.get()).isEqualTo(2);
    long deltaMs = TimeUnit.NANOSECONDS.toMillis(times.get(1) - times.get(0));
    assertThat(deltaMs).isGreaterThanOrEqualTo(100);
  }

  private static class RateLimitedException extends RuntimeException {
    int getHttpStatusCode() {
      return 429;
    }
  }
}
