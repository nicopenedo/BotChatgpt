package com.bottrading.notify;

import com.bottrading.config.TelegramProperties;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class TelegramNotifier {

  private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);
  private final TelegramProperties properties;
  private final RestTemplate restTemplate = new RestTemplate();

  public TelegramNotifier(TelegramProperties properties) {
    this.properties = properties;
  }

  public void notifyFill(String symbol, String side, BigDecimal qty, BigDecimal price, String orderId) {
    send("Fill %s %s qty=%s price=%s orderId=%s".formatted(symbol, side, qty, price, orderId));
  }

  public void notifyStopHit(String symbol, com.bottrading.model.enums.OrderSide side, BigDecimal price, BigDecimal pnl) {
    send("Stop hit %s side=%s price=%s pnl=%s".formatted(symbol, side, price, pnl));
  }

  public void notifyBreakeven(String symbol, BigDecimal price) {
    send("Breakeven moved %s price=%s".formatted(symbol, price));
  }

  public void notifyTrailingAdjustment(String symbol, BigDecimal stop) {
    send("Trailing adjusted %s stop=%s".formatted(symbol, stop));
  }

  public void notifyDivergence(String symbol, BigDecimal livePnl, BigDecimal shadowPnl, BigDecimal thresholdPct) {
    send(
        "Shadow divergence %s live=%s shadow=%s diffPct=%s"
            .formatted(symbol, livePnl, shadowPnl, thresholdPct));
  }

  public void notifyBnbTopup(BigDecimal amountBnb, BigDecimal costQuote) {
    send("BNB top-up qty=%s cost=%s".formatted(amountBnb, costQuote));
  }

  public void notifyKillSwitch(boolean enabled) {
    send("Kill switch=" + enabled);
  }

  public void notifyError(String message) {
    send("Error: " + message);
  }

  private void send(String message) {
    if (!properties.isEnabled()) {
      log.debug("Telegram disabled: {}", message);
      return;
    }
    if (properties.getBotToken() == null || properties.getChatId() == null) {
      log.warn("Telegram not configured");
      return;
    }
    try {
      URI uri =
          URI.create(
              "https://api.telegram.org/bot" + properties.getBotToken() + "/sendMessage");
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      Map<String, Object> payload =
          Map.of("chat_id", properties.getChatId(), "text", message, "parse_mode", "Markdown");
      restTemplate.postForEntity(uri, new HttpEntity<>(payload, headers), Void.class);
    } catch (Exception ex) {
      log.warn("Failed to send telegram message: {}", ex.getMessage());
    }
  }
}
