package com.bottrading.research.regime;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RegimeLabelSet {

  private final Map<Instant, RegimeLabel> byTimestamp;
  private final List<RegimeLabel> ordered;

  public RegimeLabelSet(List<RegimeLabel> labels) {
    Map<Instant, RegimeLabel> temp = new LinkedHashMap<>();
    List<RegimeLabel> sorted = new ArrayList<>();
    if (labels != null) {
      for (RegimeLabel label : labels) {
        if (label == null || label.timestamp() == null) {
          continue;
        }
        temp.put(label.timestamp(), label);
        sorted.add(label);
      }
    }
    sorted.sort(Comparator.comparing(RegimeLabel::timestamp));
    this.byTimestamp = Map.copyOf(temp);
    this.ordered = List.copyOf(sorted);
  }

  public static RegimeLabelSet load(Path path) throws IOException {
    if (path == null) {
      return new RegimeLabelSet(List.of());
    }
    List<RegimeLabel> labels = new ArrayList<>();
    try (BufferedReader reader = Files.newBufferedReader(path)) {
      String line;
      boolean headerSkipped = false;
      while ((line = reader.readLine()) != null) {
        if (!headerSkipped) {
          headerSkipped = true;
          if (line.toLowerCase().contains("trend")) {
            continue;
          }
        }
        String[] parts = line.split(",");
        if (parts.length < 3) {
          continue;
        }
        try {
          Instant ts = Instant.parse(parts[0].trim());
          RegimeTrend trend = RegimeTrend.valueOf(parts[1].trim().toUpperCase());
          RegimeVolatility vol = RegimeVolatility.valueOf(parts[2].trim().toUpperCase());
          labels.add(new RegimeLabel(ts, trend, vol));
        } catch (Exception ignore) {
          // skip malformed rows
        }
      }
    }
    return new RegimeLabelSet(labels);
  }

  public void write(Path path) throws IOException {
    if (path == null) {
      return;
    }
    if (path.getParent() != null) {
      Files.createDirectories(path.getParent());
    }
    try (BufferedWriter writer = Files.newBufferedWriter(path)) {
      writer.write("ts,trend,vol");
      writer.newLine();
      for (RegimeLabel label : ordered) {
        writer
            .append(label.timestamp().toString())
            .append(",")
            .append(label.trend().name())
            .append(",")
            .append(label.volatility().name());
        writer.newLine();
      }
    }
  }

  public boolean isEmpty() {
    return ordered.isEmpty();
  }

  public RegimeLabel labelAt(Instant timestamp) {
    if (timestamp == null) {
      return null;
    }
    return byTimestamp.get(timestamp);
  }

  public long count(RegimeTrend trend, Instant from, Instant to) {
    if (trend == null || ordered.isEmpty()) {
      return 0;
    }
    return ordered.stream()
        .filter(label -> label.trend() == trend)
        .filter(label -> within(label.timestamp(), from, to))
        .count();
  }

  public List<RegimeLabel> between(RegimeTrend trend, Instant from, Instant to) {
    if (trend == null || ordered.isEmpty()) {
      return List.of();
    }
    List<RegimeLabel> subset = new ArrayList<>();
    for (RegimeLabel label : ordered) {
      if (label.trend() == trend && within(label.timestamp(), from, to)) {
        subset.add(label);
      }
    }
    return Collections.unmodifiableList(subset);
  }

  public List<RegimeLabel> labels() {
    return ordered;
  }

  private boolean within(Instant ts, Instant from, Instant to) {
    if (ts == null) {
      return false;
    }
    boolean after = from == null || !ts.isBefore(from);
    boolean before = to == null || !ts.isAfter(to);
    return after && before;
  }
}
