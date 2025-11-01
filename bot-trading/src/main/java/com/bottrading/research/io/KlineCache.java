package com.bottrading.research.io;

import com.bottrading.model.dto.Kline;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class KlineCache {

  private final Path cacheDir;

  public KlineCache(Path cacheDir) {
    this.cacheDir = cacheDir;
  }

  public Optional<List<Kline>> read(String symbol, String interval) {
    Path file = cacheDir.resolve(symbol + "-" + interval + ".csv");
    if (!Files.exists(file)) {
      return Optional.empty();
    }
    try {
      List<Kline> klines = new ArrayList<>();
      List<String> lines = Files.readAllLines(file);
      for (String line : lines) {
        if (line.startsWith("timestamp")) {
          continue;
        }
        String[] parts = line.split(",");
        klines.add(
            new Kline(
                Instant.ofEpochMilli(Long.parseLong(parts[0])),
                new BigDecimal(parts[1]),
                new BigDecimal(parts[2]),
                new BigDecimal(parts[3]),
                new BigDecimal(parts[4]),
                new BigDecimal(parts[5])));
      }
      return Optional.of(klines);
    } catch (IOException ex) {
      return Optional.empty();
    }
  }

  public void write(String symbol, String interval, List<Kline> klines) {
    try {
      if (!Files.exists(cacheDir)) {
        Files.createDirectories(cacheDir);
      }
      Path file = cacheDir.resolve(symbol + "-" + interval + ".csv");
      List<String> lines = new ArrayList<>();
      lines.add("timestamp,open,high,low,close,volume");
      for (Kline kline : klines) {
        lines.add(
            "%d,%s,%s,%s,%s,%s"
                .formatted(
                    kline.openTime().toEpochMilli(),
                    kline.open().toPlainString(),
                    kline.high().toPlainString(),
                    kline.low().toPlainString(),
                    kline.close().toPlainString(),
                    kline.volume().toPlainString()));
      }
      Files.write(file, lines);
    } catch (IOException ex) {
      // ignore cache write errors
    }
  }
}
