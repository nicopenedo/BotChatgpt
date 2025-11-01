package com.bottrading.web.ui;

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

  @GetMapping("/ui/dashboard")
  @PreAuthorize("hasRole('VIEWER')")
  public String dashboard(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String interval,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      Model model) {
    model.addAttribute("symbols", List.of("BTCUSDT", "ETHUSDT", "BNBUSDT"));
    model.addAttribute("intervals", List.of("1m", "5m", "15m", "1h", "4h", "1d"));
    model.addAttribute("defaultSymbol", symbol != null ? symbol : "BTCUSDT");
    model.addAttribute("defaultInterval", interval != null ? interval : "1h");
    Instant now = Instant.now();
    model.addAttribute("defaultTo", to != null ? to : ISO.format(now));
    model.addAttribute(
        "defaultFrom",
        from != null ? from : ISO.format(now.minusSeconds(7 * 24 * 3600)));
    return "dashboard";
  }
}
