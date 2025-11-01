package com.bottrading.service.risk;

import com.bottrading.config.RiskProperties;
import com.bottrading.execution.PositionManager;
import com.bottrading.notify.TelegramNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class RiskAction {

  private static final Logger log = LoggerFactory.getLogger(RiskAction.class);

  private final RiskProperties properties;
  private final TelegramNotifier notifier;
  private final ObjectProvider<PositionManager> positionManagerProvider;

  public RiskAction(
      RiskProperties properties,
      TelegramNotifier notifier,
      ObjectProvider<PositionManager> positionManagerProvider) {
    this.properties = properties;
    this.notifier = notifier;
    this.positionManagerProvider = positionManagerProvider;
  }

  public void onPause(RiskFlag flag, String detail, RiskState snapshot) {
    notifier.notifyError("Risk pause triggered by %s: %s".formatted(flag.name(), detail));
    if (properties.isForceCloseOnPause()) {
      PositionManager manager = positionManagerProvider.getIfAvailable();
      if (manager != null) {
        log.warn("Force closing positions due to risk pause ({}).", flag);
        manager.forceCloseAll();
      } else {
        log.warn("PositionManager unavailable; cannot force close after risk pause");
      }
    }
  }
}
