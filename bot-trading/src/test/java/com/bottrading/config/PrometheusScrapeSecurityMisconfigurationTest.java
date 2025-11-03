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
    properties = {
      "observability.prometheus.enabled=true",
      "management.metrics.export.prometheus.enabled=true",
      "observability.prometheus.allowlist-cidrs=",
      "observability.prometheus.trusted-proxies-cidrs=",
      "observability.prometheus.token=",
      "management.endpoints.web.exposure.include=health,info,metrics,prometheus"
    })
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class PrometheusScrapeSecurityMisconfigurationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void deniesWhenNoTokenNorAllowlistConfigured() throws Exception {
    mockMvc.perform(get("/actuator/prometheus"))
        .andExpect(status().isForbidden());
  }
}
