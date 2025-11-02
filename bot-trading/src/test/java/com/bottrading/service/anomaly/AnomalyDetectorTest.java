package com.bottrading.service.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.config.AnomalyProperties;
import com.bottrading.saas.config.SaasProperties;
import com.bottrading.saas.service.TenantMetrics;
import com.bottrading.service.risk.RiskFlag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.bottrading.saas.repository.TenantRepository;

class AnomalyDetectorTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

  private SimpleMeterRegistry meterRegistry;
  private TenantMetrics tenantMetrics;

  @BeforeEach
  void setup() {
    meterRegistry = new SimpleMeterRegistry();
    tenantMetrics =
        new TenantMetrics(Mockito.mock(TenantRepository.class), new SaasProperties(), CLOCK);
  }

  @Test
  void highSlippageTriggersSizeDown() {
    AnomalyProperties properties = new AnomalyProperties();
    properties.setWindow(20);
    properties.setMinSamples(5);
    properties.setCoolDownSec(60);
    properties.getZscore().setWarn(1.0);
    properties.getZscore().setMitigate(1.5);
    properties.getZscore().setHigh(2.0);
    properties.getZscore().setSevere(3.0);
    properties.setActions(Map.of("high", "SIZE_DOWN_50"));

    StubPublisher publisher = new StubPublisher();
    StubRiskAdapter riskAdapter = new StubRiskAdapter();
    AnomalyDetector detector =
        new AnomalyDetector(properties, meterRegistry, publisher, riskAdapter, CLOCK, tenantMetrics);

    double[] baseline = {1.0, 1.1, 0.9, 1.05, 0.95, 1.02, 0.97};
    for (double sample : baseline) {
      detector.recordSlippage("BTCUSDT", sample);
    }

    detector.recordSlippage("BTCUSDT", 5.0);

    assertThat(detector.sizingMultiplier("BTCUSDT")).isCloseTo(0.5, withinTolerance());
    assertThat(publisher.notifications).hasSize(1);
    AnomalyNotification notification = publisher.notifications.getFirst();
    assertThat(notification.severity()).isEqualTo(AnomalySeverity.HIGH);
    assertThat(notification.metric()).isEqualTo(AnomalyMetric.SLIPPAGE_BPS);
    assertThat(riskAdapter.flags).isEmpty();
  }

  @Test
  void sustainedApiErrorsTriggerPause() {
    AnomalyProperties properties = new AnomalyProperties();
    properties.setWindow(50);
    properties.setMinSamples(10);
    properties.setCoolDownSec(300);
    properties.getZscore().setWarn(1.0);
    properties.getZscore().setMitigate(1.5);
    properties.getZscore().setHigh(2.5);
    properties.getZscore().setSevere(3.0);
    properties.setActions(Map.of("severe", "PAUSE", "high", "SIZE_DOWN_50"));

    StubPublisher publisher = new StubPublisher();
    StubRiskAdapter riskAdapter = new StubRiskAdapter();
    AnomalyDetector detector =
        new AnomalyDetector(properties, meterRegistry, publisher, riskAdapter, CLOCK, tenantMetrics);

    for (int i = 0; i < 20; i++) {
      detector.recordApiCall("BTCUSDT", 80, true);
    }
    for (int i = 0; i < 6; i++) {
      detector.recordApiCall("BTCUSDT", 120, false);
    }

    assertThat(detector.sizingMultiplier("BTCUSDT")).isCloseTo(0.0, withinTolerance());
    assertThat(riskAdapter.flags).containsExactly(RiskFlag.ANOMALY_API_ERRORS);
    AnomalyDetector.AnomalySnapshot snapshot = detector.snapshot("BTCUSDT").orElseThrow();
    assertThat(snapshot.severity()).isEqualTo("SEVERE");
    assertThat(publisher.notifications).isNotEmpty();
  }

  @Test
  void warnLevelAnomalyOnlyAlerts() {
    AnomalyProperties properties = new AnomalyProperties();
    properties.setWindow(50);
    properties.setMinSamples(10);
    properties.setCoolDownSec(120);
    properties.getZscore().setWarn(1.0);
    properties.getZscore().setMitigate(3.0);
    properties.getZscore().setHigh(4.0);
    properties.getZscore().setSevere(5.0);

    StubPublisher publisher = new StubPublisher();
    StubRiskAdapter riskAdapter = new StubRiskAdapter();
    AnomalyDetector detector =
        new AnomalyDetector(properties, meterRegistry, publisher, riskAdapter, CLOCK);

    double[] baseline = {0.0, 0.0, 0.0, 0.0, 0.0, 1.0, -1.0, 1.0, -1.0, 0.0};
    for (double sample : baseline) {
      detector.recordSlippage("BTCUSDT", sample);
    }

    detector.recordSlippage("BTCUSDT", 1.0);

    assertThat(detector.sizingMultiplier("BTCUSDT")).isCloseTo(1.0, withinTolerance());
    assertThat(detector.executionOverride("BTCUSDT"))
        .isEqualTo(AnomalyDetector.ExecutionOverride.NONE);
    assertThat(publisher.notifications).hasSize(1);
    AnomalyNotification notification = publisher.notifications.getFirst();
    assertThat(notification.severity()).isEqualTo(AnomalySeverity.WARN);
    assertThat(notification.action()).isEqualTo(AnomalyAction.ALERT);
    assertThat(riskAdapter.flags).isEmpty();
  }

  @Test
  void mediumLatencyForcesMarketExecution() {
    AnomalyProperties properties = new AnomalyProperties();
    properties.setWindow(60);
    properties.setMinSamples(12);
    properties.setCoolDownSec(180);
    properties.getZscore().setWarn(1.0);
    properties.getZscore().setMitigate(2.0);
    properties.getZscore().setHigh(3.0);
    properties.getZscore().setSevere(4.0);
    properties.setActions(Map.of("medium", "SWITCH_TO_MARKET", "high", "SIZE_DOWN_50"));

    StubPublisher publisher = new StubPublisher();
    StubRiskAdapter riskAdapter = new StubRiskAdapter();
    AnomalyDetector detector =
        new AnomalyDetector(properties, meterRegistry, publisher, riskAdapter, CLOCK);

    double[] baselineLatency = {100, 100, 100, 100, 100, 101, 99, 101, 99, 100, 100, 100};
    for (double sample : baselineLatency) {
      detector.recordApiCall("BTCUSDT", (long) sample, true);
    }

    detector.recordApiCall("BTCUSDT", 104, true);

    assertThat(detector.sizingMultiplier("BTCUSDT")).isCloseTo(1.0, withinTolerance());
    assertThat(detector.executionOverride("BTCUSDT"))
        .isEqualTo(AnomalyDetector.ExecutionOverride.FORCE_MARKET);
    assertThat(publisher.notifications).hasSize(1);
    AnomalyNotification notification = publisher.notifications.getFirst();
    assertThat(notification.metric()).isEqualTo(AnomalyMetric.LATENCY_MS);
    assertThat(notification.severity()).isEqualTo(AnomalySeverity.MEDIUM);
    assertThat(notification.action()).isEqualTo(AnomalyAction.SWITCH_TO_MARKET);
    assertThat(riskAdapter.flags).isEmpty();
  }

  private static org.assertj.core.data.Offset<Double> withinTolerance() {
    return org.assertj.core.data.Offset.offset(1e-6);
  }

  private static class StubPublisher implements AnomalyAlertPublisher {
    private final List<AnomalyNotification> notifications = new ArrayList<>();

    @Override
    public void publish(AnomalyNotification notification) {
      notifications.add(notification);
    }
  }

  private static class StubRiskAdapter implements AnomalyRiskAdapter {
    private final List<RiskFlag> flags = new ArrayList<>();

    @Override
    public void applyPause(RiskFlag flag, java.time.Duration duration, String detail) {
      flags.add(flag);
    }
  }
}
