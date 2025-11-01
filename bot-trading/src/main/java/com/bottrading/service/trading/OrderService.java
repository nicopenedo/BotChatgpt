package com.bottrading.service.trading;

import com.bottrading.config.TradingProps;
import com.bottrading.model.dto.AccountBalancesResponse;
import com.bottrading.model.dto.OrderRequest;
import com.bottrading.model.dto.OrderResponse;
import com.bottrading.model.entity.OrderEntity;
import com.bottrading.repository.OrderRepository;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.anomaly.AnomalyDetector;
import com.bottrading.service.risk.RiskGuard;
import com.bottrading.service.risk.TradingState;
import com.bottrading.util.IdGenerator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);

  private final BinanceClient binanceClient;
  private final TradingProps tradingProperties;
  private final TradingState tradingState;
  private final RiskGuard riskGuard;
  private final OrderRepository orderRepository;
  private final Counter ordersSent;
  private final Counter ordersFilled;
  private final AnomalyDetector anomalyDetector;

  public OrderService(
      BinanceClient binanceClient,
      TradingProps tradingProperties,
      TradingState tradingState,
      RiskGuard riskGuard,
      OrderRepository orderRepository,
      MeterRegistry meterRegistry,
      AnomalyDetector anomalyDetector) {
    this.binanceClient = binanceClient;
    this.tradingProperties = tradingProperties;
    this.tradingState = tradingState;
    this.riskGuard = riskGuard;
    this.orderRepository = orderRepository;
    this.ordersSent = meterRegistry.counter("orders.sent");
    this.ordersFilled = meterRegistry.counter("orders.filled");
    this.anomalyDetector = anomalyDetector;
  }

  @Transactional
  public OrderResponse placeOrder(OrderRequest request) {
    String symbol = request.getSymbol() != null ? request.getSymbol() : tradingProperties.getSymbol();
    request.setSymbol(symbol);

    if (!riskGuard.canOpen(symbol)) {
      throw new IllegalStateException("Risk guard prevents trading. Cooldown active.");
    }

    boolean liveAllowed = tradingState.isLiveEnabled() && tradingProperties.isLiveEnabled();
    boolean dryRun = request.isDryRun() || tradingProperties.isDryRun() || !liveAllowed || tradingState.isKillSwitchActive();

    if (dryRun) {
      log.info("Executing dry-run order: {}", request);
      return simulateOrder(request);
    }

    Instant started = Instant.now();
    try {
      OrderResponse response = binanceClient.placeOrder(request);
      long latency = Duration.between(started, Instant.now()).toMillis();
      anomalyDetector.recordApiCall(symbol, latency, true);
      riskGuard.onApiSuccess();
      persistOrder(response, request);
      ordersSent.increment();
      if (response.executedQty().compareTo(BigDecimal.ZERO) > 0) {
        ordersFilled.increment();
      }
      return response;
    } catch (RuntimeException ex) {
      long latency = Duration.between(started, Instant.now()).toMillis();
      anomalyDetector.recordApiCall(symbol, latency, false);
      riskGuard.onApiError();
      throw ex;
    }
  }

  public OrderResponse getOrder(String symbol, String orderId) {
    Optional<OrderEntity> local = orderRepository.findByOrderId(orderId);
    if (local.isPresent()) {
      OrderEntity entity = local.get();
      return new OrderResponse(
          entity.getOrderId(),
          entity.getClientOrderId(),
          entity.getSymbol(),
          entity.getSide(),
          entity.getType(),
          entity.getPrice(),
          entity.getExecutedQty(),
          entity.getQuoteQty(),
          entity.getStatus(),
          entity.getTransactTime());
    }
    return binanceClient.getOrder(symbol, orderId);
  }

  public AccountBalancesResponse getBalances(List<String> assets) {
    return binanceClient.getAccountBalances(assets);
  }

  private OrderResponse simulateOrder(OrderRequest request) {
    String orderId = "SIM-" + System.currentTimeMillis();
    String clientOrderId =
        request.getClientOrderId() != null ? request.getClientOrderId() : IdGenerator.newClientOrderId();
    OrderResponse response =
        new OrderResponse(
            orderId,
            clientOrderId,
            request.getSymbol(),
            request.getSide(),
            request.getType(),
            request.getPrice(),
            Optional.ofNullable(request.getQuantity()).orElse(BigDecimal.ZERO),
            Optional.ofNullable(request.getQuoteAmount()).orElse(BigDecimal.ZERO),
            "DRY_RUN",
            Instant.now());
    persistOrder(response, request);
    return response;
  }

  private void persistOrder(OrderResponse response, OrderRequest original) {
    OrderEntity entity = new OrderEntity();
    entity.setOrderId(response.orderId());
    entity.setClientOrderId(response.clientOrderId());
    entity.setSymbol(response.symbol());
    entity.setSide(response.side());
    entity.setType(response.type());
    entity.setPrice(response.price());
    entity.setQuantity(original.getQuantity());
    entity.setExecutedQty(response.executedQty());
    entity.setQuoteQty(response.cummulativeQuoteQty());
    entity.setStatus(response.status());
    entity.setTransactTime(response.transactTime());
    orderRepository.save(entity);
  }
}
