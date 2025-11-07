package com.bottrading.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bottrading.saas.security.MfaApiHeaderFilter;
import com.bottrading.saas.security.MfaUiSessionFilter;
import com.bottrading.saas.security.TenantContextFilter;
import com.bottrading.saas.security.TenantUserDetailsService;
import com.bottrading.saas.service.TotpService;
import com.bottrading.web.api.WebhookController;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = WebhookController.class, properties = "webhook.provider.secret=test-secret")
@Import({SecurityConfig.class, TenantContextFilter.class, MfaApiHeaderFilter.class, MfaUiSessionFilter.class})
class WebhookSignatureTests {

  @Autowired private MockMvc mockMvc;

  @MockBean private TenantUserDetailsService userDetailsService;

  @MockBean private TotpService totpService;

  @Test
  void validSignatureIsAccepted() throws Exception {
    byte[] payload = "{\"event\":\"created\"}".getBytes(StandardCharsets.UTF_8);
    String signature = sign(payload, "test-secret");

    mockMvc
        .perform(
            post("/webhooks/provider")
                .secure(true)
                .content(payload)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", signature))
        .andExpect(status().isOk());
  }

  @Test
  void invalidSignatureIsRejected() throws Exception {
    byte[] payload = "{\"event\":\"created\"}".getBytes(StandardCharsets.UTF_8);
    String signature = sign(payload, "other-secret");

    mockMvc
        .perform(
            post("/webhooks/provider")
                .secure(true)
                .content(payload)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", signature))
        .andExpect(status().isUnauthorized());
  }

  private String sign(byte[] payload, String secret) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return Hex.encodeHexString(mac.doFinal(payload));
  }
}
