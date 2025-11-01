package com.bottrading.chaos;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class ChaosClock extends Clock {

  private final Clock delegate;
  private final ChaosSuite chaosSuite;

  public ChaosClock(Clock delegate, ChaosSuite chaosSuite) {
    this.delegate = delegate;
    this.chaosSuite = chaosSuite;
  }

  @Override
  public ZoneId getZone() {
    return delegate.getZone();
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return new ChaosClock(delegate.withZone(zone), chaosSuite);
  }

  @Override
  public Instant instant() {
    long drift = chaosSuite.clockDriftMs();
    return delegate.instant().plusMillis(drift);
  }

  @Override
  public long millis() {
    return instant().toEpochMilli();
  }
}
