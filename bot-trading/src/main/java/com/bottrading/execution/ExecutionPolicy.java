package com.bottrading.execution;

import com.bottrading.config.ExecutionProperties;
import com.bottrading.model.enums.OrderType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ExecutionPolicy {

  private final ExecutionProperties properties;

  public ExecutionPolicy(ExecutionProperties properties) {
    this.properties = properties;
  }

  public OrderPlan planFor(ExecutionRequest request, MarketSnapshot snapshot, TcaModel tcaModel) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(tcaModel, "tcaModel");

    Instant now = Instant.now();
    double expectedMarket = tcaModel.expectedSlippageBps(request.symbol(), OrderType.MARKET, now);
    double expectedLimit = tcaModel.expectedSlippageBps(request.symbol(), OrderType.LIMIT, now);

    if (request.urgency() == ExecutionRequest.Urgency.HIGH) {
      return new MarketPlan();
    }

    if (!Double.isNaN(expectedMarket) && expectedMarket <= request.maxSlippageBps()) {
      return new MarketPlan();
    }

    boolean largeOrder = isLargeOrder(request, snapshot);
    if (largeOrder) {
      double participation = participationRatio(request, snapshot);
      double targetPct = properties.getPov().getTargetPct();
      if (participation > targetPct * 2) {
        int slices = Math.max(2, properties.getTwap().getSlices());
        Duration window = properties.getTwap().windowDuration();
        return new TwapPlan(slices, window);
      }
      return new PovPlan(Math.max(0.01, targetPct));
    }

    boolean spreadWide = snapshot.spreadBps() >= properties.getLimit().getSpreadThresholdBps();
    if (spreadWide && request.urgency() == ExecutionRequest.Urgency.LOW) {
      return new LimitPlan(bufferBpsFor(expectedLimit), properties.getLimit().getTtlMs(), properties.getLimit().getMaxRetries());
    }

    OrderType defaultType = OrderType.valueOf(properties.getDefaultOrder().getType().toUpperCase());
    if (defaultType == OrderType.MARKET) {
      return new MarketPlan();
    }
    return new LimitPlan(bufferBpsFor(expectedLimit), properties.getLimit().getTtlMs(), properties.getLimit().getMaxRetries());
  }

  private boolean isLargeOrder(ExecutionRequest request, MarketSnapshot snapshot) {
    if (snapshot.barVolume() == null || snapshot.barVolume().compareTo(BigDecimal.ZERO) <= 0) {
      return false;
    }
    BigDecimal quantityShare = request.quantity().divide(snapshot.barVolume(), 8, RoundingMode.HALF_UP);
    return quantityShare.compareTo(BigDecimal.valueOf(properties.getPov().getTargetPct())) > 0;
  }

  private double participationRatio(ExecutionRequest request, MarketSnapshot snapshot) {
    if (snapshot.barVolume() == null || snapshot.barVolume().compareTo(BigDecimal.ZERO) <= 0) {
      return 0;
    }
    BigDecimal share = request.quantity().divide(snapshot.barVolume(), 8, RoundingMode.HALF_UP);
    return share.doubleValue();
  }

  private double bufferBpsFor(double expectedLimit) {
    double base = properties.getLimit().getBufferBps();
    if (!Double.isNaN(expectedLimit) && expectedLimit > 0) {
      return Math.max(base, expectedLimit / 2.0);
    }
    return base;
  }

  public sealed interface OrderPlan permits LimitPlan, MarketPlan, TwapPlan, PovPlan {}

  public record LimitPlan(double bufferBps, long ttlMs, int maxRetries) implements OrderPlan {}

  public record MarketPlan() implements OrderPlan {}

  public record TwapPlan(int slices, Duration window) implements OrderPlan {}

  public record PovPlan(double targetParticipation) implements OrderPlan {}

  public interface TcaModel {
    double expectedSlippageBps(String symbol, OrderType type, Instant timestamp);
  }
}
