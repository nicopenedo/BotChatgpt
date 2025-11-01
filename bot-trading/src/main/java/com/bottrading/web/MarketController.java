package com.bottrading.web;

import com.bottrading.model.dto.Kline;
import com.bottrading.model.dto.PriceTicker;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.market.MarketDataService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketController {

  private final BinanceClient binanceClient;
  private final MarketDataService marketDataService;

  public MarketController(BinanceClient binanceClient, MarketDataService marketDataService) {
    this.binanceClient = binanceClient;
    this.marketDataService = marketDataService;
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
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) Integer limit) {
    Instant fromTs = parseInstant(from);
    Instant toTs = parseInstant(to);
    return ResponseEntity.ok(marketDataService.getKlines(symbol, interval, fromTs, toTs, limit));
  }

  private Instant parseInstant(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException("Invalid timestamp: " + value, ex);
    }
  }
}
