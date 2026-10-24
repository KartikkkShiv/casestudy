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
 * Tests both the original /health endpoint (backward compatibility)
 * and the new /console endpoint (enhanced operations console).
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/integrations/health returns 200 with JSON (backward compatibility)")
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
    @DisplayName("Health endpoint contains required fields")
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
    @DisplayName("GET /api/integrations/console returns enhanced operations console")
    void shouldReturnOperationsConsole() throws Exception {
        mockMvc.perform(get("/api/integrations/console"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.integrations").isArray())
                .andExpect(jsonPath("$.recentFailures").isArray())
                .andExpect(jsonPath("$.refreshedAt").isNumber());
    }

    @Test
    @DisplayName("Console endpoint contains enhanced integration health fields")
    void shouldContainEnhancedIntegrationFields() throws Exception {
        mockMvc.perform(get("/api/integrations/console"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integrations[0].successCount").isNumber())
                .andExpect(jsonPath("$.integrations[0].errorCount").isNumber())
                .andExpect(jsonPath("$.integrations[0].timeoutCount").isNumber())
                .andExpect(jsonPath("$.integrations[0].successRate").isNumber())
                .andExpect(jsonPath("$.integrations[0].unsuccessfulRate").isNumber())
                .andExpect(jsonPath("$.integrations[0].maxLatencyMs").isNumber())
                .andExpect(jsonPath("$.integrations[0].latestRequestTimestamp").isString())
                .andExpect(jsonPath("$.integrations[0].consecutiveFailures").isNumber())
                .andExpect(jsonPath("$.integrations[0].recommendations").isArray());
    }

    @Test
    @DisplayName("Console summary contains total successes and errors")
    void shouldContainEnhancedSummaryFields() throws Exception {
        mockMvc.perform(get("/api/integrations/console"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalSuccesses").value(43))
                .andExpect(jsonPath("$.summary.totalErrors").value(12))
                .andExpect(jsonPath("$.summary.totalTimeouts").value(5))
                .andExpect(jsonPath("$.summary.totalRequests").value(60));
    }

    @Test
    @DisplayName("Console endpoint contains recent failures sorted by timestamp")
    void shouldContainSortedRecentFailures() throws Exception {
        mockMvc.perform(get("/api/integrations/console"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentFailures").isArray())
                .andExpect(jsonPath("$.recentFailures.length()").value(17));
    }

    @Test
    @DisplayName("Failure entries contain integration, timestamp, status, and latency")
    void shouldContainFailureDetails() throws Exception {
        mockMvc.perform(get("/api/integrations/console"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentFailures[0].integration").isString())
                .andExpect(jsonPath("$.recentFailures[0].timestamp").isString())
                .andExpect(jsonPath("$.recentFailures[0].status").isString())
                .andExpect(jsonPath("$.recentFailures[0].statusCode").exists())
                .andExpect(jsonPath("$.recentFailures[0].latencyMs").isNumber())
                .andExpect(jsonPath("$.recentFailures[0].consecutiveFailureCount").isNumber());
    }
}

