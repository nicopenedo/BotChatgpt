package com.bottrading.strategy;

import java.util.ArrayList;
import java.util.List;

abstract class SignalTestSupport {

  protected List<String[]> series(double... closes) {
    List<String[]> klines = new ArrayList<>();
    long timestamp = 0L;
    for (double close : closes) {
      double high = close + 0.5;
      double low = close - 0.5;
      klines.add(
          new String[] {
            String.valueOf(timestamp),
            String.format("%.4f", close),
            String.format("%.4f", high),
            String.format("%.4f", low),
            String.format("%.4f", close),
            "100"
          });
      timestamp += 60000;
    }
    return klines;
  }
}
