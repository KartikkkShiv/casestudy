package com.brightline.integrationhealth.casestudy;

import com.brightline.integrationhealth.casestudy.model.*;
import com.brightline.integrationhealth.casestudy.service.HealthService;
import com.brightline.integrationhealth.casestudy.service.LogReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the integration health dashboard.
 * Tests cover JSON parsing, metric calculations, health classification,
 * edge cases (empty data, missing latency, division by zero), and boundary conditions.
 */
class HealthServiceTests {

    private HealthService healthService;
    private LogReader logReader;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        logReader = new LogReader(objectMapper);
        healthService = new HealthService(logReader);
    }

    // ----- JSON Parsing Tests -----

    @Nested
    @DisplayName("JSON Parsing")
    class JsonParsingTests {

        @Test
        @DisplayName("Should load logs from classpath resource")
        void shouldLoadLogsFromClasspath() {
            List<IntegrationLog> logs = logReader.loadLogs();
            assertNotNull(logs);
            assertEquals(60, logs.size(), "Dataset should contain 60 log entries");
        }

        @Test
        @DisplayName("Should parse all fields correctly")
        void shouldParseFieldsCorrectly() {
            List<IntegrationLog> logs = logReader.loadLogs();
            IntegrationLog first = logs.get(0);

            assertEquals("Slack Notifications", first.getIntegration());
            assertEquals("2026-08-04T09:00:00", first.getTimestamp());
            assertEquals("success", first.getStatus());
            assertEquals(200, first.getStatusCode());
            assertEquals(130L, first.getLatencyMs());
        }

        @Test
        @DisplayName("Should parse timeout entries with null status_code")
        void shouldParseTimeoutEntries() {
            List<IntegrationLog> logs = logReader.loadLogs();

            // Greenhouse ATS Sync second entry is a timeout
            IntegrationLog timeout = logs.stream()
                    .filter(l -> "Greenhouse ATS Sync".equals(l.getIntegration())
                            && "timeout".equals(l.getStatus()))
                    .findFirst()
                    .orElseThrow();

            assertNull(timeout.getStatusCode(), "Timeout entries should have null status_code");
            assertEquals(30000L, timeout.getLatencyMs());
        }

        @Test
        @DisplayName("Should contain all five integrations")
        void shouldContainAllIntegrations() {
            List<IntegrationLog> logs = logReader.loadLogs();
            long distinctIntegrations = logs.stream()
                    .map(IntegrationLog::getIntegration)
                    .distinct()
                    .count();
            assertEquals(5, distinctIntegrations, "Should have 5 distinct integrations");
        }
    }

    // ----- Metric Calculation Tests -----

    @Nested
    @DisplayName("Metric Calculations")
    class MetricCalculationTests {

        @Test
        @DisplayName("Should count successful and failed requests correctly")
        void shouldCountSuccessAndFailures() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "success", 200, 110L),
                    log("TestAPI", "error", 500, 300L),
                    log("TestAPI", "success", 200, 90L)
            );

            IntegrationHealth health = healthService.calculateHealth("TestAPI", logs);

            assertEquals(4, health.getTotalRequests());
            assertEquals(3, health.getSuccessfulRequests());
            assertEquals(1, health.getFailedRequests());
            assertEquals(0, health.getTimeouts());
        }

        @Test
        @DisplayName("Should count timeouts separately from errors")
        void shouldCountTimeoutsSeparately() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "error", 500, 300L),
                    log("TestAPI", "timeout", null, 30000L),
                    log("TestAPI", "timeout", null, 30000L)
            );

            IntegrationHealth health = healthService.calculateHealth("TestAPI", logs);

            assertEquals(4, health.getTotalRequests());
            assertEquals(1, health.getSuccessfulRequests());
            assertEquals(3, health.getFailedRequests()); // 1 error + 2 timeouts
            assertEquals(2, health.getTimeouts());
        }

        @Test
        @DisplayName("Should calculate error rate correctly")
        void shouldCalculateErrorRate() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "success", 200, 110L),
                    log("TestAPI", "error", 500, 300L),
                    log("TestAPI", "success", 200, 90L)
            );

            IntegrationHealth health = healthService.calculateHealth("TestAPI", logs);

            assertEquals(0.25, health.getErrorRate(), 0.001);
        }

        @Test
        @DisplayName("Should calculate average latency for successful requests only")
        void shouldCalculateAverageLatencyForSuccessOnly() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "success", 200, 200L),
                    log("TestAPI", "error", 500, 5000L), // error latency excluded
                    log("TestAPI", "success", 200, 300L)
            );

            IntegrationHealth health = healthService.calculateHealth("TestAPI", logs);

            // Average of 100, 200, 300 = 200
            assertEquals(200.0, health.getAverageLatencyMs(), 0.01);
        }

        @Test
        @DisplayName("Should handle unknown status values without discarding them")
        void shouldHandleUnknownStatusValues() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "unknown_status", 999, 500L)
            );

            IntegrationHealth health = healthService.calculateHealth("TestAPI", logs);

            assertEquals(2, health.getTotalRequests());
            assertEquals(1, health.getSuccessfulRequests());
            assertEquals(1, health.getFailedRequests()); // unknown treated as non-success
        }
    }

    // ----- Health Classification Tests -----

    @Nested
    @DisplayName("Health Classification")
    class HealthClassificationTests {

        @Test
        @DisplayName("Should classify as HEALTHY when all metrics are within thresholds")
        void shouldClassifyHealthy() {
            // 100% success rate, low latency
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "success", 200, 110L),
                    log("TestAPI", "success", 200, 120L)
            );

            IntegrationHealth health = healthService.calculateHealth("TestAPI", logs);

            assertEquals(HealthStatus.HEALTHY, health.getStatus());
            assertTrue(health.getReasons().stream()
                    .anyMatch(r -> r.contains("within healthy thresholds")));
        }

        @Test
        @DisplayName("Should classify as DEGRADED when error rate exceeds 5%")
        void shouldClassifyDegradedOnErrorRate() {
            // 1 failure out of 10 = 10% error rate
            List<IntegrationLog> successLogs = new java.util.ArrayList<>();
            for (int i = 0; i < 9; i++) {
                successLogs.add(log("TestAPI", "success", 200, 100L));
            }
            successLogs.add(log("TestAPI", "error", 500, 300L));

            IntegrationHealth health = healthService.calculateHealth("TestAPI", successLogs);

            assertEquals(HealthStatus.DEGRADED, health.getStatus());
        }

        @Test
        @DisplayName("Should classify as UNHEALTHY when error rate exceeds 25%")
        void shouldClassifyUnhealthyOnHighErrorRate() {
            // 3 failures out of 4 = 75% error rate
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "error", 500, 300L),
                    log("TestAPI", "error", 500, 300L),
                    log("TestAPI", "error", 500, 300L)
            );

            IntegrationHealth health = healthService.calculateHealth("TestAPI", logs);

            assertEquals(HealthStatus.UNHEALTHY, health.getStatus());
        }

        @Test
        @DisplayName("Should classify as UNHEALTHY when timeout rate exceeds 20%")
        void shouldClassifyUnhealthyOnHighTimeoutRate() {
            // 3 timeouts out of 4 = 75%
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "timeout", null, 30000L),
                    log("TestAPI", "timeout", null, 30000L),
                    log("TestAPI", "timeout", null, 30000L)
            );

            IntegrationHealth health = healthService.calculateHealth("TestAPI", logs);

            assertEquals(HealthStatus.UNHEALTHY, health.getStatus());
        }

        @Test
        @DisplayName("Should use worst-case status when multiple rules trigger")
        void shouldUseWorstCaseStatus() {
            // High error rate (UNHEALTHY) + timeouts (DEGRADED) -> UNHEALTHY wins
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 100L),
                    log("TestAPI", "error", 500, 300L),
                    log("TestAPI", "error", 500, 300L),
                    log("TestAPI", "timeout", null, 30000L)
            );

            IntegrationHealth health = healthService.calculateHealth("TestAPI", logs);

            assertEquals(HealthStatus.UNHEALTHY, health.getStatus());
            // Should have multiple reasons
            assertTrue(health.getReasons().size() >= 2);
        }

        @Test
        @DisplayName("Should classify as DEGRADED on high latency even with zero errors")
        void shouldClassifyDegradedOnHighLatency() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "success", 200, 1500L),
                    log("TestAPI", "success", 200, 1600L),
                    log("TestAPI", "success", 200, 1400L)
            );

            IntegrationHealth health = healthService.calculateHealth("TestAPI", logs);

            assertEquals(HealthStatus.DEGRADED, health.getStatus());
            assertTrue(health.getReasons().stream()
                    .anyMatch(r -> r.contains("latency")));
        }

        @Test
        @DisplayName("Boundary: exactly 5% error rate should be HEALTHY")
        void shouldBeHealthyAtExactly5Percent() {
            // 1 failure out of 20 = 5% exactly
            List<IntegrationLog> logs = new java.util.ArrayList<>();
            for (int i = 0; i < 19; i++) {
                logs.add(log("TestAPI", "success", 200, 100L));
            }
            logs.add(log("TestAPI", "error", 500, 300L));

            IntegrationHealth health = healthService.calculateHealth("TestAPI", logs);

            assertEquals(HealthStatus.HEALTHY, health.getStatus());
        }
    }

    // ----- Edge Case Tests -----

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty log list")
        void shouldHandleEmptyLogList() {
            IntegrationHealth health = healthService.calculateHealth("EmptyAPI", Collections.emptyList());

            assertEquals(HealthStatus.UNKNOWN, health.getStatus());
            assertEquals(0, health.getTotalRequests());
            assertNull(health.getAverageLatencyMs());
        }

        @Test
        @DisplayName("Should handle null log list")
        void shouldHandleNullLogList() {
            IntegrationHealth health = healthService.calculateHealth("NullAPI", null);

            assertEquals(HealthStatus.UNKNOWN, health.getStatus());
            assertEquals(0, health.getTotalRequests());
        }

        @Test
        @DisplayName("Should return empty list for empty dataset")
        void shouldReturnEmptyListForEmptyDataset() {
            List<IntegrationHealth> healths = healthService.calculateAllHealth(Collections.emptyList());
            assertTrue(healths.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list for null dataset")
        void shouldReturnEmptyListForNullDataset() {
            List<IntegrationHealth> healths = healthService.calculateAllHealth(null);
            assertTrue(healths.isEmpty());
        }

        @Test
        @DisplayName("Should handle missing latency values")
        void shouldHandleMissingLatency() {
            List<IntegrationLog> logs = List.of(
                    new IntegrationLog("TestAPI", "2026-01-01T00:00:00", "success", 200, null),
                    new IntegrationLog("TestAPI", "2026-01-01T00:05:00", "success", 200, null)
            );

            IntegrationHealth health = healthService.calculateHealth("TestAPI", logs);

            assertNull(health.getAverageLatencyMs(), "Average latency should be null when no latency data");
        }

        @Test
        @DisplayName("Should handle all-failure dataset")
        void shouldHandleAllFailures() {
            List<IntegrationLog> logs = List.of(
                    log("TestAPI", "error", 500, 300L),
                    log("TestAPI", "error", 500, 400L)
            );

            IntegrationHealth health = healthService.calculateHealth("TestAPI", logs);

            assertEquals(HealthStatus.UNHEALTHY, health.getStatus());
            assertEquals(1.0, health.getErrorRate(), 0.001);
            assertNull(health.getAverageLatencyMs(), "No successful requests means no avg latency");
        }
    }

    // ----- Overall Summary Tests -----

    @Nested
    @DisplayName("Overall Summary")
    class OverallSummaryTests {

        @Test
        @DisplayName("Should build correct overall summary from real dataset")
        void shouldBuildCorrectOverallSummary() {
            List<IntegrationLog> logs = logReader.loadLogs();
            List<IntegrationHealth> healths = healthService.calculateAllHealth(logs);
            DashboardResponse.OverallSummary summary = healthService.buildOverallSummary(healths);

            assertEquals(5, summary.getTotalIntegrations());
            assertEquals(60, summary.getTotalRequests());
            assertTrue(summary.getTotalFailures() > 0, "There should be failures in the dataset");
            assertTrue(summary.getTotalTimeouts() > 0, "There should be timeouts in the dataset");
            assertNotNull(summary.getOverallStatus());
            assertNotNull(summary.getGeneratedAt());
        }

        @Test
        @DisplayName("Should set UNKNOWN overall status for empty dataset")
        void shouldSetUnknownForEmptyDataset() {
            DashboardResponse.OverallSummary summary = healthService.buildOverallSummary(Collections.emptyList());
            assertEquals(HealthStatus.UNKNOWN, summary.getOverallStatus());
        }
    }

    // ----- Real Dataset Validation -----

    @Nested
    @DisplayName("Real Dataset Validation")
    class RealDatasetTests {

        @Test
        @DisplayName("Slack should be DEGRADED (1 error out of 12 ≈ 8.3% > 5%)")
        void slackShouldBeHealthy() {
            List<IntegrationLog> logs = logReader.loadLogs();
            List<IntegrationHealth> healths = healthService.calculateAllHealth(logs);

            IntegrationHealth slack = findByName(healths, "Slack Notifications");
            assertEquals(12, slack.getTotalRequests());
            assertEquals(11, slack.getSuccessfulRequests());
            assertEquals(1, slack.getFailedRequests());
            assertEquals(0, slack.getTimeouts());
            assertEquals(HealthStatus.DEGRADED, slack.getStatus());
        }

        @Test
        @DisplayName("Workday should be DEGRADED (1 error out of 12 ≈ 8.3% > 5%)")
        void workdayShouldBeHealthy() {
            List<IntegrationLog> logs = logReader.loadLogs();
            List<IntegrationHealth> healths = healthService.calculateAllHealth(logs);

            IntegrationHealth workday = findByName(healths, "Workday Directory Sync");
            assertEquals(12, workday.getTotalRequests());
            assertEquals(HealthStatus.DEGRADED, workday.getStatus());
        }

        @Test
        @DisplayName("ADP should be DEGRADED (5 errors out of 12 ≈ 41.7%)")
        void adpShouldBeUnhealthy() {
            List<IntegrationLog> logs = logReader.loadLogs();
            List<IntegrationHealth> healths = healthService.calculateAllHealth(logs);

            IntegrationHealth adp = findByName(healths, "ADP Payroll Sync");
            assertEquals(12, adp.getTotalRequests());
            assertEquals(5, adp.getFailedRequests());
            assertEquals(HealthStatus.UNHEALTHY, adp.getStatus());
        }

        @Test
        @DisplayName("DocuSign should be DEGRADED (4 errors out of 12 ≈ 33.3%)")
        void docuSignShouldBeUnhealthy() {
            List<IntegrationLog> logs = logReader.loadLogs();
            List<IntegrationHealth> healths = healthService.calculateAllHealth(logs);

            IntegrationHealth docusign = findByName(healths, "DocuSign Envelope API");
            assertEquals(12, docusign.getTotalRequests());
            assertEquals(4, docusign.getFailedRequests());
            assertEquals(HealthStatus.UNHEALTHY, docusign.getStatus());
        }

        @Test
        @DisplayName("Greenhouse should be UNHEALTHY (5 timeouts + 1 error out of 12 = 50%)")
        void greenhouseShouldBeUnhealthy() {
            List<IntegrationLog> logs = logReader.loadLogs();
            List<IntegrationHealth> healths = healthService.calculateAllHealth(logs);

            IntegrationHealth greenhouse = findByName(healths, "Greenhouse ATS Sync");
            assertEquals(12, greenhouse.getTotalRequests());
            assertEquals(5, greenhouse.getTimeouts());
            assertEquals(6, greenhouse.getFailedRequests()); // 5 timeouts + 1 error
            assertEquals(HealthStatus.UNHEALTHY, greenhouse.getStatus());
        }

        private IntegrationHealth findByName(List<IntegrationHealth> healths, String name) {
            return healths.stream()
                    .filter(h -> name.equals(h.getIntegration()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Integration not found: " + name));
        }
    }

    // ----- Helper -----

    private static IntegrationLog log(String integration, String status,
                                       Integer statusCode, Long latencyMs) {
        return new IntegrationLog(integration, "2026-01-01T00:00:00", status, statusCode, latencyMs);
    }
}
