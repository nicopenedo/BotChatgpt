package com.bottrading.fees;

import com.bottrading.config.FeeProperties;
import com.bottrading.service.binance.BinanceClient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class FeeService {

  private final BinanceClient binanceClient;
  private final FeeProperties properties;
  private final Cache<String, FeeInfo> cache;
  private final MeterRegistry meterRegistry;
  private final Map<String, FeeInfo> lastFees = new ConcurrentHashMap<>();

  public FeeService(BinanceClient binanceClient, FeeProperties properties, MeterRegistry meterRegistry) {
    this.binanceClient = binanceClient;
    this.properties = properties;
    this.meterRegistry = meterRegistry;
    this.cache =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(Math.max(1, properties.getCacheMinutes())))
            .build();
  }

  public FeeInfo effectiveFees(String symbol, boolean payWithBnb) {
    FeeInfo info =
        cache.get(
            symbol,
            key -> {
              BigDecimal commission = binanceClient.getTradingCommission(key);
              BigDecimal maker = commission;
              BigDecimal taker = commission;
              int vipLevel = 0;
              boolean payingWithBnb = payWithBnb || properties.isPayWithBnb();
              if (payingWithBnb) {
                maker = maker.multiply(BigDecimal.valueOf(0.75)).setScale(8, RoundingMode.HALF_UP);
                taker = taker.multiply(BigDecimal.valueOf(0.75)).setScale(8, RoundingMode.HALF_UP);
              }
              FeeInfo calculated = new FeeInfo(maker, taker, vipLevel, payingWithBnb);
              lastFees.put(symbol, calculated);
              Gauge.builder("fees.effective.maker", calculated, FeeInfo::makerAsDouble)
                  .tags("symbol", symbol)
                  .register(meterRegistry);
              Gauge.builder("fees.effective.taker", calculated, FeeInfo::takerAsDouble)
                  .tags("symbol", symbol)
                  .register(meterRegistry);
              return calculated;
            });
    lastFees.put(symbol, info);
    return info;
  }

  public FeeInfo effectiveFees(String symbol) {
    return effectiveFees(symbol, properties.isPayWithBnb());
  }

  public void evict(String symbol) {
    cache.invalidate(symbol);
    lastFees.remove(symbol);
  }

  public record FeeInfo(BigDecimal maker, BigDecimal taker, int vipLevel, boolean payingWithBnb) {
    double makerAsDouble() {
      return maker.doubleValue();
    }

    double takerAsDouble() {
      return taker.doubleValue();
    }
  }
}
