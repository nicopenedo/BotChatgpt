package com.bottrading.research.backtest.realistic;

import com.bottrading.model.dto.Kline;
import com.bottrading.research.backtest.ExecutionResult;
import com.bottrading.research.backtest.ExecutionResult.ExecutionType;
import com.bottrading.research.backtest.FillDetail;
import com.bottrading.strategy.SignalSide;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Simulates executions using the simplified depth model with TTL, queues and fallback logic. */
public class RealisticExecutionSimulator {

  private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

  private final String symbol;
  private final RealisticBacktestConfig config;
  private final BigDecimal makerFeeBps;
  private final BigDecimal takerFeeBps;
  private final Random random;
  private final LobEstimator lobEstimator;
  private final SyntheticTcaEstimator tcaEstimator;

  public RealisticExecutionSimulator(
      String symbol,
      RealisticBacktestConfig config,
      BigDecimal makerFeeBps,
      BigDecimal takerFeeBps,
      long seed) {
    this.symbol = symbol;
    this.config = config == null ? new RealisticBacktestConfig() : config;
    this.makerFeeBps = makerFeeBps == null ? BigDecimal.ZERO : makerFeeBps;
    this.takerFeeBps = takerFeeBps == null ? BigDecimal.ZERO : takerFeeBps;
    this.random = new Random(seed);
    this.lobEstimator = new LobEstimator(this.config.lob());
    this.tcaEstimator = new SyntheticTcaEstimator(this.config.tca());
  }

