package com.bottrading.service.risk;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class TradingState {

  public enum Mode {
    LIVE,
    SHADOW,
    PAUSED
  }

  private final AtomicBoolean killSwitch = new AtomicBoolean(false);
  private final AtomicBoolean liveEnabled = new AtomicBoolean(false);
  private final AtomicReference<Instant> cooldownUntil = new AtomicReference<>(Instant.EPOCH);
  private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.LIVE);

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
    return liveEnabled.get() && mode.get() == Mode.LIVE;
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

  public void setMode(Mode newMode) {
    mode.set(newMode);
  }

  public Mode getMode() {
    return mode.get();
  }
}
