package com.bottrading.execution;

import com.bottrading.config.StopProperties;
import com.bottrading.config.StopProperties.StopSymbolProperties;
import com.bottrading.model.entity.ManagedOrderEntity;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.enums.ManagedOrderType;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import com.bottrading.model.enums.PositionStatus;
import com.bottrading.notify.TelegramNotifier;
import com.bottrading.repository.ManagedOrderRepository;
import com.bottrading.repository.PositionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class StopEngine {

  private final StopProperties stopProperties;
  private final PositionRepository positionRepository;
  private final ManagedOrderRepository managedOrderRepository;
  private final TelegramNotifier telegramNotifier;
  private final Clock clock;
  private final ConcurrentMap<Long, ActivePosition> activePositions = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, StopSymbolProperties> overrides = new ConcurrentHashMap<>();
  private final Counter slHits;
  private final Counter tpHits;
  private final Counter trailingAdjustments;
  private final Counter breakevenMoves;

  public StopEngine(
      StopProperties stopProperties,
      PositionRepository positionRepository,
      ManagedOrderRepository managedOrderRepository,
      TelegramNotifier telegramNotifier,
      MeterRegistry meterRegistry,
      Optional<Clock> clock) {
    this.stopProperties = stopProperties;
    this.positionRepository = positionRepository;
    this.managedOrderRepository = managedOrderRepository;
    this.telegramNotifier = telegramNotifier;
    this.clock = clock.orElse(Clock.systemUTC());
    this.slHits = meterRegistry.counter("stop.sl.hits");
    this.tpHits = meterRegistry.counter("stop.tp.hits");
    this.trailingAdjustments = meterRegistry.counter("stop.trailing.adjustments");
    this.breakevenMoves = meterRegistry.counter("stop.breakeven.moves");
  }

  public StopPlan plan(String symbol, OrderSide side, BigDecimal entryPrice, BigDecimal atr) {
    Objects.requireNonNull(symbol, "symbol");
    Objects.requireNonNull(side, "side");
    Objects.requireNonNull(entryPrice, "entryPrice");
    StopSymbolProperties conf = overrides.getOrDefault(symbol, stopProperties.getForSymbol(symbol));
    StopProperties.Mode mode = conf.getModeOrDefault(stopProperties);
    BigDecimal stopLoss;
    BigDecimal takeProfit;
    if (mode == StopProperties.Mode.ATR && atr != null) {
      BigDecimal slOffset = atr.multiply(conf.getSlAtrMultOrDefault(stopProperties));
      BigDecimal tpOffset = atr.multiply(conf.getTpAtrMultOrDefault(stopProperties));
      if (side == OrderSide.BUY) {
        stopLoss = entryPrice.subtract(slOffset);
        takeProfit = entryPrice.add(tpOffset);
      } else {
        stopLoss = entryPrice.add(slOffset);
        takeProfit = entryPrice.subtract(tpOffset);
      }
    } else {
      BigDecimal slPct = conf.getSlPctOrDefault(stopProperties).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
      BigDecimal tpPct = conf.getTpPctOrDefault(stopProperties).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
      if (side == OrderSide.BUY) {
        stopLoss = entryPrice.multiply(BigDecimal.ONE.subtract(slPct));
        takeProfit = entryPrice.multiply(BigDecimal.ONE.add(tpPct));
      } else {
        stopLoss = entryPrice.multiply(BigDecimal.ONE.add(slPct));
        takeProfit = entryPrice.multiply(BigDecimal.ONE.subtract(tpPct));
      }
    }
    BigDecimal trailingOffset = computeTrailingOffset(side, entryPrice, atr, conf);
    BigDecimal breakevenTrigger =
        conf.isBreakevenEnabledOrDefault(stopProperties)
            ? entryPrice
                .multiply(
                    BigDecimal.ONE.add(
                        conf.getBreakevenTriggerPctOrDefault(stopProperties)
                            .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                            .multiply(side == OrderSide.BUY ? BigDecimal.ONE : BigDecimal.valueOf(-1))))
                .setScale(8, RoundingMode.HALF_UP)
            : null;
    return new StopPlan(stopLoss, takeProfit, trailingOffset, breakevenTrigger, conf);
  }

  private BigDecimal computeTrailingOffset(
      OrderSide side, BigDecimal entryPrice, BigDecimal atr, StopSymbolProperties conf) {
    if (!conf.isTrailingEnabledOrDefault(stopProperties)) {
      return null;
    }
    if (conf.getModeOrDefault(stopProperties) == StopProperties.Mode.ATR && atr != null) {
      return atr.multiply(conf.getTrailingAtrMultOrDefault(stopProperties));
    }
    BigDecimal pct =
        conf.getTrailingPctOrDefault(stopProperties).divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
    BigDecimal offset = entryPrice.multiply(pct);
    return offset.max(BigDecimal.ZERO);
  }

  public PositionEntity registerPosition(
      String symbol,
      OrderSide side,
      BigDecimal entryPrice,
      BigDecimal quantity,
      BigDecimal atr,
      String ocoGroupId) {
    StopPlan plan = plan(symbol, side, entryPrice, atr);
    PositionEntity entity = new PositionEntity();
    entity.setSymbol(symbol);
    entity.setSide(side);
    entity.setEntryPrice(entryPrice);
    entity.setQuantity(quantity);
    entity.setStopLoss(plan.stopLoss());
    entity.setTakeProfit(plan.takeProfit());
    entity.setTrailingOffset(plan.trailingOffset());
    entity.setTrailingStop(plan.stopLoss());
    entity.setBreakevenPrice(plan.breakevenTrigger());
    entity.setEntryAtr(atr);
    entity.setOcoGroupId(StringUtils.hasText(ocoGroupId) ? ocoGroupId : generateOcoId(symbol));
    entity.setOpenedAt(Instant.now(clock));
    PositionEntity saved = positionRepository.save(entity);
    createManagedOrders(saved, plan, side);
    activePositions.put(saved.getId(), new ActivePosition(saved, plan));
    return saved;
  }

  private void createManagedOrders(PositionEntity position, StopPlan plan, OrderSide side) {
    ManagedOrderEntity sl = new ManagedOrderEntity();
    sl.setPosition(position);
    sl.setType(ManagedOrderType.STOP_LOSS);
    sl.setSide(side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY);
    sl.setPrice(plan.stopLoss());
    sl.setQuantity(position.getQuantity());
    sl.setStatus("OPEN");
    sl.setCreatedAt(Instant.now(clock));
    managedOrderRepository.save(sl);

    ManagedOrderEntity tp = new ManagedOrderEntity();
    tp.setPosition(position);
    tp.setType(ManagedOrderType.TAKE_PROFIT);
    tp.setSide(side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY);
    tp.setPrice(plan.takeProfit());
    tp.setQuantity(position.getQuantity());
    tp.setStatus("OPEN");
    tp.setCreatedAt(Instant.now(clock));
    managedOrderRepository.save(tp);
  }

  public void onPriceUpdate(String symbol, BigDecimal price, BigDecimal atr) {
    Collection<ActivePosition> states = activePositions.values();
    for (ActivePosition state : states) {
      if (!state.position().getSymbol().equalsIgnoreCase(symbol)) {
        continue;
      }
      handlePrice(symbol, price, atr, state);
    }
  }

  private void handlePrice(String symbol, BigDecimal price, BigDecimal atr, ActivePosition state) {
    PositionEntity position = state.position();
    if (position.getStatus() != PositionStatus.OPEN) {
      return;
    }
    if (position.getSide() == OrderSide.BUY) {
      if (price.compareTo(position.getStopLoss()) <= 0) {
        completePosition(position, price, PositionStatus.STOP_HIT);
        slHits.increment();
        telegramNotifier.notifyStopHit(symbol, OrderSide.SELL, price, price.subtract(position.getEntryPrice()));
        return;
      }
      if (price.compareTo(position.getTakeProfit()) >= 0) {
        completePosition(position, price, PositionStatus.TAKE_PROFIT);
        tpHits.increment();
        telegramNotifier.notifyStopHit(symbol, OrderSide.SELL, price, price.subtract(position.getEntryPrice()));
        return;
      }
    } else {
      if (price.compareTo(position.getStopLoss()) >= 0) {
        completePosition(position, price, PositionStatus.STOP_HIT);
        slHits.increment();
        telegramNotifier.notifyStopHit(symbol, OrderSide.BUY, price, position.getEntryPrice().subtract(price));
        return;
      }
      if (price.compareTo(position.getTakeProfit()) <= 0) {
        completePosition(position, price, PositionStatus.TAKE_PROFIT);
        tpHits.increment();
        telegramNotifier.notifyStopHit(symbol, OrderSide.BUY, price, position.getEntryPrice().subtract(price));
        return;
      }
    }

    adjustTrailing(position, price, atr, state.plan().properties());
    maybeMoveBreakeven(position, price);
  }

  private void completePosition(PositionEntity position, BigDecimal price, PositionStatus status) {
    position.setStatus(status);
    position.setClosedAt(Instant.now(clock));
    positionRepository.save(position);
    cancelOppositeOrders(position, status);
    activePositions.remove(position.getId());
  }

  private void cancelOppositeOrders(PositionEntity position, PositionStatus status) {
    for (ManagedOrderEntity order : managedOrderRepository.findByPosition(position)) {
      if (order.getType() == ManagedOrderType.STOP_LOSS || order.getType() == ManagedOrderType.TAKE_PROFIT) {
        if (status == PositionStatus.TAKE_PROFIT && order.getType() == ManagedOrderType.STOP_LOSS) {
          order.setStatus("CANCELLED");
        } else if (status == PositionStatus.STOP_HIT && order.getType() == ManagedOrderType.TAKE_PROFIT) {
          order.setStatus("CANCELLED");
        } else {
          order.setStatus("FILLED");
        }
        order.setUpdatedAt(Instant.now(clock));
        managedOrderRepository.save(order);
      }
    }
  }

  private void adjustTrailing(PositionEntity position, BigDecimal price, BigDecimal atr, StopSymbolProperties conf) {
    if (!conf.isTrailingEnabledOrDefault(stopProperties)) {
      return;
    }
    BigDecimal trailingOffset = position.getTrailingOffset();
    if (trailingOffset == null) {
      return;
    }
    BigDecimal candidate;
    if (conf.getModeOrDefault(stopProperties) == StopProperties.Mode.ATR && atr != null) {
      BigDecimal offset = atr.multiply(conf.getTrailingAtrMultOrDefault(stopProperties));
      candidate =
          position.getSide() == OrderSide.BUY ? price.subtract(offset) : price.add(offset);
    } else {
      BigDecimal offset =
          position
              .getEntryPrice()
              .multiply(
                  conf.getTrailingPctOrDefault(stopProperties)
                      .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
      candidate = position.getSide() == OrderSide.BUY ? price.subtract(offset) : price.add(offset);
    }
    if (position.getSide() == OrderSide.BUY) {
      if (candidate.compareTo(position.getStopLoss()) > 0 && candidate.compareTo(price) < 0) {
        updateStop(position, candidate);
      }
    } else {
      if (candidate.compareTo(position.getStopLoss()) < 0 && candidate.compareTo(price) > 0) {
        updateStop(position, candidate);
      }
    }
  }

  private void updateStop(PositionEntity position, BigDecimal newStop) {
    position.setStopLoss(newStop);
    position.setTrailingStop(newStop);
    position.setLastAdjustmentAt(Instant.now(clock));
    positionRepository.save(position);
    managedOrderRepository
        .findByPositionAndType(position, ManagedOrderType.STOP_LOSS)
        .ifPresent(
            order -> {
              order.setPrice(newStop);
              order.setStatus("UPDATED");
              order.setUpdatedAt(Instant.now(clock));
              managedOrderRepository.save(order);
            });
    trailingAdjustments.increment();
    telegramNotifier.notifyTrailingAdjustment(position.getSymbol(), newStop);
  }

  private void maybeMoveBreakeven(PositionEntity position, BigDecimal price) {
    BigDecimal breakeven = position.getBreakevenPrice();
    if (breakeven == null || position.getSide() == null) {
      return;
    }
    if (position.getSide() == OrderSide.BUY) {
      if (price.compareTo(breakeven) >= 0 && position.getStopLoss().compareTo(position.getEntryPrice()) < 0) {
        updateStop(position, position.getEntryPrice());
        breakevenMoves.increment();
        telegramNotifier.notifyBreakeven(position.getSymbol(), position.getEntryPrice());
      }
    } else {
      if (price.compareTo(breakeven) <= 0 && position.getStopLoss().compareTo(position.getEntryPrice()) > 0) {
        updateStop(position, position.getEntryPrice());
        breakevenMoves.increment();
        telegramNotifier.notifyBreakeven(position.getSymbol(), position.getEntryPrice());
      }
    }
  }

  public void updateConfiguration(String symbol, StopSymbolProperties config) {
    overrides.put(symbol, config);
    stopProperties.getSymbols().put(symbol, config);
  }

  public StopStatus status(String symbol) {
    StopSymbolProperties conf = overrides.getOrDefault(symbol, stopProperties.getForSymbol(symbol));
    return new StopStatus(conf, activePositions.values().stream().filter(a -> a.position().getSymbol().equalsIgnoreCase(symbol)).map(ActivePosition::position).toList());
  }

  private String generateOcoId(String symbol) {
    return symbol + "-oco-" + Instant.now(clock).toEpochMilli();
  }

  private record ActivePosition(PositionEntity position, StopPlan plan) {}

  public record StopPlan(
      BigDecimal stopLoss,
      BigDecimal takeProfit,
      BigDecimal trailingOffset,
      BigDecimal breakevenTrigger,
      StopSymbolProperties properties) {}

  public record StopStatus(StopSymbolProperties config, java.util.List<PositionEntity> positions) {}
}
