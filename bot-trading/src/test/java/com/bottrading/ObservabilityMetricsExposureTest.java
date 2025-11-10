package com.bottrading;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ObservabilityMetricsExposureTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void prometheusEndpointExposesNewMetrics() {
    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth("viewer", "viewerPass")
            .getForEntity("http://localhost:" + port + "/actuator/prometheus", String.class);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    String body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body)
        .contains("scheduler_candle_backlog_ms")
        .contains("binance_api_requests_total")
        .contains("risk_daily_loss_limit_pct")
        .contains("exec_limit_ttl_ms_sum")
        .contains("research_nightly_last_duration_seconds");
  }
}
