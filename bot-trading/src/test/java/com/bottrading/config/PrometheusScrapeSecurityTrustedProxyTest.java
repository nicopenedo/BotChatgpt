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
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "observability.prometheus.enabled=true",
      "management.metrics.export.prometheus.enabled=true",
      "observability.prometheus.allowlist-cidrs=203.0.113.1/32",
      "observability.prometheus.trusted-proxies-cidrs=10.0.0.0/8",
      "observability.prometheus.token=",
      "management.endpoints.web.exposure.include=health,info,metrics,prometheus"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrometheusScrapeSecurityTrustedProxyTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void resolvesClientFromTrustedProxyChain() throws Exception {
    mockMvc
        .perform(
            get("/actuator/prometheus")
                .with(remoteAddr("10.1.2.3"))
                .header("X-Forwarded-For", "203.0.113.1, 10.2.2.2"))
        .andExpect(status().isOk());
  }

  @Test
  void resolvesFromXRealIpWhenForwardedChainMissingClient() throws Exception {
    mockMvc
        .perform(
            get("/actuator/prometheus")
                .with(remoteAddr("10.1.2.3"))
                .header("X-Real-IP", "203.0.113.1"))
        .andExpect(status().isOk());
  }

  @Test
  void deniesWhenAllForwardedIpsAreTrusted() throws Exception {
    mockMvc
        .perform(
            get("/actuator/prometheus")
                .with(remoteAddr("10.1.2.3"))
                .header("X-Forwarded-For", "10.2.2.2, 10.3.3.3"))
        .andExpect(status().isForbidden());
  }

  @Test
  void ignoresForgedForwardedForFromUntrustedPeer() throws Exception {
    mockMvc
        .perform(
            get("/actuator/prometheus")
                .with(remoteAddr("203.0.113.55"))
                .header("X-Forwarded-For", "10.0.0.1"))
        .andExpect(status().isForbidden());
  }

  @Test
  void deniesWhenForwardedHeaderIsInvalid() throws Exception {
    mockMvc
        .perform(
            get("/actuator/prometheus")
                .with(remoteAddr("10.1.2.3"))
                .header("X-Forwarded-For", "malicious-host"))
        .andExpect(status().isForbidden());
  }

  private static RequestPostProcessor remoteAddr(String ip) {
    return request -> {
      request.setRemoteAddr(ip);
      return request;
    };
  }
}
