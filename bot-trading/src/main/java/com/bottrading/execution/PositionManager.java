package com.bottrading.execution;

import com.bottrading.config.OcoProperties;
import com.bottrading.config.PositionManagerProperties;
import com.bottrading.model.entity.ManagedOrderEntity;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.entity.TradeEntity;
import com.bottrading.model.enums.ManagedOrderStatus;
import com.bottrading.model.enums.ManagedOrderType;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PositionStatus;
import com.bottrading.notify.TelegramNotifier;
import com.bottrading.repository.ManagedOrderRepository;
import com.bottrading.repository.PositionRepository;
import com.bottrading.repository.TradeRepository;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.risk.drift.DriftWatchdog;
import com.bottrading.util.IdGenerator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PositionManager {

  private static final Logger log = LoggerFactory.getLogger(PositionManager.class);

  private final PositionRepository positionRepository;
  private final ManagedOrderRepository managedOrderRepository;
  private final TradeRepository tradeRepository;
  private final BinanceClient binanceClient;
  private final TelegramNotifier notifier;
  private final OcoProperties ocoProperties;
  private final Duration lockTimeout;
  private final Clock clock;
  private final Counter positionsOpened;
  private final Counter positionsClosed;
  private final Counter ordersPartial;
  private final Counter ordersFilled;
  private final Counter ordersCanceled;
  private final Counter ocoCorrections;
  private final DriftWatchdog driftWatchdog;
  private final ConcurrentMap<Long, ReentrantLock> positionLocks = new ConcurrentHashMap<>();

  public PositionManager(
      PositionRepository positionRepository,
      ManagedOrderRepository managedOrderRepository,
      TradeRepository tradeRepository,
      BinanceClient binanceClient,
      TelegramNotifier notifier,
      OcoProperties ocoProperties,
      PositionManagerProperties positionManagerProperties,
      MeterRegistry meterRegistry,
      Optional<Clock> clock,
      DriftWatchdog driftWatchdog) {
    this.positionRepository = positionRepository;
    this.managedOrderRepository = managedOrderRepository;
    this.tradeRepository = tradeRepository;
    this.binanceClient = binanceClient;
    this.notifier = notifier;
    this.ocoProperties = ocoProperties;
    this.lockTimeout = Duration.ofMillis(positionManagerProperties.getLockTimeoutMs());
    this.clock = clock.orElse(Clock.systemUTC());
    this.positionsOpened = meterRegistry.counter("positions.opened");
    this.positionsClosed = meterRegistry.counter("positions.closed");
    this.ordersPartial = meterRegistry.counter("orders.partial");
    this.ordersFilled = meterRegistry.counter("orders.filled");
    this.ordersCanceled = meterRegistry.counter("orders.canceled");
    this.ocoCorrections = meterRegistry.counter("oco.corrections");
    this.driftWatchdog = driftWatchdog;
  }

  @Transactional
  public PositionEntity openPosition(OpenPositionCommand command) {
    Objects.requireNonNull(command.symbol(), "symbol");
    Objects.requireNonNull(command.side(), "side");
    Objects.requireNonNull(command.entryPrice(), "entryPrice");
    Objects.requireNonNull(command.quantity(), "quantity");

    PositionEntity entity = new PositionEntity();
    entity.setSymbol(command.symbol());
    entity.setSide(command.side());
    entity.setEntryPrice(command.entryPrice());
    entity.setQtyInit(command.quantity());
    entity.setQtyRemaining(command.quantity());
    entity.setStopLoss(command.stopLoss());
    entity.setTakeProfit(command.takeProfit());
    entity.setTrailingConf(command.trailingConfigurationJson());
    entity.setOpenedAt(Instant.now(clock));
    entity.setLastUpdateAt(entity.getOpenedAt());
    entity.setStatus(PositionStatus.OPEN);
    entity.setCorrelationId(command.correlationId());
    PositionEntity saved = positionRepository.save(entity);
    positionLocks.putIfAbsent(saved.getId(), new ReentrantLock());

    ManagedOrderEntity sl = createChildOrder(saved, ManagedOrderType.STOP_LOSS, command.stopLoss(), command.stopLoss(), command.quantity());
    ManagedOrderEntity tp = createChildOrder(saved, ManagedOrderType.TAKE_PROFIT, command.takeProfit(), null, command.quantity());

    placeProtectiveOrders(saved, Map.of(ManagedOrderType.STOP_LOSS, sl, ManagedOrderType.TAKE_PROFIT, tp));

    notifier.notifyPositionOpened(saved.getSymbol(), saved.getSide(), saved.getQtyInit(), saved.getEntryPrice(), saved.getCorrelationId());
    positionsOpened.increment();
    return saved;
  }

  private ManagedOrderEntity createChildOrder(
      PositionEntity position, ManagedOrderType type, BigDecimal price, BigDecimal stopPrice, BigDecimal quantity) {
    ManagedOrderEntity entity = new ManagedOrderEntity();
    entity.setPosition(position);
    entity.setType(type);
    entity.setSide(position.getSide() == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY);
    entity.setPrice(price);
    entity.setStopPrice(stopPrice);
    entity.setQuantity(quantity);
    entity.setFilledQuantity(BigDecimal.ZERO);
    entity.setStatus(ManagedOrderStatus.NEW);
    entity.setCreatedAt(Instant.now(clock));
    entity.setUpdatedAt(entity.getCreatedAt());
    entity.setClientOrderId(IdGenerator.newClientOrderId() + "-" + type.name());
    return managedOrderRepository.save(entity);
  }

  private void placeProtectiveOrders(PositionEntity position, Map<ManagedOrderType, ManagedOrderEntity> orders) {
    try {
      boolean ocoCreated = binanceClient.placeOcoOrder(position.getSymbol(), orders.get(ManagedOrderType.STOP_LOSS), orders.get(ManagedOrderType.TAKE_PROFIT));
      if (!ocoCreated && ocoProperties.getClientEmulation().isEnabled()) {
        log.debug("Native OCO unavailable for {}, registering emulation", position.getSymbol());
        createSingleProtectiveOrder(orders.get(ManagedOrderType.STOP_LOSS));
        createSingleProtectiveOrder(orders.get(ManagedOrderType.TAKE_PROFIT));
      }
    } catch (UnsupportedOperationException ex) {
      log.warn("Binance client does not support native OCO: {}", ex.getMessage());
      if (ocoProperties.getClientEmulation().isEnabled()) {
        createSingleProtectiveOrder(orders.get(ManagedOrderType.STOP_LOSS));
        createSingleProtectiveOrder(orders.get(ManagedOrderType.TAKE_PROFIT));
      }
    }
  }

  private void createSingleProtectiveOrder(ManagedOrderEntity order) {
    binanceClient.placeChildOrder(order);
    order.setStatus(ManagedOrderStatus.WORKING);
    order.setUpdatedAt(Instant.now(clock));
    managedOrderRepository.save(order);
  }

  @Transactional
  public void onOrderUpdate(ManagedOrderUpdate update) {
    if (update == null || update.clientOrderId() == null) {
      return;
    }
    ManagedOrderEntity order = managedOrderRepository.findByClientOrderId(update.clientOrderId()).orElse(null);
    if (order == null) {
      log.debug("Received update for unmanaged order {}", update.clientOrderId());
      return;
    }
    PositionEntity position = order.getPosition();
    ReentrantLock lock = positionLocks.computeIfAbsent(position.getId(), id -> new ReentrantLock());
    if (!acquire(lock)) {
      log.warn("Could not acquire lock for position {}", position.getId());
      return;
    }
    try {
      if (order.getStatus() == update.status() && Objects.equals(order.getFilledQuantity(), update.cumulativeFilledQty())) {
        return; // idempotent
      }
      order.setStatus(update.status());
      order.setFilledQuantity(update.cumulativeFilledQty());
      order.setExchangeOrderId(update.exchangeOrderId());
      order.setUpdatedAt(update.eventTime());
      managedOrderRepository.save(order);

      position.setLastUpdateAt(update.eventTime());

      switch (update.status()) {
        case PARTIAL -> handlePartialFill(position, order, update.lastFilledQty(), update.price());
        case FILLED -> handleFullFill(position, order, update.lastFilledQty(), update.price());
        case CANCELED -> {
          ordersCanceled.increment();
        }
        case REJECTED, ERROR -> {
          position.setStatus(PositionStatus.ERROR);
          positionRepository.save(position);
        }
        default -> {}
      }
    } finally {
      lock.unlock();
    }
  }

  private void handlePartialFill(PositionEntity position, ManagedOrderEntity order, BigDecimal lastFilled, BigDecimal price) {
    ordersPartial.increment();
    notifier.notifyPartialFill(position.getSymbol(), order.getSide(), lastFilled, price, order.getClientOrderId());
    reducePositionQuantity(position, lastFilled);
    managedOrderRepository.save(order);
    positionRepository.save(position);
    adjustOppositeQuantity(position, order, lastFilled);
  }

  private void handleFullFill(PositionEntity position, ManagedOrderEntity order, BigDecimal lastFilled, BigDecimal price) {
    ordersFilled.increment();
    reducePositionQuantity(position, lastFilled);
    TradeEntity trade = new TradeEntity();
    trade.setPosition(position);
    trade.setOrderId(order.getExchangeOrderId());
    trade.setQuantity(lastFilled);
    trade.setPrice(price);
    trade.setSide(order.getSide());
    trade.setExecutedAt(Instant.now(clock));
    tradeRepository.save(trade);

    BigDecimal incrementalPnl = incrementalPnl(position, lastFilled, price);
    if (incrementalPnl != null) {
      driftWatchdog.recordLiveTrade(position.getSymbol(), incrementalPnl.doubleValue());
    }

    ManagedOrderType type = order.getType();
    if (type == ManagedOrderType.TAKE_PROFIT) {
      notifier.notifyTakeProfit(position.getSymbol(), order.getSide(), price, realisedPnl(position, price));
      cancelOpposite(position, ManagedOrderType.STOP_LOSS, order);
    } else if (type == ManagedOrderType.STOP_LOSS) {
      notifier.notifyStopHit(position.getSymbol(), order.getSide(), price, realisedPnl(position, price));
      cancelOpposite(position, ManagedOrderType.TAKE_PROFIT, order);
    }

    if (position.getQtyRemaining().compareTo(minExecutable(position)) <= 0) {
      closePosition(position);
    } else {
      positionRepository.save(position);
    }
  }

  private BigDecimal realisedPnl(PositionEntity position, BigDecimal exitPrice) {
    BigDecimal diff = exitPrice.subtract(position.getEntryPrice());
    if (position.getSide() == OrderSide.SELL) {
      diff = diff.negate();
    }
    return diff.multiply(position.getQtyInit()).setScale(8, RoundingMode.HALF_UP);
  }

  private BigDecimal incrementalPnl(PositionEntity position, BigDecimal filledQty, BigDecimal exitPrice) {
    if (filledQty == null || exitPrice == null) {
      return null;
    }
    BigDecimal diff = exitPrice.subtract(position.getEntryPrice());
    if (position.getSide() == OrderSide.SELL) {
      diff = diff.negate();
    }
    return diff.multiply(filledQty).setScale(8, RoundingMode.HALF_UP);
  }

  private void adjustOppositeQuantity(PositionEntity position, ManagedOrderEntity filled, BigDecimal qtyDelta) {
    ManagedOrderType oppositeType = filled.getType() == ManagedOrderType.STOP_LOSS ? ManagedOrderType.TAKE_PROFIT : ManagedOrderType.STOP_LOSS;
    managedOrderRepository
        .findByPositionAndType(position, oppositeType)
        .ifPresent(
            other -> {
              BigDecimal remaining = other.getQuantity().subtract(qtyDelta);
              if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                cancelOrder(other);
              } else {
                other.setQuantity(remaining);
                other.setUpdatedAt(Instant.now(clock));
                managedOrderRepository.save(other);
              }
            });
  }

  private void cancelOpposite(PositionEntity position, ManagedOrderType oppositeType, ManagedOrderEntity completed) {
    managedOrderRepository
        .findByPositionAndType(position, oppositeType)
        .ifPresent(
            other -> {
              if (other.getStatus() == ManagedOrderStatus.CANCELED || other.getStatus() == ManagedOrderStatus.FILLED) {
                return;
              }
              try {
                binanceClient.cancelOrder(other);
              } catch (Exception ex) {
                log.warn("Failed to cancel opposite order {} after fill {}: {}", other.getClientOrderId(), completed.getClientOrderId(), ex.getMessage());
                if (ocoProperties.getClientEmulation().getCancelGraceMillis() > 0) {
                  ocoCorrections.increment();
                }
              }
              other.setStatus(ManagedOrderStatus.CANCELED);
              other.setUpdatedAt(Instant.now(clock));
              managedOrderRepository.save(other);
            });
  }

  @Transactional
  public void replaceStop(long positionId, BigDecimal newStopPrice, BigDecimal quantity) {
    PositionEntity position = positionRepository.findById(positionId).orElseThrow();
    ReentrantLock lock = positionLocks.computeIfAbsent(positionId, id -> new ReentrantLock());
    if (!acquire(lock)) {
      throw new IllegalStateException("Position lock busy");
    }
    try {
      managedOrderRepository
          .findByPositionAndType(position, ManagedOrderType.STOP_LOSS)
          .ifPresent(
              order -> {
                cancelOrder(order);
                order.setPrice(newStopPrice);
                order.setStopPrice(newStopPrice);
                order.setQuantity(quantity);
                order.setStatus(ManagedOrderStatus.NEW);
                order.setCreatedAt(Instant.now(clock));
                order.setUpdatedAt(order.getCreatedAt());
                managedOrderRepository.save(order);
                binanceClient.placeChildOrder(order);
              });
      position.setStopLoss(newStopPrice);
      position.setLastUpdateAt(Instant.now(clock));
      positionRepository.save(position);
    } finally {
      lock.unlock();
    }
  }

  @Transactional
  public void closePosition(long positionId) {
    PositionEntity position = positionRepository.findById(positionId).orElseThrow();
    closePosition(position);
  }

  private void closePosition(PositionEntity position) {
    position.setStatus(PositionStatus.CLOSED);
    position.setClosedAt(Instant.now(clock));
    position.setQtyRemaining(BigDecimal.ZERO);
    position.setLastUpdateAt(position.getClosedAt());
    positionRepository.save(position);
    managedOrderRepository
        .findByPosition(position)
        .forEach(
            order -> {
              if (order.getStatus() == ManagedOrderStatus.WORKING || order.getStatus() == ManagedOrderStatus.NEW || order.getStatus() == ManagedOrderStatus.PARTIAL) {
                cancelOrder(order);
                order.setStatus(ManagedOrderStatus.CANCELED);
                order.setUpdatedAt(Instant.now(clock));
                managedOrderRepository.save(order);
              }
            });
    positionsClosed.increment();
  }

  private void cancelOrder(ManagedOrderEntity order) {
    try {
      binanceClient.cancelOrder(order);
    } catch (Exception ex) {
      log.warn("Failed to cancel order {}: {}", order.getClientOrderId(), ex.getMessage());
    }
  }

  @Transactional
  public void forceCloseAll() {
    positionRepository
        .findByStatus(PositionStatus.OPEN)
        .forEach(
            position -> {
              try {
                closePosition(position);
              } catch (Exception ex) {
                log.warn(
                    "Failed to force close position {}: {}",
                    position.getId(),
                    ex.getMessage());
              }
            });
  }

  @Transactional
  public ReconciliationReport reconcile(Collection<ExternalOrderSnapshot> snapshots) {
    int adopted = 0;
    int cancelled = 0;
    for (ExternalOrderSnapshot snapshot : snapshots) {
      ManagedOrderEntity order = managedOrderRepository.findByClientOrderId(snapshot.clientOrderId()).orElse(null);
      if (order == null) {
        notifier.notifyReconciledItem(snapshot.symbol(), "adopting order " + snapshot.clientOrderId());
        adopted++;
        continue;
      }
      if (snapshot.status() == ManagedOrderStatus.CANCELED || snapshot.status() == ManagedOrderStatus.FILLED) {
        order.setStatus(snapshot.status());
        order.setFilledQuantity(snapshot.executedQty());
        order.setExchangeOrderId(snapshot.exchangeOrderId());
        order.setUpdatedAt(snapshot.eventTime());
        managedOrderRepository.save(order);
      } else if (order.getStatus() == ManagedOrderStatus.CANCELED && snapshot.status() == ManagedOrderStatus.WORKING) {
        notifier.notifyOcoCorrected(snapshot.symbol(), order.getClientOrderId(), snapshot.exchangeOrderId(), Instant.now(clock));
        ocoCorrections.increment();
        cancelOrder(order);
        cancelled++;
      }
    }
    return new ReconciliationReport(adopted, cancelled);
  }

  private boolean acquire(ReentrantLock lock) {
    try {
      return lock.tryLock(lockTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private void reducePositionQuantity(PositionEntity position, BigDecimal filledQty) {
    if (filledQty == null) {
      return;
    }
    BigDecimal remaining = position.getQtyRemaining().subtract(filledQty);
    position.setQtyRemaining(remaining.max(BigDecimal.ZERO));
  }

  private BigDecimal minExecutable(PositionEntity position) {
    return position.getQtyInit().multiply(BigDecimal.valueOf(0.0001));
  }

  public record ManagedOrderUpdate(
      String symbol,
      String clientOrderId,
      String exchangeOrderId,
      ManagedOrderStatus status,
      BigDecimal lastFilledQty,
      BigDecimal cumulativeFilledQty,
      BigDecimal price,
      Instant eventTime) {}

  public record OpenPositionCommand(
      String symbol,
      OrderSide side,
      BigDecimal entryPrice,
      BigDecimal quantity,
      BigDecimal stopLoss,
      BigDecimal takeProfit,
      String trailingConfigurationJson,
      String correlationId) {}

  public record ExternalOrderSnapshot(
      String symbol,
      String clientOrderId,
      String exchangeOrderId,
      ManagedOrderStatus status,
      BigDecimal executedQty,
      Instant eventTime) {}

  public record ReconciliationReport(int adopted, int cancelled) {}
}
