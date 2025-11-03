package com.bottrading.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "PROMETHEUS_ENABLED=false",
      "MANAGEMENT_ENDPOINTS_INCLUDE=health,info,metrics",
      "PROMETHEUS_TOKEN=test-token"
    })
@ActiveProfiles("prod")
class PrometheusEndpointDisabledTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void prometheusEndpointUnavailableWhenDisabled() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/actuator/prometheus", String.class);

    assertThat(response.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
  }

  @Test
  void healthEndpointRemainsAccessible() {
    ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
