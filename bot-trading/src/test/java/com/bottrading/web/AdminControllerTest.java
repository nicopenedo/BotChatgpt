package com.bottrading.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bottrading.config.TradingProps;
import com.bottrading.service.health.HealthService;
import com.bottrading.service.risk.RiskFlag;
import com.bottrading.service.risk.RiskGuard;
import com.bottrading.service.risk.RiskMode;
import com.bottrading.service.risk.RiskState;
import com.bottrading.service.risk.drift.DriftWatchdog;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private RiskGuard riskGuard;
  @MockBean private TradingProps tradingProps;
  @MockBean private DriftWatchdog driftWatchdog;
  @MockBean private HealthService healthService;

  @Test
  @WithMockUser(roles = "ADMIN")
  void shouldChangeModeViaAdminEndpoint() throws Exception {
    mockMvc.perform(post("/admin/mode/PAUSED")).andExpect(status().isOk());
    verify(riskGuard).setMode(RiskMode.PAUSED);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void shouldExposeRiskStatus() throws Exception {
    RiskState state =
        new RiskState(
            RiskMode.SHADOW,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            1,
            2,
            BigDecimal.valueOf(1000),
            EnumSet.of(RiskFlag.API_ERRORS),
            Instant.now());
    when(riskGuard.getState()).thenReturn(state);

    mockMvc
        .perform(get("/admin/risk/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("SHADOW"))
        .andExpect(jsonPath("$.riskFlags[0]").value("API_ERRORS"));
  }
}
