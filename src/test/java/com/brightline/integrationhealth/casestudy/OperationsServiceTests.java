package com.brightline.integrationhealth.casestudy;

import com.brightline.integrationhealth.casestudy.model.*;
import com.brightline.integrationhealth.casestudy.service.OperationsService;
import com.brightline.integrationhealth.casestudy.service.LogReader;
import com.brightline.integrationhealth.casestudy.service.HealthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the enhanced operations console service.
 */
class OperationsServiceTests {

    private OperationsService operationsService;
    private LogReader logReader;
    private HealthService healthService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        logReader = new LogReader(objectMapper);
        healthService = new HealthService(logReader);
        operationsService = new OperationsService(logReader, healthService);
    }

    @Nested
    @DisplayName("Enhanced Health Calculations")
    class EnhancedHealthTests {

        @Test
        @DisplayName("Should calculate success rate and unsuccessful rate")
        void shouldCalculateRates() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "success", 200, 110L),
                    log("TestAPI", "error", 500, 300L),
                    log("TestAPI", "success", 200, 90L)
            );

            EnhancedIntegrationHealth health = operationsService.calculateEnhancedHealthForIntegration("TestAPI", logs);

            assertEquals(3, health.getSuccessCount());
            assertEquals(1, health.getErrorCount());
            assertEquals(0, health.getTimeoutCount());
            assertEquals(75.0, health.getSuccessRate(), 0.01);
            assertEquals(25.0, health.getUnsuccessfulRate(), 0.01);
        }

        @Test
        @DisplayName("Should find max latency correctly")
        void shouldFindMaxLatency() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "success", 200, 500L),
                    log("TestAPI", "error", 500, 300L),
                    log("TestAPI", "success", 200, 150L)
            );

            EnhancedIntegrationHealth health = operationsService.calculateEnhancedHealthForIntegration("TestAPI", logs);

            assertEquals(500L, health.getMaxLatencyMs());
        }

        @Test
        @DisplayName("Should track latest timestamps correctly")
        void shouldTrackLatestTimestamps() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "2026-01-01T10:00:00", "success", 200, 100L),
                    log("TestAPI", "2026-01-01T10:05:00", "error", 500, 300L),
                    log("TestAPI", "2026-01-01T10:10:00", "success", 200, 120L)
            );

            EnhancedIntegrationHealth health = operationsService.calculateEnhancedHealthForIntegration("TestAPI", logs);

            assertEquals("2026-01-01T10:10:00", health.getLatestRequestTimestamp());
            assertEquals("2026-01-01T10:10:00", health.getLatestSuccessfulTimestamp());
            assertEquals("2026-01-01T10:05:00", health.getLatestFailureTimestamp());
        }

        @Test
        @DisplayName("Should calculate consecutive failures correctly")
        void shouldCalculateConsecutiveFailures() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "error", 500, 300L),
                    log("TestAPI", "error", 500, 300L),
                    log("TestAPI", "timeout", null, 30000L),
                    log("TestAPI", "success", 200, 120L)
            );

            EnhancedIntegrationHealth health = operationsService.calculateEnhancedHealthForIntegration("TestAPI", logs);

            assertEquals(0, health.getConsecutiveFailures(), "No consecutive failures at end");
        }

        @Test
        @DisplayName("Should calculate consecutive failures at end of sequence")
        void shouldCalculateConsecutiveFailuresAtEnd() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "success", 200, 110L),
                    log("TestAPI", "error", 500, 300L),
                    log("TestAPI", "timeout", null, 30000L),
                    log("TestAPI", "error", 500, 320L)
            );

            EnhancedIntegrationHealth health = operationsService.calculateEnhancedHealthForIntegration("TestAPI", logs);

            assertEquals(3, health.getConsecutiveFailures(), "Three consecutive failures at end");
        }
    }

    @Nested
    @DisplayName("Investigation Recommendations")
    class RecommendationsTests {

        @Test
        @DisplayName("Should detect repeated HTTP 500 errors")
        void shouldDetectRepeated500s() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "error", 500, 300L),
                    log("TestAPI", "error", 500, 310L),
                    log("TestAPI", "error", 500, 320L)
            );

            List<InvestigationRecommendation> recommendations = operationsService.generateRecommendations("TestAPI", logs);

            assertTrue(recommendations.stream().anyMatch(r -> r.getType().equals("REPEATED_500_ERRORS")));
        }

        @Test
        @DisplayName("Should detect HTTP 429 rate limiting")
        void shouldDetectRateLimiting() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "error", 429, 500L),
                    log("TestAPI", "success", 200, 110L)
            );

            List<InvestigationRecommendation> recommendations = operationsService.generateRecommendations("TestAPI", logs);

            assertTrue(recommendations.stream().anyMatch(r -> r.getType().equals("RATE_LIMITING")));
        }

        @Test
        @DisplayName("Should detect HTTP 503 service unavailable")
        void shouldDetectServiceUnavailable() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "error", 503, 5000L),
                    log("TestAPI", "error", 503, 5100L)
            );

            List<InvestigationRecommendation> recommendations = operationsService.generateRecommendations("TestAPI", logs);

            assertTrue(recommendations.stream().anyMatch(r -> r.getType().equals("SERVICE_UNAVAILABLE")));
        }

        @Test
        @DisplayName("Should detect repeated timeouts")
        void shouldDetectRepeatedTimeouts() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "timeout", null, 30000L),
                    log("TestAPI", "timeout", null, 30000L),
                    log("TestAPI", "timeout", null, 30000L)
            );

            List<InvestigationRecommendation> recommendations = operationsService.generateRecommendations("TestAPI", logs);

            assertTrue(recommendations.stream().anyMatch(r -> r.getType().equals("REPEATED_TIMEOUTS")));
        }

        @Test
        @DisplayName("Should not flag recommendations for single errors")
        void shouldNotFlagSingleErrors() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "error", 500, 300L),
                    log("TestAPI", "success", 200, 110L)
            );

            List<InvestigationRecommendation> recommendations = operationsService.generateRecommendations("TestAPI", logs);

            assertTrue(recommendations.isEmpty(), "Single error should not trigger recommendations");
        }
    }

    @Nested
    @DisplayName("Recent Failures Extraction")
    class FailuresExtractionTests {

        @Test
        @DisplayName("Should extract all failures sorted by timestamp newest first")
        void shouldExtractAndSortFailures() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "2026-01-01T10:00:00", "success", 200, 100L),
                    log("TestAPI", "2026-01-01T10:05:00", "error", 500, 300L),
                    log("TestAPI", "2026-01-01T10:10:00", "timeout", null, 30000L),
                    log("TestAPI", "2026-01-01T10:15:00", "success", 200, 120L)
            );

            List<RequestHistoryEntry> failures = operationsService.extractRecentFailures(logs);

            assertEquals(2, failures.size());
            assertEquals("2026-01-01T10:10:00", failures.get(0).getTimestamp());
            assertEquals("2026-01-01T10:05:00", failures.get(1).getTimestamp());
        }

        @Test
        @DisplayName("Should track consecutive failure count correctly")
        void shouldTrackConsecutiveCount() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "error", 500, 300L),
                    log("TestAPI", "error", 500, 310L),
                    log("TestAPI", "success", 200, 120L),
                    log("TestAPI", "error", 500, 330L)
            );

            List<RequestHistoryEntry> failures = operationsService.extractRecentFailures(logs);

            assertEquals(3, failures.size());
            // When sorted newest first, the order is: failures[0] = last error (5th), failures[1] = 2nd error, failures[2] = 1st error
            // The last error has consecutive count = 1 (first failure after success)
            assertEquals(1, failures.get(0).getConsecutiveFailureCount());  // Last error (single failure after success)
            assertEquals(2, failures.get(1).getConsecutiveFailureCount());  // Second error (continuation of streak)
            assertEquals(1, failures.get(2).getConsecutiveFailureCount());  // First error after earlier success
        }

        @Test
        @DisplayName("Should handle empty failure list")
        void shouldHandleEmptyFailures() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "success", 200, 110L)
            );

            List<RequestHistoryEntry> failures = operationsService.extractRecentFailures(logs);

            assertTrue(failures.isEmpty());
        }
    }

    @Nested
    @DisplayName("Operations Console Response")
    class OperationsConsoleTests {

        @Test
        @DisplayName("Should build complete operations console response from real dataset")
        void shouldBuildCompleteResponse() {
            OperationsConsoleResponse response = operationsService.buildOperationsConsole();

            assertNotNull(response.getSummary());
            assertNotNull(response.getIntegrations());
            assertNotNull(response.getRecentFailures());
            assertTrue(response.getRefreshedAt() > 0);

            assertEquals(5, response.getIntegrations().size());
            assertEquals(60, response.getSummary().getTotalRequests());
        }

        @Test
        @DisplayName("Should handle empty dataset")
        void shouldHandleEmptyDataset() {
            List<EnhancedIntegrationHealth> healths = operationsService.calculateEnhancedHealth(Collections.emptyList());
            assertTrue(healths.isEmpty());
        }

        @Test
        @DisplayName("Should calculate correct totals")
        void shouldCalculateCorrectTotals() {
            OperationsConsoleResponse response = operationsService.buildOperationsConsole();

            long expectedSuccesses = 43;
            long expectedErrors = 12;
            long expectedTimeouts = 5;

            assertEquals(expectedSuccesses, response.getSummary().getTotalSuccesses());
            assertEquals(expectedErrors, response.getSummary().getTotalErrors());
            assertEquals(expectedTimeouts, response.getSummary().getTotalTimeouts());
        }
    }

    // Helper method
    private static IntegrationLog log(String integration, String status, Integer statusCode, Long latencyMs) {
        return log(integration, "2026-01-01T00:00:00", status, statusCode, latencyMs);
    }

    private static IntegrationLog log(String integration, String timestamp, String status,
                                      Integer statusCode, Long latencyMs) {
        return new IntegrationLog(integration, timestamp, status, statusCode, latencyMs);
    }
}
