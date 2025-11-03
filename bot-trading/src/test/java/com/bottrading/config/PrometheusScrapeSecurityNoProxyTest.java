package com.bottrading.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(
    properties = {
      "observability.prometheus.enabled=true",
      "management.metrics.export.prometheus.enabled=true",
      "observability.prometheus.allowlist-cidrs=1.2.3.4/32",
      "observability.prometheus.trusted-proxies-cidrs=",
      "observability.prometheus.token=test-token",
      "management.endpoints.web.exposure.include=health,info,metrics,prometheus"
    })
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class PrometheusScrapeSecurityNoProxyTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void allowsGetWhenRemoteInAllowlistIgnoringForgedForwardedFor() throws Exception {
    mockMvc
        .perform(
            get("/actuator/prometheus")
                .with(remoteAddr("1.2.3.4"))
                .header("X-Forwarded-For", "10.0.0.1")
                .header("X-Prometheus-Token", "test-token"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void rejectsWhenTokenMissing() throws Exception {
    mockMvc
        .perform(get("/actuator/prometheus").with(remoteAddr("1.2.3.4")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsNonGetMethods() throws Exception {
    mockMvc
        .perform(
            post("/actuator/prometheus")
                .contentType(MediaType.APPLICATION_JSON)
                .with(remoteAddr("1.2.3.4"))
                .header("X-Prometheus-Token", "test-token"))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  void healthEndpointRemainsAccessible() throws Exception {
    mockMvc.perform(get("/actuator/health"))
        .andExpect(status().isOk());
  }

  private static RequestPostProcessor remoteAddr(String ip) {
    return request -> {
      request.setRemoteAddr(ip);
      return request;
    };
  }
}
