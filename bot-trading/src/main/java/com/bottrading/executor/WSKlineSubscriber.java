package com.bottrading.executor;

import com.bottrading.config.BinanceProperties;
import com.bottrading.service.health.HealthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WSKlineSubscriber {

  private static final Logger log = LoggerFactory.getLogger(WSKlineSubscriber.class);

  private final BinanceProperties binanceProperties;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final ScheduledExecutorService scheduler;
  private final HealthService healthService;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean healthy = new AtomicBoolean(false);
  private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

  private volatile WebSocket webSocket;
  private volatile Consumer<KlineEvent> listener;
  private volatile String symbol;
  private volatile String interval;

  public WSKlineSubscriber(BinanceProperties binanceProperties, HealthService healthService) {
    this.binanceProperties = binanceProperties;
    this.healthService = healthService;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.objectMapper = new ObjectMapper();
    this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "ws-kline-subscriber");
      t.setDaemon(true);
      return t;
    });
  }

  public void start(String symbol, String interval, Consumer<KlineEvent> listener) {
    this.symbol = Objects.requireNonNull(symbol, "symbol");
    this.interval = Objects.requireNonNull(interval, "interval");
    this.listener = Objects.requireNonNull(listener, "listener");
    if (running.compareAndSet(false, true)) {
      log.info("Starting kline websocket stream for {} {}", symbol, interval);
      connect(0);
    }
  }

  public boolean isHealthy() {
    return healthy.get();
  }

  public void stop() {
    running.set(false);
    healthy.set(false);
    if (webSocket != null) {
      try {
        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
      } catch (Exception ignored) {
      }
    }
    scheduler.shutdownNow();
  }

  @PreDestroy
  public void destroy() {
    stop();
  }

  private void connect(int attempt) {
    if (!running.get()) {
      return;
    }
    String stream = symbol.toLowerCase() + "@kline_" + interval;
    String url = binanceProperties.streamUrl();
    URI uri = URI.create(url.endsWith("/") ? url + stream : url + "/" + stream);
    log.info("Connecting to Binance websocket {} (attempt #{})", uri, attempt + 1);
    httpClient
        .newWebSocketBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .buildAsync(uri, new KlineListener())
        .whenComplete(
            (ws, err) -> {
              if (err != null) {
                log.warn("Failed to connect to websocket: {}", err.getMessage());
                scheduleReconnect();
              } else {
                this.webSocket = ws;
              }
            });
  }

  private void scheduleReconnect() {
    healthy.set(false);
    healthService.onWebsocketReconnect();
    if (!running.get()) {
      return;
    }
    int attempt = reconnectAttempts.incrementAndGet();
    long delay = Math.min(60, (long) Math.pow(2, Math.max(0, attempt - 1)));
    log.info("Scheduling websocket reconnect in {}s (attempt #{})", delay, attempt + 1);
    scheduler.schedule(() -> connect(attempt), delay, TimeUnit.SECONDS);
  }

  private class KlineListener implements Listener {

    private final StringBuilder buffer = new StringBuilder();

    @Override
    public void onOpen(WebSocket webSocket) {
      reconnectAttempts.set(0);
      healthy.set(true);
      log.info("Websocket stream opened for {} {}", symbol, interval);
      webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      buffer.append(data);
      if (last) {
        String message = buffer.toString();
        buffer.setLength(0);
        handleMessage(message);
      }
      webSocket.request(1);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      log.error("Websocket error: {}", error.getMessage(), error);
      scheduleReconnect();
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      log.info("Websocket closed code={} reason={}", statusCode, reason);
      scheduleReconnect();
      return Listener.super.onClose(webSocket, statusCode, reason);
    }

    private void handleMessage(String message) {
      try {
        JsonNode node = objectMapper.readTree(message);
        JsonNode klineNode = node.path("k");
        if (klineNode.isMissingNode()) {
          return;
        }
        boolean closed = klineNode.path("x").asBoolean(false);
        if (!closed) {
          return;
        }
        String eventSymbol = klineNode.path("s").asText(symbol);
        String eventInterval = klineNode.path("i").asText(interval);
        long closeTime = klineNode.path("T").asLong();
        Consumer<KlineEvent> currentListener = listener;
        if (currentListener != null) {
          currentListener.accept(new KlineEvent(eventSymbol, eventInterval, closeTime, true));
        }
      } catch (IOException ex) {
        log.warn("Failed to parse websocket payload: {}", ex.getMessage());
      }
    }
  }
}
