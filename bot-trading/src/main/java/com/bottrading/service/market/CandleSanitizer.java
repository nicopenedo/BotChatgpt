package com.bottrading.service.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CandleSanitizer {

  private static final Logger log = LoggerFactory.getLogger(CandleSanitizer.class);

  private final ConcurrentMap<String, AtomicLong> lastCloses = new ConcurrentHashMap<>();

  public List<Long> sanitize(String symbol, String interval, long closeTime) {
    Objects.requireNonNull(symbol, "symbol");
    Objects.requireNonNull(interval, "interval");
    AtomicLong reference = lastCloses.computeIfAbsent(key(symbol, interval), k -> new AtomicLong(-1));
    long previous = reference.get();
    if (previous >= closeTime) {
      log.debug("Duplicate or stale candle detected for {} {} closeTime={} previous={}", symbol, interval, closeTime, previous);
      return List.of();
    }
    long step = intervalToMillis(interval);
    List<Long> output = new ArrayList<>();
    if (previous > 0 && step > 0) {
      long expected = previous + step;
      while (expected < closeTime) {
        log.debug("Gap detected for {} {} expected={} actual={}", symbol, interval, expected, closeTime);
        output.add(expected);
        expected += step;
      }
    }
    output.add(closeTime);
    reference.set(closeTime);
    return output;
  }

  private String key(String symbol, String interval) {
    return symbol + "|" + interval;
  }

  private long intervalToMillis(String interval) {
    if (interval == null || interval.isBlank()) {
      return TimeUnit.MINUTES.toMillis(1);
    }
    try {
      char suffix = Character.toLowerCase(interval.charAt(interval.length() - 1));
      long value = Long.parseLong(interval.substring(0, interval.length() - 1));
      return switch (suffix) {
        case 'm' -> TimeUnit.MINUTES.toMillis(value);
        case 'h' -> TimeUnit.HOURS.toMillis(value);
        case 'd' -> TimeUnit.DAYS.toMillis(value);
        default -> TimeUnit.MINUTES.toMillis(1);
      };
    } catch (NumberFormatException ex) {
      log.warn("Unable to parse interval '{}', defaulting to 1m", interval);
      return TimeUnit.MINUTES.toMillis(1);
    }
  }

  public long intervalMillis(String interval) {
    return intervalToMillis(interval);
  }
}
