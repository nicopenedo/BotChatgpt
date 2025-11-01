package com.bottrading.service.exchange;

import com.bottrading.config.ReconcileProperties;
import com.bottrading.execution.PositionManager;
import com.bottrading.execution.PositionManager.ExternalOrderSnapshot;
import com.bottrading.execution.PositionManager.ReconciliationReport;
import com.bottrading.model.entity.ManagedOrderEntity;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.enums.ManagedOrderStatus;
import com.bottrading.model.enums.PositionStatus;
import com.bottrading.notify.TelegramNotifier;
import com.bottrading.repository.ManagedOrderRepository;
import com.bottrading.repository.PositionRepository;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.binance.BinanceClient.ExchangeOrder;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExchangeReconciler {

  private static final Logger log = LoggerFactory.getLogger(ExchangeReconciler.class);

  private final BinanceClient binanceClient;
  private final PositionManager positionManager;
  private final PositionRepository positionRepository;
  private final ManagedOrderRepository managedOrderRepository;
  private final ReconcileProperties reconcileProperties;
  private final TelegramNotifier notifier;
  private final Clock clock;

  public ExchangeReconciler(
      BinanceClient binanceClient,
      PositionManager positionManager,
      PositionRepository positionRepository,
      ManagedOrderRepository managedOrderRepository,
      ReconcileProperties reconcileProperties,
      TelegramNotifier notifier,
      Optional<Clock> clock) {
    this.binanceClient = binanceClient;
    this.positionManager = positionManager;
    this.positionRepository = positionRepository;
    this.managedOrderRepository = managedOrderRepository;
    this.reconcileProperties = reconcileProperties;
    this.notifier = notifier;
    this.clock = clock.orElse(Clock.systemUTC());
  }

  @Transactional
  public ReconciliationReport reconcileSymbol(String symbol) {
    List<ExchangeOrder> remoteOrders = binanceClient.getOpenOrders(symbol);
    List<ExternalOrderSnapshot> snapshots =
        remoteOrders.stream().map(this::toSnapshot).collect(Collectors.toList());
    ReconciliationReport report = positionManager.reconcile(snapshots);
    cleanupLocalOrders(symbol, snapshots);
    return report;
  }

  @Transactional
  public void reconcileAll() {
    List<PositionEntity> openPositions = positionRepository.findByStatus(PositionStatus.OPEN);
    Map<String, List<PositionEntity>> bySymbol =
        openPositions.stream().collect(Collectors.groupingBy(PositionEntity::getSymbol));
    bySymbol.keySet().forEach(this::reconcileSymbol);
  }

  private void cleanupLocalOrders(String symbol, Collection<ExternalOrderSnapshot> remoteSnapshots) {
    Set<String> remoteClientIds = remoteSnapshots.stream().map(ExternalOrderSnapshot::clientOrderId).collect(Collectors.toSet());
    List<PositionEntity> positions = positionRepository.findByStatus(PositionStatus.OPEN);
    for (PositionEntity position : positions) {
      if (!position.getSymbol().equalsIgnoreCase(symbol)) {
        continue;
      }
      List<ManagedOrderEntity> orders = managedOrderRepository.findByPosition(position);
      for (ManagedOrderEntity order : orders) {
        if (order.getStatus() == ManagedOrderStatus.FILLED || order.getStatus() == ManagedOrderStatus.CANCELED) {
          continue;
        }
        if (!remoteClientIds.contains(order.getClientOrderId())) {
          log.warn("Order {} missing on exchange, marking as canceled", order.getClientOrderId());
          order.setStatus(ManagedOrderStatus.CANCELED);
          order.setUpdatedAt(Instant.now(clock));
          managedOrderRepository.save(order);
          notifier.notifyReconciledItem(symbol, "Local order canceled " + order.getClientOrderId());
        }
      }
    }
  }

  private ExternalOrderSnapshot toSnapshot(ExchangeOrder order) {
    return new ExternalOrderSnapshot(
        order.symbol(),
        order.clientOrderId(),
        order.exchangeOrderId(),
        mapStatus(order.status()),
        order.executedQty(),
        Instant.ofEpochMilli(order.updateTime()));
  }

  private ManagedOrderStatus mapStatus(String status) {
    return switch (status) {
      case "NEW" -> ManagedOrderStatus.NEW;
      case "PARTIALLY_FILLED" -> ManagedOrderStatus.PARTIAL;
      case "FILLED" -> ManagedOrderStatus.FILLED;
      case "CANCELED" -> ManagedOrderStatus.CANCELED;
      default -> ManagedOrderStatus.ERROR;
    };
  }

  public boolean reconcileOnStartup() {
    return reconcileProperties.isOnStartup();
  }

  public int scanWindowMinutes() {
    return reconcileProperties.getScanMinutes();
  }
}
