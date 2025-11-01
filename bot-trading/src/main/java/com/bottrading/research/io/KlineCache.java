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
        if (line.startsWith("open_time")) {
          continue;
        }
        String[] parts = line.split(",");
        Instant open = Instant.ofEpochMilli(Long.parseLong(parts[0]));
        Instant close =
            parts.length > 6
                ? Instant.ofEpochMilli(Long.parseLong(parts[1]))
                : open.plusSeconds(60);
        int offset = parts.length > 6 ? 1 : 0;
        klines.add(
            new Kline(
                open,
                close,
                new BigDecimal(parts[1 + offset]),
                new BigDecimal(parts[2 + offset]),
                new BigDecimal(parts[3 + offset]),
                new BigDecimal(parts[4 + offset]),
                new BigDecimal(parts[5 + offset])));
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
      lines.add("open_time,close_time,open,high,low,close,volume");
      for (Kline kline : klines) {
        lines.add(
            "%d,%d,%s,%s,%s,%s,%s"
                .formatted(
                    kline.openTime().toEpochMilli(),
                    kline.closeTime().toEpochMilli(),
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
