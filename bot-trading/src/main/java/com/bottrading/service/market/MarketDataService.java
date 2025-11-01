package com.bottrading.service.market;

import com.bottrading.config.CacheConfig;
import com.bottrading.model.dto.Kline;
import com.bottrading.model.dto.report.AtrBandPoint;
import com.bottrading.model.dto.report.IndicatorPoint;
import com.bottrading.model.dto.report.SupertrendPoint;
import com.bottrading.service.binance.BinanceClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MarketDataService {

  private final BinanceClient binanceClient;

  public MarketDataService(BinanceClient binanceClient) {
    this.binanceClient = binanceClient;
  }

  public List<Kline> getKlines(
      String symbol, String interval, Instant from, Instant to, Integer limit) {
    int effectiveLimit =
        Optional.ofNullable(limit).orElseGet(() -> estimateLimit(interval, from, to));
    List<Kline> klines = loadKlines(symbol, interval, effectiveLimit);
    if (from == null && to == null) {
      return klines;
    }
    return klines.stream()
        .filter(kline ->
            (from == null || !kline.closeTime().isBefore(from))
                && (to == null || !kline.closeTime().isAfter(to)))
        .toList();
  }

  @Cacheable(cacheNames = CacheConfig.KLINES_CACHE, key = "#symbol + '|' + #interval + '|' + #limit")
  public List<Kline> loadKlines(String symbol, String interval, int limit) {
    return binanceClient.getKlines(symbol, interval, limit);
  }

  @Cacheable(
      cacheNames = CacheConfig.INDICATOR_CACHE,
      key = "'vwap|' + #symbol + '|' + #interval + '|' + #from + '|' + #to + '|' + #anchorTs")
  public List<IndicatorPoint> vwap(
      String symbol, String interval, Instant from, Instant to, Instant anchorTs) {
    List<Kline> klines = getKlines(symbol, interval, from, to, null);
    if (klines.isEmpty()) {
      return Collections.emptyList();
    }
    List<IndicatorPoint> result = new ArrayList<>();
    BigDecimal cumulativePV = BigDecimal.ZERO;
    BigDecimal cumulativeVolume = BigDecimal.ZERO;
    LocalDate currentDay = null;
    for (Kline kline : klines) {
      if (anchorTs != null && kline.closeTime().isBefore(anchorTs)) {
        continue;
      }
      if (anchorTs == null) {
        LocalDate day = kline.closeTime().atZone(ZoneOffset.UTC).toLocalDate();
        if (!Objects.equals(currentDay, day)) {
          cumulativePV = BigDecimal.ZERO;
          cumulativeVolume = BigDecimal.ZERO;
          currentDay = day;
        }
      } else if (cumulativeVolume.compareTo(BigDecimal.ZERO) == 0) {
        cumulativePV = BigDecimal.ZERO;
        cumulativeVolume = BigDecimal.ZERO;
      }
      BigDecimal typical =
          kline.high().add(kline.low()).add(kline.close()).divide(BigDecimal.valueOf(3), 8, RoundingMode.HALF_UP);
      cumulativePV = cumulativePV.add(typical.multiply(kline.volume()));
      cumulativeVolume = cumulativeVolume.add(kline.volume());
      if (cumulativeVolume.compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal value = cumulativePV.divide(cumulativeVolume, 8, RoundingMode.HALF_UP);
        result.add(new IndicatorPoint(kline.closeTime(), value));
      }
    }
    return result;
  }

  @Cacheable(
      cacheNames = CacheConfig.INDICATOR_CACHE,
      key = "'atr|' + #symbol + '|' + #interval + '|' + #from + '|' + #to + '|' + #period + '|' + #multiplier")
  public List<AtrBandPoint> atrBands(
      String symbol,
      String interval,
      Instant from,
      Instant to,
      int period,
      BigDecimal multiplier) {
    List<Kline> klines = getKlines(symbol, interval, from, to, null);
    if (klines.isEmpty()) {
      return Collections.emptyList();
    }
    List<AtrBandPoint> bands = new ArrayList<>();
    BigDecimal prevClose = null;
    BigDecimal atr = null;
    int count = 0;
    for (Kline kline : klines) {
      BigDecimal tr = trueRange(kline, prevClose);
      prevClose = kline.close();
      count++;
      if (atr == null) {
        atr = tr;
      } else {
        atr = atr.multiply(BigDecimal.valueOf(period - 1))
            .add(tr)
            .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
      }
      if (count < period) {
        continue;
      }
      BigDecimal mid = kline.close();
      BigDecimal delta = atr.multiply(multiplier);
      bands.add(new AtrBandPoint(kline.closeTime(), mid, mid.add(delta), mid.subtract(delta)));
    }
    return bands;
  }

  public List<SupertrendPoint> supertrend(
      String symbol,
      String interval,
      Instant from,
      Instant to,
      int atrPeriod,
      BigDecimal multiplier) {
    List<AtrBandPoint> atrSeries = atrBands(symbol, interval, from, to, atrPeriod, multiplier);
    if (atrSeries.isEmpty()) {
      return Collections.emptyList();
    }
    List<SupertrendPoint> points = new ArrayList<>();
    String trend = "UP";
    BigDecimal line = atrSeries.get(0).mid();
    for (AtrBandPoint band : atrSeries) {
      BigDecimal upper = band.upper();
      BigDecimal lower = band.lower();
      if (band.mid().compareTo(line) > 0) {
        trend = "UP";
        line = lower;
      } else if (band.mid().compareTo(line) < 0) {
        trend = "DOWN";
        line = upper;
      }
      points.add(new SupertrendPoint(band.ts(), trend, line));
    }
    return points;
  }

  private int estimateLimit(String interval, Instant from, Instant to) {
    if (from == null || to == null) {
      return 1000;
    }
    long seconds = Math.max(1, to.getEpochSecond() - from.getEpochSecond());
    long intervalSeconds = intervalToSeconds(interval);
    long candles = seconds / intervalSeconds + 5;
    if (candles > Integer.MAX_VALUE) {
      return 10_000;
    }
    return (int) Math.min(Math.max(200, candles), 5000);
  }

  private long intervalToSeconds(String interval) {
    if (!StringUtils.hasText(interval)) {
      return 60;
    }
    char suffix = Character.toLowerCase(interval.charAt(interval.length() - 1));
    long value = Long.parseLong(interval.substring(0, interval.length() - 1));
    return switch (suffix) {
      case 'm' -> value * 60;
      case 'h' -> value * 3600;
      case 'd' -> value * 86400;
      default -> 60;
    };
  }

  private BigDecimal trueRange(Kline kline, BigDecimal prevClose) {
    BigDecimal highLow = kline.high().subtract(kline.low()).abs();
    if (prevClose == null) {
      return highLow;
    }
    BigDecimal highClose = kline.high().subtract(prevClose).abs();
    BigDecimal lowClose = kline.low().subtract(prevClose).abs();
    return highLow.max(highClose).max(lowClose);
  }
}
