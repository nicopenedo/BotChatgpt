package com.bottrading.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "PROMETHEUS_ENABLED=true",
      "MANAGEMENT_ENDPOINTS_INCLUDE=health,info,metrics,prometheus",
      "PROMETHEUS_TOKEN=test-token",
      "PROMETHEUS_ALLOWLIST=10.0.0.0/8"
    })
@ActiveProfiles("prod")
class PrometheusEndpointEnabledTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void exposesPrometheusWhenEnabledAndTokenProvided() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Prometheus-Token", "test-token");
    headers.set("X-Forwarded-For", "10.20.30.40");

    ResponseEntity<String> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/actuator/prometheus",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .isNotNull()
        .contains("jvm_threads_live")
        .contains("process_uptime_seconds");
  }

  @Test
  void rejectsPrometheusScrapeWithoutToken() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/actuator/prometheus", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void rejectsPrometheusScrapeOutsideAllowlist() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Prometheus-Token", "test-token");
    headers.set("X-Forwarded-For", "192.168.50.1");

    ResponseEntity<String> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/actuator/prometheus",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
