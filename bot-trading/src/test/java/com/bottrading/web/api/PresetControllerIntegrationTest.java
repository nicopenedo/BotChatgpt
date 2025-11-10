package com.bottrading.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PresetControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  @WithMockUser(roles = "ADMIN")
  void importAndActivatePreset() throws Exception {
    MockMultipartFile params =
        new MockMultipartFile(
            "params", "preset.yaml", MediaType.APPLICATION_JSON_VALUE, "{\"presetKey\":\"alpha\"}".getBytes(StandardCharsets.UTF_8));
    MockMultipartFile metrics =
        new MockMultipartFile(
            "metrics",
            "metrics.json",
            MediaType.APPLICATION_JSON_VALUE,
            "{\"PF\":2.0,\"MaxDD\":5.0,\"Trades\":200}".getBytes(StandardCharsets.UTF_8));

    String response =
        mockMvc
            .perform(
                multipart("/api/presets/import")
                    .file(params)
                    .file(metrics)
                    .param("regime", "UP")
                    .param("side", "BUY")
                    .param("runId", "RUN_API")
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode node = objectMapper.readTree(response);
    UUID presetId = UUID.fromString(node.get("id").asText());

    mockMvc
        .perform(post("/api/presets/" + presetId + "/activate").with(csrf()))
        .andExpect(status().isOk());

    String detail =
        mockMvc
            .perform(get("/api/presets/" + presetId).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(detail).contains("status");
  }
}
