package com.bottrading.throttle;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class Throttle {
  private static final String GLOBAL_SYMBOL = "__GLOBAL__";
  private static final int MAX_RETRIES = 5;
  private static final AtomicInteger WORKER_COUNTER = new AtomicInteger();
  private static final AtomicInteger SCHEDULER_COUNTER = new AtomicInteger();

  private final RateBudget budget;
  private final BackoffPolicy backoffPolicy;
  private final MeterRegistry meterRegistry;
  private final ReentrantLock lock = new ReentrantLock();
  private final Deque<ThrottleTask<?>> globalQueue = new ArrayDeque<>();
  private final Map<String, SymbolQueue> symbolQueues = new HashMap<>();
  private final int maxPerSymbol;
  private final int maxGlobal;
  private final ScheduledExecutorService scheduler;
  private final ExecutorService workers;
  private final AtomicInteger globalDepth = new AtomicInteger();
  private final Counter rejectionCounter;
  private final DistributionSummary delaySummary;

  public Throttle(ThrottleProperties properties, MeterRegistry meterRegistry) {
    this(
        properties,
        meterRegistry,
        Executors.newCachedThreadPool(threadFactory("throttle-worker-", WORKER_COUNTER)),
        Executors.newSingleThreadScheduledExecutor(
            threadFactory("throttle-scheduler-", SCHEDULER_COUNTER)));
  }

  Throttle(
      ThrottleProperties properties,
      MeterRegistry meterRegistry,
      ExecutorService workers,
      ScheduledExecutorService scheduler) {
    Objects.requireNonNull(properties, "properties");
    Objects.requireNonNull(meterRegistry, "meterRegistry");
    this.meterRegistry = meterRegistry;
    this.budget = new RateBudget(properties.getWindow1s(), properties.getWindow60s());
    this.backoffPolicy = new BackoffPolicy(properties);
    this.maxPerSymbol = Math.max(0, properties.getQueue().getMaxDepthPerSymbol());
    this.maxGlobal = Math.max(0, properties.getQueue().getMaxDepthGlobal());
    this.workers = workers;
    this.scheduler = scheduler;
    this.rejectionCounter =
        Counter.builder("throttle.rejections").description("Tasks rejected due to queue limits").register(meterRegistry);
    this.delaySummary =
        DistributionSummary.builder("throttle.delays.ms")
            .baseUnit("milliseconds")
            .description("Delay between submission and execution")
            .register(meterRegistry);
    meterRegistry.gauge("queue.depth", Tags.of("symbol", "global"), globalDepth);
    meterRegistry.gauge(
        "throttle.budget.remaining",
        Tags.of("window", "1s"),
        budget,
        value -> budget.remainingBudget1s(System.nanoTime()));
    meterRegistry.gauge(
        "throttle.budget.remaining",
        Tags.of("window", "60s"),
        budget,
        value -> budget.remainingBudget60s(System.nanoTime()));
  }

  public <T> CompletionStage<T> submit(Endpoint endpoint, String symbol, Supplier<T> supplier) {
    Objects.requireNonNull(endpoint, "endpoint");
    Objects.requireNonNull(supplier, "supplier");
    CompletableFuture<T> future = new CompletableFuture<>();
    ThrottleTask<T> task = new ThrottleTask<>(endpoint, normalizeSymbol(symbol), supplier, future);
    boolean accepted;
    lock.lock();
    try {
      accepted = enqueue(task);
    } finally {
      lock.unlock();
    }
    if (!accepted) {
      rejectionCounter.increment();
      future.completeExceptionally(
          new RejectedExecutionException("Throttle queues are at capacity for symbol " + task.symbol));
      return future;
    }
    triggerDispatch();
    return future;
  }

  public boolean canSchedule(Endpoint endpoint, String symbol) {
    Objects.requireNonNull(endpoint, "endpoint");
    String normalized = normalizeSymbol(symbol);
    long now = System.nanoTime();
    lock.lock();
    try {
      if (maxGlobal > 0 && globalQueue.size() >= maxGlobal) {
        return false;
      }
      SymbolQueue queue = symbolQueues.get(normalized);
      if (maxPerSymbol > 0 && queue != null && queue.depth.get() >= maxPerSymbol) {
        return false;
      }
      return budget.hasBudget(endpoint, now);
    } finally {
      lock.unlock();
    }
  }

  @PreDestroy
  public void shutdown() {
    workers.shutdownNow();
    scheduler.shutdownNow();
  }

  private boolean enqueue(ThrottleTask<?> task) {
    if (maxGlobal > 0 && globalQueue.size() >= maxGlobal) {
      return false;
    }
    SymbolQueue queue = symbolQueues.computeIfAbsent(task.symbol, this::createQueue);
    if (maxPerSymbol > 0 && queue.depth.get() >= maxPerSymbol) {
      return false;
    }
    queue.enqueue(task);
    globalQueue.addLast(task);
    globalDepth.set(globalQueue.size());
    return true;
  }

  private void enqueueRetry(ThrottleTask<?> task) {
    SymbolQueue queue = symbolQueues.computeIfAbsent(task.symbol, this::createQueue);
    queue.enqueue(task);
    globalQueue.addLast(task);
    globalDepth.set(globalQueue.size());
  }

  private SymbolQueue createQueue(String symbol) {
    SymbolQueue queue = new SymbolQueue(symbol);
    meterRegistry.gauge(
        "queue.depth", Tags.of("symbol", symbol), queue, value -> value.depth.get());
    return queue;
  }

  private void triggerDispatch() {
    scheduler.execute(this::dispatch);
  }

  private void scheduleDispatch(long delayNanos) {
    if (delayNanos <= 0) {
      triggerDispatch();
    } else {
      scheduler.schedule(this::dispatch, delayNanos, TimeUnit.NANOSECONDS);
    }
  }

  private void dispatch() {
    while (true) {
      ThrottleTask<?> task;
      long now = System.nanoTime();
      lock.lock();
      try {
        if (globalQueue.isEmpty()) {
          return;
        }
        task = globalQueue.peekFirst();
        if (task == null) {
          return;
        }
        if (now < task.earliestDispatchTime) {
          scheduleDispatch(task.earliestDispatchTime - now);
          return;
        }
        long wait = budget.reserve(task.endpoint, now);
        if (wait > 0) {
          scheduleDispatch(wait);
          return;
        }
        globalQueue.removeFirst();
        globalDepth.set(globalQueue.size());
        SymbolQueue queue = symbolQueues.get(task.symbol);
        if (queue != null) {
          queue.dequeue(task);
        }
      } finally {
        lock.unlock();
      }
      executeTask(task, now);
    }
  }

  private <T> void executeTask(ThrottleTask<T> task, long dispatchTime) {
    long delay = Math.max(0, dispatchTime - task.submittedAt);
    delaySummary.record(delay / 1_000_000.0);
    workers.submit(
        () -> {
          try {
            T result = task.supplier.get();
            backoffPolicy.onSuccess();
            task.future.complete(result);
          } catch (Throwable ex) {
            handleFailure(task, ex);
            return;
          }
          triggerDispatch();
        });
  }

  private <T> void handleFailure(ThrottleTask<T> task, Throwable error) {
    Throwable cause = unwrap(error);
    Optional<BackoffPolicy.BackoffDecision> decision = backoffPolicy.onError(cause);
    if (decision.isPresent() && task.attempt.incrementAndGet() <= MAX_RETRIES) {
      BackoffPolicy.BackoffDecision backoff = decision.get();
      long now = System.nanoTime();
      Duration delay = backoff.delay();
      task.earliestDispatchTime = now + delay.toNanos();
      lock.lock();
      try {
        enqueueRetry(task);
      } finally {
        lock.unlock();
      }
      long expiry = budget.applyPenalty(backoff.rateMultiplier(), now, delay.toNanos());
      scheduleDispatch(delay.toNanos());
      if (expiry > now) {
        scheduleDispatch(expiry - now);
      }
      triggerDispatch();
      return;
    }
    backoffPolicy.onSuccess();
    task.future.completeExceptionally(cause);
    triggerDispatch();
  }

  private Throwable unwrap(Throwable error) {
    Throwable current = error;
    while (current instanceof java.util.concurrent.ExecutionException
        || current instanceof java.util.concurrent.CompletionException) {
      if (current.getCause() == null) {
        break;
      }
      current = current.getCause();
    }
    if (current instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
    return current;
  }

  private String normalizeSymbol(String symbol) {
    return symbol == null || symbol.isBlank() ? GLOBAL_SYMBOL : symbol.toUpperCase();
  }

  private static final class ThrottleTask<T> {
    private final Endpoint endpoint;
    private final String symbol;
    private final Supplier<T> supplier;
    private final CompletableFuture<T> future;
    private final long submittedAt;
    private final AtomicInteger attempt = new AtomicInteger(0);
    private volatile long earliestDispatchTime;

    private ThrottleTask(
        Endpoint endpoint, String symbol, Supplier<T> supplier, CompletableFuture<T> future) {
      this.endpoint = endpoint;
      this.symbol = symbol;
      this.supplier = supplier;
      this.future = future;
      this.submittedAt = System.nanoTime();
      this.earliestDispatchTime = this.submittedAt;
    }
  }

  private final class SymbolQueue {
    private final String symbol;
    private final Deque<ThrottleTask<?>> queue = new ArrayDeque<>();
    private final AtomicInteger depth = new AtomicInteger();

    private SymbolQueue(String symbol) {
      this.symbol = symbol;
    }

    private void enqueue(ThrottleTask<?> task) {
      queue.addLast(task);
      depth.set(queue.size());
    }

    private void dequeue(ThrottleTask<?> task) {
      if (!queue.isEmpty() && queue.peekFirst() == task) {
        queue.pollFirst();
      } else {
        queue.remove(task);
      }
      depth.set(queue.size());
    }
  }

  private static ThreadFactory threadFactory(String prefix, AtomicInteger counter) {
    return runnable -> {
      Thread thread = new Thread(runnable);
      thread.setName(prefix + counter.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };
  }
}
