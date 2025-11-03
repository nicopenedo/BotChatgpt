package com.bottrading.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(
    properties = {
      "management.metrics.export.prometheus.enabled=true",
      "prometheus.allowlist=127.0.0.1/32",
      "management.endpoints.web.exposure.include=health,info,metrics,prometheus"
    })
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class PrometheusScrapeSecurityLegacyAllowlistTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void allowsScrapeFromLegacyAllowlist() throws Exception {
    mockMvc.perform(get("/actuator/prometheus").with(remoteAddr("127.0.0.1")))
        .andExpect(status().isOk());
  }

  @Test
  void blocksScrapeOutsideLegacyAllowlist() throws Exception {
    mockMvc.perform(get("/actuator/prometheus").with(remoteAddr("1.2.3.4")))
        .andExpect(status().isForbidden());
  }

  private static RequestPostProcessor remoteAddr(String ip) {
    return request -> {
      request.setRemoteAddr(ip);
      return request;
    };
  }
}