  public ExecutionResult executeEntry(
      SignalSide side, List<Kline> klines, int index, BigDecimal quantity) {
    if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
      return ExecutionResult.empty(quantity, ExecutionType.LIMIT);
    }
    ExecutionResult limitResult = executeLimit(side, klines, index, quantity);
    BigDecimal filled = limitResult.quantity();
    if (filled.compareTo(quantity) >= 0) {
      return limitResult;
    }
    BigDecimal remaining = quantity.subtract(filled, MC);
    if (!config.execution().limit().fallbackToMarket()) {
      return new ExecutionResult(
          limitResult.fills(), quantity, true, ExecutionType.LIMIT);
    }
    ExecutionResult marketResult = executeMarket(side, klines, index, remaining);
    List<FillDetail> merged = new ArrayList<>(limitResult.fills());
    merged.addAll(marketResult.fills());
    boolean ttlExpired = true;
    return new ExecutionResult(merged, quantity, ttlExpired, ExecutionType.LIMIT);
  }

  public ExecutionResult executeExit(
      SignalSide side, List<Kline> klines, int index, BigDecimal quantity) {
    if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
      return ExecutionResult.empty(quantity, ExecutionType.MARKET);
    }
    BigDecimal adv = lobEstimator.snapshot(symbol, klines.get(Math.min(index, klines.size() - 1))).adv();
    BigDecimal participation = ratio(quantity, adv);
    RealisticBacktestConfig.ExecutionConfig exec = config.execution();
    if (participation.compareTo(exec.pov().targetParticipation()) > 0) {
      return executePov(side, klines, index, quantity);
    }
    if (exec.twap().slices() > 1) {
      return executeTwap(side, klines, index, quantity, exec.twap().slices());
    }
    return executeMarket(side, klines, index, quantity);
  }

  private ExecutionResult executeLimit(
      SignalSide side, List<Kline> klines, int index, BigDecimal quantity) {
    RealisticBacktestConfig.LimitConfig limit = config.execution().limit();
    BigDecimal remaining = quantity;
    List<FillDetail> fills = new ArrayList<>();
    Kline current = klines.get(index);
    Instant orderTime = current.closeTime() != null ? current.closeTime() : current.openTime();
    long remainingTtl = limit.ttlMs();
    int cursor = index;
    while (remaining.compareTo(BigDecimal.ZERO) > 0 && remainingTtl > 0 && cursor + 1 < klines.size()) {
      Kline next = klines.get(++cursor);
      LobEstimator.TopOfBookSnapshot book = lobEstimator.snapshot(symbol, next);
      long delta =
          Math.max(
              1L,
              next.openTime().toEpochMilli()
                  - (orderTime == null ? next.openTime().toEpochMilli() : orderTime.toEpochMilli()));
      remainingTtl -= delta;
      BigDecimal distance =
          limit.bufferBps().add(book.spreadBps().divide(BigDecimal.valueOf(2), MC), MC);
      double probability = fillProbability(distance, remaining, book.depth());
      if (probability <= 0) {
        continue;
      }
      BigDecimal fillQty = remaining.multiply(BigDecimal.valueOf(Math.min(1.0, probability)), MC);
      if (fillQty.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
      }
      BigDecimal price = limitPrice(side, next.close(), limit.bufferBps());
      BigDecimal slippage =
          tcaEstimator.estimate(book.spreadBps(), fillQty, book.adv(), next.volume(), next.closeTime());
      long queue = queueTime(limit.latencyMs(), probability);
      BigDecimal fee = makerFee(price, fillQty);
      fills.add(new FillDetail(next.closeTime(), price, fillQty, queue, slippage, fee, true));
      remaining = remaining.subtract(fillQty, MC);
    }
    boolean ttlExpired = remaining.compareTo(BigDecimal.ZERO) > 0;
    return new ExecutionResult(fills, quantity, ttlExpired, ExecutionType.LIMIT);
  }

  private ExecutionResult executeMarket(
      SignalSide side, List<Kline> klines, int index, BigDecimal quantity) {
    Kline kline = klines.get(Math.min(index + 1, klines.size() - 1));
    LobEstimator.TopOfBookSnapshot book = lobEstimator.snapshot(symbol, kline);
    BigDecimal slippage =
        config.execution()
            .market()
            .baseSlippageBps()
            .add(
                tcaEstimator.estimate(
                    book.spreadBps(), quantity, book.adv(), kline.volume(), kline.closeTime()),
                MC);
    BigDecimal price = marketPrice(side, kline.close(), slippage);
    BigDecimal fee = takerFee(price, quantity);
    FillDetail fill =
        new FillDetail(
            kline.closeTime(), price, quantity, 0L, slippage, fee, false);
    return new ExecutionResult(List.of(fill), quantity, false, ExecutionType.MARKET);
  }

  private ExecutionResult executeTwap(
      SignalSide side, List<Kline> klines, int index, BigDecimal quantity, int slices) {
    BigDecimal remaining = quantity;
    List<FillDetail> fills = new ArrayList<>();
    int cursor = index;
    for (int i = 0; i < slices && remaining.compareTo(BigDecimal.ZERO) > 0; i++) {
      BigDecimal sliceQty = remaining.divide(BigDecimal.valueOf(slices - i), MC);
      ExecutionResult part = executeMarket(side, klines, cursor, sliceQty);
      fills.addAll(part.fills());
      remaining = remaining.subtract(part.quantity(), MC);
      cursor = Math.min(cursor + 1, klines.size() - 1);
    }
    return new ExecutionResult(fills, quantity, false, ExecutionType.TWAP);
  }

  private ExecutionResult executePov(
      SignalSide side, List<Kline> klines, int index, BigDecimal quantity) {
    BigDecimal remaining = quantity;
    List<FillDetail> fills = new ArrayList<>();
    int cursor = index;
    BigDecimal targetParticipation = config.execution().pov().targetParticipation();
    while (remaining.compareTo(BigDecimal.ZERO) > 0 && cursor + 1 < klines.size()) {
      cursor++;
      Kline kline = klines.get(cursor);
      BigDecimal targetQty = kline.volume().multiply(targetParticipation, MC);
      if (targetQty.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
      }
      BigDecimal slice = remaining.min(targetQty);
      ExecutionResult part = executeMarket(side, klines, cursor - 1, slice);
      fills.addAll(part.fills());
      remaining = remaining.subtract(part.quantity(), MC);
    }
    if (remaining.compareTo(BigDecimal.ZERO) > 0) {
      ExecutionResult sweep = executeMarket(side, klines, cursor, remaining);
      fills.addAll(sweep.fills());
    }
    return new ExecutionResult(fills, quantity, false, ExecutionType.POV);
  }

  private double fillProbability(BigDecimal distanceBps, BigDecimal qty, BigDecimal depth) {
    double distance = distanceBps.doubleValue();
    double depthFactor = depth.doubleValue();
    if (depthFactor <= 0) {
      return 0.0;
    }
    double sizeRatio = qty.doubleValue() / depthFactor;
    double decay = Math.exp(-distance / (5.0 + depthFactor / 100.0));
    return Math.min(1.0, decay / (1.0 + sizeRatio));
  }

  private long queueTime(long latencyMs, double probability) {
    long jitter = (long) (random.nextDouble() * 200 * (1.0 - probability));
    return latencyMs + Math.max(5, jitter);
  }

  private BigDecimal limitPrice(SignalSide side, BigDecimal reference, BigDecimal bufferBps) {
    BigDecimal multiplier = bufferBps.divide(BigDecimal.valueOf(10000), MC);
    if (side == SignalSide.BUY) {
      return reference.multiply(BigDecimal.ONE.subtract(multiplier, MC), MC);
    }
    return reference.multiply(BigDecimal.ONE.add(multiplier, MC), MC);
  }

  private BigDecimal marketPrice(SignalSide side, BigDecimal reference, BigDecimal slippageBps) {
    BigDecimal multiplier = slippageBps.divide(BigDecimal.valueOf(10000), MC);
    if (side == SignalSide.BUY) {
      return reference.multiply(BigDecimal.ONE.add(multiplier, MC), MC);
    }
    return reference.multiply(BigDecimal.ONE.subtract(multiplier, MC), MC);
  }

  private BigDecimal makerFee(BigDecimal price, BigDecimal qty) {
    return price.multiply(qty, MC).multiply(makerFeeBps, MC).divide(BigDecimal.valueOf(10000), MC);
  }

  private BigDecimal takerFee(BigDecimal price, BigDecimal qty) {
    return price.multiply(qty, MC).multiply(takerFeeBps, MC).divide(BigDecimal.valueOf(10000), MC);
  }

  private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
    if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return numerator.divide(denominator, MC);
  }
}
