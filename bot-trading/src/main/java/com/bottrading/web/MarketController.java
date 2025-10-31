package com.bottrading.web;

import com.bottrading.model.dto.Kline;
import com.bottrading.model.dto.PriceTicker;
import com.bottrading.service.binance.BinanceClient;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketController {

  private final BinanceClient binanceClient;

  public MarketController(BinanceClient binanceClient) {
    this.binanceClient = binanceClient;
  }

  @GetMapping("/price")
  @PreAuthorize("hasRole('READ')")
  public ResponseEntity<PriceTicker> getPrice(@RequestParam String symbol) {
    return ResponseEntity.ok(binanceClient.getPrice(symbol));
  }

  @GetMapping("/klines")
  @PreAuthorize("hasRole('READ')")
  public ResponseEntity<List<Kline>> getKlines(
      @RequestParam String symbol,
      @RequestParam(defaultValue = "1m") String interval,
      @RequestParam(defaultValue = "200") int limit) {
    return ResponseEntity.ok(binanceClient.getKlines(symbol, interval, limit));
  }
}
