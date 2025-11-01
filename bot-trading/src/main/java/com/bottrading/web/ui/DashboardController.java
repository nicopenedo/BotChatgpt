package com.bottrading.web.ui;

import com.bottrading.config.TradingProps;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {

  private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

  private final TradingProps tradingProps;

  public DashboardController(TradingProps tradingProps) {
    this.tradingProps = tradingProps;
  }

  @GetMapping("/ui/dashboard")
  @PreAuthorize("hasRole('VIEWER')")
  public String dashboard(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String interval,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      Model model) {
    List<String> symbols = tradingProps.getSymbols();
    model.addAttribute("symbols", symbols);
    model.addAttribute("intervals", List.of("1m", "5m", "15m", "1h", "4h", "1d"));
    String defaultSymbol = symbol != null ? symbol : (symbols.isEmpty() ? tradingProps.getSymbol() : symbols.get(0));
    model.addAttribute("defaultSymbol", defaultSymbol);
    model.addAttribute("defaultInterval", interval != null ? interval : tradingProps.getInterval());
    Instant now = Instant.now();
    model.addAttribute("defaultTo", to != null ? to : ISO.format(now));
    model.addAttribute(
        "defaultFrom",
        from != null ? from : ISO.format(now.minusSeconds(7 * 24 * 3600)));
    return "dashboard";
  }
}
