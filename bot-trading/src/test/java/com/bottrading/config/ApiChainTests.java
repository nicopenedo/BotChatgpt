package com.bottrading.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bottrading.config.SecurityTestControllers.ApiController;
import com.bottrading.saas.security.MfaApiHeaderFilter;
import com.bottrading.saas.security.MfaUiSessionFilter;
import com.bottrading.saas.security.TenantContextFilter;
import com.bottrading.saas.security.TenantUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ApiController.class)
@Import({SecurityConfig.class, TenantContextFilter.class, MfaApiHeaderFilter.class, MfaUiSessionFilter.class})
class ApiChainTests {

  @Autowired private MockMvc mockMvc;

  @MockBean private TenantUserDetailsService userDetailsService;

  @MockBean private com.bottrading.saas.service.TotpService totpService;

  @Test
  void apiPostDoesNotRequireCsrfToken() throws Exception {
    mockMvc.perform(post("/api/foo").secure(true)).andExpect(status().isUnauthorized());
  }
}
