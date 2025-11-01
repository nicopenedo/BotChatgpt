package com.bottrading.service.anomaly;

import com.bottrading.notify.TelegramNotifier;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class TelegramAnomalyAlertPublisher implements AnomalyAlertPublisher {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ISO_LOCAL_TIME.withZone(ZoneId.of("UTC"));

  private final TelegramNotifier notifier;

  public TelegramAnomalyAlertPublisher(TelegramNotifier notifier) {
    this.notifier = notifier;
  }

  @Override
  public void publish(AnomalyNotification notification) {
    StringBuilder builder = new StringBuilder();
    builder
        .append("Anomaly ")
        .append(notification.symbol())
        .append(' ')
        .append(notification.metric().id())
        .append(" severity=")
        .append(notification.severity().name())
        .append(" action=")
        .append(notification.action().name());
    builder
        .append(" value=")
        .append(String.format("%.4f", notification.value()))
        .append(" mean=")
        .append(String.format("%.4f", notification.mean()))
        .append(" z=")
        .append(String.format("%.2f", notification.zScore()));
    builder.append(" · ").append(notification.detail());
    Instant expires = notification.expiresAt();
    if (expires != null) {
      Duration remaining = Duration.between(Instant.now(), expires);
      long minutes = Math.max(0, remaining.toMinutes());
      builder
          .append(" · cooldown=")
          .append(minutes)
          .append("m (until ")
          .append(FORMATTER.format(expires))
          .append(')');
    }
    notifier.notifyError(builder.toString());
  }
}
