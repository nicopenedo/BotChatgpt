package com.bottrading.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bottrading.config.SecurityTestControllers.TenantDashboardController;
import com.bottrading.saas.security.MfaApiHeaderFilter;
import com.bottrading.saas.security.MfaUiSessionFilter;
import com.bottrading.saas.security.TenantContextFilter;
import com.bottrading.saas.security.TenantUserDetails;
import com.bottrading.saas.security.TenantUserDetailsService;
import com.bottrading.saas.service.TotpService;
import com.bottrading.web.mvc.controller.MfaController;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = {MfaController.class, TenantDashboardController.class})
@Import({SecurityConfig.class, TenantContextFilter.class, MfaApiHeaderFilter.class, MfaUiSessionFilter.class})
class MfaWebTests {

  @Autowired private MockMvc mockMvc;

  @MockBean private TenantUserDetailsService userDetailsService;

  @MockBean private TotpService totpService;

  private TenantUserDetails mfaUser;
  private TenantUserDetails noMfaUser;

  @BeforeEach
  void setup() {
    mfaUser = TestTenantUsers.tenantUser("mfa@demo.local", true);
    noMfaUser = TestTenantUsers.tenantUser("nomfa@demo.local", false);
    when(userDetailsService.loadUserByUsername("mfa@demo.local")).thenReturn(mfaUser);
    when(userDetailsService.loadUserByUsername("nomfa@demo.local")).thenReturn(noMfaUser);
  }

  @Test
  void loginWithMfaRedirectsToChallenge() throws Exception {
    mockMvc
        .perform(formLogin().user("mfa@demo.local").password("password").secure(true))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/mfa"));
  }

  @Test
  void tenantAccessWithoutMfaVerificationRedirects() throws Exception {
    mockMvc
        .perform(get("/tenant/dashboard").secure(true).with(user(mfaUser)))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/mfa"));
  }

  @Test
  void successfulMfaUnlocksTenantArea() throws Exception {
    when(totpService.verify(any(), eq("123456"))).thenReturn(true);

    MvcResult result =
        mockMvc
            .perform(
                post("/mfa")
                    .secure(true)
                    .with(user(mfaUser))
                    .with(csrf())
                    .param("code", "123456"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/tenant/dashboard"))
            .andReturn();

    HttpSession session = result.getRequest().getSession(false);
    assertThat(session).isInstanceOf(MockHttpSession.class);
    assertThat(session.getAttribute("mfaVerified")).isEqualTo(Boolean.TRUE);

    mockMvc
        .perform(get("/tenant/dashboard").secure(true).session((MockHttpSession) session))
        .andExpect(status().isOk());
  }

  @Test
  void loginWithoutMfaGoesDirectlyToDashboard() throws Exception {
    mockMvc
        .perform(formLogin().user("nomfa@demo.local").password("password").secure(true))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/tenant/dashboard"));
  }
}
