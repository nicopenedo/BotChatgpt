package com.bottrading.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bottrading.config.SecurityTestControllers.TenantSettingsController;
import com.bottrading.saas.security.MfaApiHeaderFilter;
import com.bottrading.saas.security.MfaUiSessionFilter;
import com.bottrading.saas.security.TenantContextFilter;
import com.bottrading.saas.security.TenantUserDetails;
import com.bottrading.saas.security.TenantUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TenantSettingsController.class)
@Import({
  SecurityConfig.class,
  TenantContextFilter.class,
  MfaApiHeaderFilter.class,
  MfaUiSessionFilter.class
})
class CsrfWebTests {

  @Autowired private MockMvc mockMvc;

  @org.springframework.boot.test.mock.mockito.MockBean
  private TenantUserDetailsService userDetailsService;

  @org.springframework.boot.test.mock.mockito.MockBean
  private com.bottrading.saas.security.TotpService totpService;

  private TenantUserDetails uiUser() {
    return TestTenantUsers.tenantUser("user@demo.local", false);
  }

  @Test
  void postWithoutCsrfIsForbidden() throws Exception {
    mockMvc
        .perform(
            post("/tenant/settings/notifications")
                .secure(true)
                .with(user(uiUser())))
        .andExpect(status().isForbidden());
  }

  @Test
  void postWithCsrfSucceeds() throws Exception {
    mockMvc
        .perform(
            post("/tenant/settings/notifications")
                .secure(true)
                .with(user(uiUser()))
                .with(csrf()))
        .andExpect(status().isOk());
  }
}
