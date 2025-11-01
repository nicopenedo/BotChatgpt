package com.bottrading.service.exchange;

import com.bottrading.config.UserDataStreamProperties;
import com.bottrading.execution.PositionManager;
import com.bottrading.execution.PositionManager.ManagedOrderUpdate;
import com.bottrading.model.enums.ManagedOrderStatus;
import com.bottrading.service.binance.BinanceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

@Service
public class UserDataStreamService {

  private static final Logger log = LoggerFactory.getLogger(UserDataStreamService.class);

  private final BinanceClient binanceClient;
  private final PositionManager positionManager;
  private final UserDataStreamProperties properties;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final AtomicReference<String> listenKey = new AtomicReference<>();

  public UserDataStreamService(
      BinanceClient binanceClient,
      PositionManager positionManager,
      UserDataStreamProperties properties) {
    this.binanceClient = binanceClient;
    this.positionManager = positionManager;
    this.properties = properties;
    startListenKey();
  }

  public void startListenKey() {
    try {
      String key = binanceClient.startUserDataStream();
      listenKey.set(key);
      scheduleKeepAlive();
      subscribe(key);
      log.info("User data stream started with listen key {}", key);
    } catch (UnsupportedOperationException ex) {
      log.warn("User data stream not supported by client: {}", ex.getMessage());
    } catch (Exception ex) {
      log.error("Failed to start user data stream: {}", ex.getMessage(), ex);
    }
  }

  private void scheduleKeepAlive() {
    int minutes = Math.max(1, properties.getKeepaliveMinutes() - 1);
    scheduler.scheduleAtFixedRate(
        () -> Optional.ofNullable(listenKey.get()).ifPresent(this::keepAliveListenKey),
        minutes,
        minutes,
        TimeUnit.MINUTES);
  }

  private void keepAliveListenKey(String key) {
    try {
      binanceClient.keepAliveUserDataStream(key);
    } catch (UnsupportedOperationException ex) {
      log.debug("Keep alive not supported: {}", ex.getMessage());
    } catch (Exception ex) {
      log.warn("Failed to keep alive listen key {}: {}", key, ex.getMessage());
    }
  }

  private void subscribe(String key) {
    try {
      binanceClient.connectUserDataStream(key, this::handleMessage, this::handleError);
    } catch (UnsupportedOperationException ex) {
      log.info("User data stream websocket not available: {}", ex.getMessage());
    }
  }

  private void handleMessage(String payload) {
    try {
      JsonNode node = objectMapper.readTree(payload);
      if (node == null) {
        return;
      }
      String eventType = node.path("e").asText();
      if (!Objects.equals(eventType, "executionReport")) {
        return;
      }
      ManagedOrderUpdate update = mapExecutionReport(node);
      positionManager.onOrderUpdate(update);
    } catch (IOException ex) {
      log.warn("Failed to parse user data stream payload: {}", ex.getMessage());
    }
  }

  private ManagedOrderUpdate mapExecutionReport(JsonNode node) {
    String clientOrderId = node.path("c").asText();
    String exchangeOrderId = node.path("i").asText();
    ManagedOrderStatus status = mapStatus(node.path("X").asText());
    BigDecimal lastQty = new BigDecimal(node.path("l").asText("0"));
    BigDecimal cumulative = new BigDecimal(node.path("z").asText("0"));
    BigDecimal price = new BigDecimal(node.path("L").asText(node.path("p").asText("0")));
    Instant eventTime = Instant.ofEpochMilli(node.path("E").asLong());
    return new ManagedOrderUpdate(node.path("s").asText(), clientOrderId, exchangeOrderId, status, lastQty, cumulative, price, eventTime);
  }

  private ManagedOrderStatus mapStatus(String status) {
    return switch (status) {
      case "NEW", "ACCEPTED" -> ManagedOrderStatus.NEW;
      case "PARTIALLY_FILLED" -> ManagedOrderStatus.PARTIAL;
      case "FILLED" -> ManagedOrderStatus.FILLED;
      case "CANCELED" -> ManagedOrderStatus.CANCELED;
      case "REJECTED" -> ManagedOrderStatus.REJECTED;
      case "EXPIRED" -> ManagedOrderStatus.CANCELED;
      default -> ManagedOrderStatus.ERROR;
    };
  }

  private void handleError(Throwable throwable) {
    log.warn("User data stream error: {}", throwable.getMessage());
    restart();
  }

  public void restart() {
    Optional.ofNullable(listenKey.get()).ifPresent(this::close);
    startListenKey();
  }

  private void close(String key) {
    try {
      binanceClient.closeUserDataStream(key);
    } catch (UnsupportedOperationException ex) {
      log.debug("Close user data stream not supported: {}", ex.getMessage());
    } catch (Exception ex) {
      log.warn("Failed to close listen key {}: {}", key, ex.getMessage());
    }
  }

  public void dispatch(ManagedOrderUpdate update) {
    positionManager.onOrderUpdate(update);
  }

  @PreDestroy
  void shutdown() {
    scheduler.shutdownNow();
  }
}
