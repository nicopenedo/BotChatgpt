package com.bottrading.config.props;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "alerts")
public class AlertsProps {

  private boolean emailEnabled = true;
  private boolean telegramEnabled = false;
  private String telegramBotToken;
  private String telegramChatId;

  public boolean isEmailEnabled() {
    return emailEnabled;
  }

  public void setEmailEnabled(boolean emailEnabled) {
    this.emailEnabled = emailEnabled;
  }

  public boolean isTelegramEnabled() {
    return telegramEnabled;
  }

  public void setTelegramEnabled(boolean telegramEnabled) {
    this.telegramEnabled = telegramEnabled;
  }

  public String getTelegramBotToken() {
    return telegramBotToken;
  }

  public void setTelegramBotToken(String telegramBotToken) {
    this.telegramBotToken = telegramBotToken;
  }

  public String getTelegramChatId() {
    return telegramChatId;
  }

  public void setTelegramChatId(String telegramChatId) {
    this.telegramChatId = telegramChatId;
  }

  @AssertTrue(message = "Telegram bot token and chat id are required when telegram alerts are enabled")
  public boolean isTelegramConfigurationValid() {
    if (!telegramEnabled) {
      return true;
    }
    return telegramBotToken != null
        && !telegramBotToken.isBlank()
        && telegramChatId != null
        && !telegramChatId.isBlank();
  }
}
