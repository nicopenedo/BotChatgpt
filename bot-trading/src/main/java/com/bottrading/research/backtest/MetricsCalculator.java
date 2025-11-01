package com.bottrading.research.backtest;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MetricsCalculator {

  private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

  private MetricsCalculator() {}

  public static MetricsSummary compute(List<TradeRecord> trades, List<EquityPoint> equityCurve) {
    if (equityCurve.isEmpty()) {
      return empty();
    }
    BigDecimal start = equityCurve.get(0).equity();
    BigDecimal end = equityCurve.get(equityCurve.size() - 1).equity();
    Instant startTime = equityCurve.get(0).time();
    Instant endTime = equityCurve.get(equityCurve.size() - 1).time();
    long days = Math.max(1, Duration.between(startTime, endTime).toDays());
    BigDecimal cagr = BigDecimal.ZERO;
    if (start.compareTo(BigDecimal.ZERO) > 0) {
      double ratio = end.divide(start, MC).doubleValue();
      cagr =
          BigDecimal.valueOf(Math.pow(ratio, 365.0 / days) - 1)
              .setScale(6, RoundingMode.HALF_UP);
    }

    BigDecimal maxDrawdown = maxDrawdown(equityCurve);
    BigDecimal calmar = maxDrawdown.compareTo(BigDecimal.ZERO) == 0
        ? BigDecimal.ZERO
        : cagr.divide(maxDrawdown, MC).abs();

    List<BigDecimal> returns = periodicReturns(equityCurve);
    BigDecimal sharpe = sharpe(returns);
    BigDecimal sortino = sortino(returns);

    BigDecimal totalProfit = BigDecimal.ZERO;
    BigDecimal totalLoss = BigDecimal.ZERO;
    BigDecimal wins = BigDecimal.ZERO;
    for (TradeRecord trade : trades) {
      if (trade.pnl().compareTo(BigDecimal.ZERO) >= 0) {
        totalProfit = totalProfit.add(trade.pnl(), MC);
        wins = wins.add(BigDecimal.ONE);
      } else {
        totalLoss = totalLoss.add(trade.pnl().abs(), MC);
      }
    }
    BigDecimal profitFactor =
        totalLoss.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : totalProfit.divide(totalLoss, MC);
    BigDecimal winRate = trades.isEmpty() ? BigDecimal.ZERO : wins.divide(BigDecimal.valueOf(trades.size()), MC);
    BigDecimal expectancy = trades.isEmpty() ? BigDecimal.ZERO : end.subtract(start, MC).divide(BigDecimal.valueOf(trades.size()), MC);
    BigDecimal exposure = exposure(trades, startTime, endTime);

    return new MetricsSummary(
        cagr,
        sharpe,
        sortino,
        calmar,
        maxDrawdown,
        profitFactor,
        winRate,
        expectancy,
        trades.size(),
        exposure);
  }

  private static MetricsSummary empty() {
    BigDecimal zero = BigDecimal.ZERO;
    return new MetricsSummary(zero, zero, zero, zero, zero, zero, zero, zero, 0, zero);
  }

  private static BigDecimal maxDrawdown(List<EquityPoint> curve) {
    BigDecimal peak = curve.get(0).equity();
    BigDecimal maxDd = BigDecimal.ZERO;
    for (EquityPoint point : curve) {
      if (point.equity().compareTo(peak) > 0) {
        peak = point.equity();
      }
      BigDecimal drawdown =
          peak.compareTo(BigDecimal.ZERO) == 0
              ? BigDecimal.ZERO
              : peak.subtract(point.equity(), MC).divide(peak, MC);
      if (drawdown.compareTo(maxDd) > 0) {
        maxDd = drawdown;
      }
    }
    return maxDd;
  }

  private static List<BigDecimal> periodicReturns(List<EquityPoint> curve) {
    List<BigDecimal> returns = new ArrayList<>();
    for (int i = 1; i < curve.size(); i++) {
      BigDecimal prev = curve.get(i - 1).equity();
      BigDecimal current = curve.get(i).equity();
      if (prev.compareTo(BigDecimal.ZERO) == 0) {
        returns.add(BigDecimal.ZERO);
      } else {
        returns.add(current.subtract(prev, MC).divide(prev, MC));
      }
    }
    return returns;
  }

  private static BigDecimal sharpe(List<BigDecimal> returns) {
    if (returns.isEmpty()) {
      return BigDecimal.ZERO;
    }
    BigDecimal mean = mean(returns);
    BigDecimal variance = BigDecimal.ZERO;
    for (BigDecimal r : returns) {
      BigDecimal diff = r.subtract(mean, MC);
      variance = variance.add(diff.multiply(diff, MC), MC);
    }
    variance = variance.divide(BigDecimal.valueOf(returns.size()), MC);
    BigDecimal std = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
    if (std.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return mean.divide(std, MC);
  }

  private static BigDecimal sortino(List<BigDecimal> returns) {
    if (returns.isEmpty()) {
      return BigDecimal.ZERO;
    }
    BigDecimal mean = mean(returns);
    BigDecimal downside = BigDecimal.ZERO;
    int count = 0;
    for (BigDecimal r : returns) {
      if (r.compareTo(BigDecimal.ZERO) < 0) {
        BigDecimal diff = r.subtract(BigDecimal.ZERO, MC);
        downside = downside.add(diff.multiply(diff, MC), MC);
        count++;
      }
    }
    if (count == 0) {
      return BigDecimal.ZERO;
    }
    downside = downside.divide(BigDecimal.valueOf(count), MC);
    BigDecimal std = BigDecimal.valueOf(Math.sqrt(downside.doubleValue()));
    if (std.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }
    return mean.divide(std, MC);
  }

  private static BigDecimal mean(List<BigDecimal> values) {
    BigDecimal sum = BigDecimal.ZERO;
    for (BigDecimal v : values) {
      sum = sum.add(v, MC);
    }
    return values.isEmpty() ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(values.size()), MC);
  }

  private static BigDecimal exposure(List<TradeRecord> trades, Instant start, Instant end) {
    if (trades.isEmpty()) {
      return BigDecimal.ZERO;
    }
    long active = 0;
    for (TradeRecord trade : trades) {
      active += Duration.between(trade.entryTime(), trade.exitTime()).toMillis();
    }
    long total = Duration.between(start, end).toMillis();
    if (total <= 0) {
      return BigDecimal.ZERO;
    }
    return BigDecimal.valueOf((double) active / total);
  }
}
