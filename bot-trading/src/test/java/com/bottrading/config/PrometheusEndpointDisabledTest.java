package com.bottrading.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "observability.prometheus.enabled=false",
      "management.metrics.export.prometheus.enabled=false",
      "observability.prometheus.token=test-token",
      "management.endpoints.web.exposure.include=health,info,metrics"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrometheusEndpointDisabledTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void prometheusEndpointUnavailableWhenDisabled() throws Exception {
    mockMvc.perform(get("/actuator/prometheus"))
        .andExpect(status().isForbidden());
  }

  @Test
  void healthEndpointRemainsAccessible() throws Exception {
    mockMvc.perform(get("/actuator/health"))
        .andExpect(status().isOk());
  }
}
