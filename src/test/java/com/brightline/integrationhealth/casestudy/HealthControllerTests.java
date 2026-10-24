package com.brightline.integrationhealth.casestudy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the HealthController REST API.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/integrations/health returns 200 with JSON")
    void shouldReturnHealthDashboard() throws Exception {
        mockMvc.perform(get("/api/integrations/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.summary.totalIntegrations").value(5))
                .andExpect(jsonPath("$.summary.totalRequests").value(60))
                .andExpect(jsonPath("$.summary.overallStatus").exists())
                .andExpect(jsonPath("$.integrations").isArray())
                .andExpect(jsonPath("$.integrations.length()").value(5));
    }

    @Test
    @DisplayName("Each integration has required fields")
    void shouldContainRequiredFields() throws Exception {
        mockMvc.perform(get("/api/integrations/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integrations[0].integration").isString())
                .andExpect(jsonPath("$.integrations[0].totalRequests").isNumber())
                .andExpect(jsonPath("$.integrations[0].successfulRequests").isNumber())
                .andExpect(jsonPath("$.integrations[0].failedRequests").isNumber())
                .andExpect(jsonPath("$.integrations[0].timeouts").isNumber())
                .andExpect(jsonPath("$.integrations[0].errorRate").isNumber())
                .andExpect(jsonPath("$.integrations[0].status").isString())
                .andExpect(jsonPath("$.integrations[0].reasons").isArray());
    }

    @Test
    @DisplayName("Summary contains overall error rate and generated timestamp")
    void shouldContainSummaryMetrics() throws Exception {
        mockMvc.perform(get("/api/integrations/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.overallErrorRate").isNumber())
                .andExpect(jsonPath("$.summary.totalFailures").isNumber())
                .andExpect(jsonPath("$.summary.totalTimeouts").isNumber())
                .andExpect(jsonPath("$.summary.generatedAt").isString());
    }
}
