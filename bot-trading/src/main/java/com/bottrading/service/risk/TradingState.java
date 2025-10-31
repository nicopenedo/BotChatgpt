package com.bottrading.service.risk;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class TradingState {

  private final AtomicBoolean killSwitch = new AtomicBoolean(false);
  private final AtomicBoolean liveEnabled = new AtomicBoolean(false);
  private final AtomicReference<Instant> cooldownUntil = new AtomicReference<>(Instant.EPOCH);

  public boolean isKillSwitchActive() {
    return killSwitch.get();
  }

  public void activateKillSwitch() {
    killSwitch.set(true);
  }

  public void deactivateKillSwitch() {
    killSwitch.set(false);
  }

  public boolean isLiveEnabled() {
    return liveEnabled.get();
  }

  public void setLiveEnabled(boolean enabled) {
    liveEnabled.set(enabled);
  }

  public void setCooldownUntil(Instant until) {
    cooldownUntil.set(until);
  }

  public Instant getCooldownUntil() {
    return cooldownUntil.get();
  }

  public boolean isCoolingDown() {
    return Instant.now().isBefore(cooldownUntil.get());
  }
}
