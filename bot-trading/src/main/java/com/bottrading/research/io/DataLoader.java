package com.bottrading.research.io;

import com.bottrading.model.dto.Kline;
import com.bottrading.service.binance.BinanceClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class DataLoader {

  private final BinanceClient binanceClient;
  private final KlineCache cache;

  public DataLoader(BinanceClient binanceClient, KlineCache cache) {
    this.binanceClient = binanceClient;
    this.cache = cache;
  }

  public List<Kline> load(String symbol, String interval, Instant from, Instant to, boolean useCache) {
    if (useCache) {
      List<Kline> cached = cache.read(symbol, interval).orElse(null);
      if (cached != null && !cached.isEmpty()) {
        return cached;
      }
    }
    int limit = estimateLimit(from, to, interval);
    List<Kline> klines = binanceClient.getKlines(symbol, interval, limit);
    if (useCache) {
      cache.write(symbol, interval, klines);
    }
    return klines;
  }

  private int estimateLimit(Instant from, Instant to, String interval) {
    if (from == null || to == null) {
      return 1000;
    }
    long minutes = Math.max(1, Duration.between(from, to).toMinutes());
    int factor = switch (interval) {
      case "1m" -> 1;
      case "5m" -> 5;
      case "15m" -> 15;
      case "1h" -> 60;
      default -> 1;
    };
    long candles = minutes / factor;
    candles = Math.min(1000, Math.max(100, candles));
    return (int) candles;
  }
}
