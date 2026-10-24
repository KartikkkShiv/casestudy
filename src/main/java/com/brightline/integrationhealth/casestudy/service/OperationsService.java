package com.brightline.integrationhealth.casestudy.service;

import com.brightline.integrationhealth.casestudy.model.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced operations service for the Integration Operations Console.
 * Calculates advanced metrics including consecutive failures, request history,
 * failure history, and investigation recommendations.
 */
@Service
public class OperationsService {

    private final LogReader logReader;
    private final HealthService healthService;

    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_ERROR = "error";
    private static final String STATUS_TIMEOUT = "timeout";

    public OperationsService(LogReader logReader, HealthService healthService) {
        this.logReader = logReader;
        this.healthService = healthService;
    }

    /**
     * Builds the enhanced operations console response.
     */
    public OperationsConsoleResponse buildOperationsConsole() {
        List<IntegrationLog> allLogs = logReader.loadLogs();
        List<EnhancedIntegrationHealth> enhancedHealths = calculateEnhancedHealth(allLogs);
        OperationsConsoleResponse.OperationsSummary summary = buildOperationsSummary(enhancedHealths, allLogs);
        List<RequestHistoryEntry> recentFailures = extractRecentFailures(allLogs);

        OperationsConsoleResponse response = new OperationsConsoleResponse();
        response.setSummary(summary);
        response.setIntegrations(enhancedHealths);
        response.setRecentFailures(recentFailures);
        response.setRefreshedAt(System.currentTimeMillis());

        return response;
    }

    /**
     * Calculates enhanced health for all integrations.
     */
    public List<EnhancedIntegrationHealth> calculateEnhancedHealth(List<IntegrationLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }

        // Group logs by integration, maintaining insertion order
        Map<String, List<IntegrationLog>> grouped = logs.stream()
                .filter(l -> l.getIntegration() != null)
                .collect(Collectors.groupingBy(IntegrationLog::getIntegration,
                        LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> calculateEnhancedHealthForIntegration(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Calculates enhanced health metrics for a single integration.
     */
    public EnhancedIntegrationHealth calculateEnhancedHealthForIntegration(String integrationName, List<IntegrationLog> logs) {
        EnhancedIntegrationHealth health = new EnhancedIntegrationHealth();

        // Start with base health calculation
        IntegrationHealth baseHealth = healthService.calculateHealth(integrationName, logs);
        health.setIntegration(baseHealth.getIntegration());
        health.setTotalRequests(baseHealth.getTotalRequests());
        health.setSuccessfulRequests(baseHealth.getSuccessfulRequests());
        health.setFailedRequests(baseHealth.getFailedRequests());
        health.setTimeouts(baseHealth.getTimeouts());
        health.setErrorRate(baseHealth.getErrorRate());
        health.setTimeoutRate(baseHealth.getTimeoutRate());
        health.setAverageLatencyMs(baseHealth.getAverageLatencyMs());
        health.setStatus(baseHealth.getStatus());
        health.setReasons(baseHealth.getReasons());

        if (logs == null || logs.isEmpty()) {
            health.setSuccessCount(0L);
            health.setErrorCount(0L);
            health.setTimeoutCount(0L);
            health.setSuccessRate(0.0);
            health.setUnsuccessfulRate(0.0);
            health.setMaxLatencyMs(0L);
            health.setLatestRequestTimestamp(null);
            health.setLatestSuccessfulTimestamp(null);
            health.setLatestFailureTimestamp(null);
            health.setLatestStatusCode(null);
            health.setConsecutiveFailures(0);
            health.setRecommendations(Collections.emptyList());
            return health;
        }

        // Count successes, errors, and timeouts
        long successCount = 0;
        long errorCount = 0;
        long timeoutCount = 0;

        for (IntegrationLog log : logs) {
            String status = log.getStatus() != null ? log.getStatus().toLowerCase() : "";
            switch (status) {
                case STATUS_SUCCESS -> successCount++;
                case STATUS_ERROR -> errorCount++;
                case STATUS_TIMEOUT -> timeoutCount++;
            }
        }

        long total = logs.size();

        health.setSuccessCount(successCount);
        health.setErrorCount(errorCount);
        health.setTimeoutCount(timeoutCount);
        health.setSuccessRate(total > 0 ? roundToTwoDecimals((double) successCount / total * 100) : 0.0);
        health.setUnsuccessfulRate(total > 0 ? roundToTwoDecimals((double) (total - successCount) / total * 100) : 0.0);

        // Find max latency
        Long maxLatency = logs.stream()
                .filter(l -> l.getLatencyMs() != null)
                .map(IntegrationLog::getLatencyMs)
                .max(Long::compareTo)
                .orElse(0L);
        health.setMaxLatencyMs(maxLatency);

        // Find latest timestamps
        String latestTimestamp = null;
        String latestSuccessTimestamp = null;
        String latestFailureTimestamp = null;
        Integer latestStatusCode = null;

        // Logs should be in chronological order, so last one is latest
        if (!logs.isEmpty()) {
            IntegrationLog last = logs.get(logs.size() - 1);
            latestTimestamp = last.getTimestamp();
            if (last.getStatusCode() != null) {
                latestStatusCode = last.getStatusCode();
            }
        }

        // Find latest success and failure (reverse iteration for efficiency)
        for (int i = logs.size() - 1; i >= 0; i--) {
            IntegrationLog log = logs.get(i);
            String status = log.getStatus() != null ? log.getStatus().toLowerCase() : "";

            if (latestSuccessTimestamp == null && STATUS_SUCCESS.equals(status)) {
                latestSuccessTimestamp = log.getTimestamp();
            }
            if (latestFailureTimestamp == null && !STATUS_SUCCESS.equals(status)) {
                latestFailureTimestamp = log.getTimestamp();
            }

            if (latestSuccessTimestamp != null && latestFailureTimestamp != null) {
                break;
            }
        }

        health.setLatestRequestTimestamp(latestTimestamp);
        health.setLatestSuccessfulTimestamp(latestSuccessTimestamp);
        health.setLatestFailureTimestamp(latestFailureTimestamp);
        health.setLatestStatusCode(latestStatusCode);

        // Calculate consecutive failures (from end backwards)
        int consecutiveFailures = 0;
        for (int i = logs.size() - 1; i >= 0; i--) {
            IntegrationLog log = logs.get(i);
            String status = log.getStatus() != null ? log.getStatus().toLowerCase() : "";
            if (STATUS_SUCCESS.equals(status)) {
                break;  // Streak ends at first success
            }
            consecutiveFailures++;
        }
        health.setConsecutiveFailures(consecutiveFailures);

        // Generate investigation recommendations
        List<InvestigationRecommendation> recommendations = generateRecommendations(integrationName, logs);
        health.setRecommendations(recommendations);

        return health;
    }

    /**
     * Generates investigation recommendations based on anomalies in the logs.
     */
    public List<InvestigationRecommendation> generateRecommendations(String integrationName, List<IntegrationLog> logs) {
        List<InvestigationRecommendation> recommendations = new ArrayList<>();

        if (logs == null || logs.isEmpty()) {
            return recommendations;
        }

        // Detect patterns: repeated 500 errors, 429 rate limiting, 503 service unavailable, repeated timeouts

        // Count by HTTP status code and find last occurrence
        Map<Integer, List<IntegrationLog>> byStatusCode = new HashMap<>();
        int timeoutCount = 0;
        String lastTimeoutTimestamp = null;

        for (IntegrationLog log : logs) {
            String status = log.getStatus() != null ? log.getStatus().toLowerCase() : "";
            if (STATUS_TIMEOUT.equals(status)) {
                timeoutCount++;
                lastTimeoutTimestamp = log.getTimestamp();
            } else if (log.getStatusCode() != null) {
                byStatusCode.computeIfAbsent(log.getStatusCode(), k -> new ArrayList<>()).add(log);
            }
        }

        // Rule 1: Repeated HTTP 500 errors (more than 2)
        List<IntegrationLog> errors500 = byStatusCode.getOrDefault(500, Collections.emptyList());
        if (errors500.size() > 2) {
            String lastTime = errors500.get(errors500.size() - 1).getTimestamp();
            recommendations.add(new InvestigationRecommendation(
                    "REPEATED_500_ERRORS",
                    "Repeated Server Errors (HTTP 500)",
                    "The integration returned " + errors500.size() + " HTTP 500 (Internal Server Error) responses. This suggests a server-side issue with the third-party service.",
                    errors500.size(),
                    lastTime
            ));
        }

        // Rule 2: HTTP 429 rate limiting (any occurrence)
        List<IntegrationLog> errors429 = byStatusCode.getOrDefault(429, Collections.emptyList());
        if (!errors429.isEmpty()) {
            String lastTime = errors429.get(errors429.size() - 1).getTimestamp();
            recommendations.add(new InvestigationRecommendation(
                    "RATE_LIMITING",
                    "Rate Limiting Detected (HTTP 429)",
                    "The integration received " + errors429.size() + " HTTP 429 (Too Many Requests) responses. This may indicate request throttling or quota exhaustion with the third-party service.",
                    errors429.size(),
                    lastTime
            ));
        }

        // Rule 3: HTTP 503 service unavailable (any occurrence)
        List<IntegrationLog> errors503 = byStatusCode.getOrDefault(503, Collections.emptyList());
        if (!errors503.isEmpty()) {
            String lastTime = errors503.get(errors503.size() - 1).getTimestamp();
            recommendations.add(new InvestigationRecommendation(
                    "SERVICE_UNAVAILABLE",
                    "Service Unavailable (HTTP 503)",
                    "The integration received " + errors503.size() + " HTTP 503 (Service Unavailable) responses. The third-party service may be experiencing outages or maintenance.",
                    errors503.size(),
                    lastTime
            ));
        }

        // Rule 4: Repeated timeouts (more than 2)
        if (timeoutCount > 2) {
            recommendations.add(new InvestigationRecommendation(
                    "REPEATED_TIMEOUTS",
                    "Repeated Timeouts",
                    "The integration experienced " + timeoutCount + " timeout failures. This suggests network latency or unresponsiveness from the third-party service.",
                    timeoutCount,
                    lastTimeoutTimestamp
            ));
        }

        return recommendations;
    }

    /**
     * Extracts recent failures from the log (sorted newest first).
     */
    public List<RequestHistoryEntry> extractRecentFailures(List<IntegrationLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }

        // Build failure entries with consecutive failure count tracking
        List<RequestHistoryEntry> allFailures = new ArrayList<>();
        Map<String, Integer> consecutiveCountByIntegration = new LinkedHashMap<>();

        // Process logs in order to track consecutive failures per integration
        for (IntegrationLog log : logs) {
            String status = log.getStatus() != null ? log.getStatus().toLowerCase() : "";
            String integration = log.getIntegration() != null ? log.getIntegration() : "Unknown";

            if (STATUS_SUCCESS.equals(status)) {
                // Reset counter on success
                consecutiveCountByIntegration.put(integration, 0);
            } else {
                // Increment counter on failure
                int count = consecutiveCountByIntegration.getOrDefault(integration, 0) + 1;
                consecutiveCountByIntegration.put(integration, count);

                allFailures.add(new RequestHistoryEntry(
                        integration,
                        log.getTimestamp(),
                        status,
                        log.getStatusCode(),
                        log.getLatencyMs(),
                        count
                ));
            }
        }

        // Return sorted by timestamp (newest first)
        return allFailures.stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .collect(Collectors.toList());
    }

    /**
     * Builds operations summary with enhanced metrics.
     */
    OperationsConsoleResponse.OperationsSummary buildOperationsSummary(
            List<EnhancedIntegrationHealth> enhancedHealths,
            List<IntegrationLog> logs) {

        OperationsConsoleResponse.OperationsSummary summary = new OperationsConsoleResponse.OperationsSummary();

        // Inherit from existing OverallSummary logic
        List<IntegrationHealth> baseHealths = new ArrayList<>(enhancedHealths);
        DashboardResponse.OverallSummary baseS = healthService.buildOverallSummary(baseHealths);

        summary.setTotalIntegrations(baseS.getTotalIntegrations());
        summary.setHealthyCount(baseS.getHealthyCount());
        summary.setDegradedCount(baseS.getDegradedCount());
        summary.setUnhealthyCount(baseS.getUnhealthyCount());
        summary.setUnknownCount(baseS.getUnknownCount());
        summary.setTotalRequests(baseS.getTotalRequests());
        summary.setTotalFailures(baseS.getTotalFailures());
        summary.setTotalTimeouts(baseS.getTotalTimeouts());
        summary.setOverallErrorRate(baseS.getOverallErrorRate());
        summary.setOverallStatus(baseS.getOverallStatus());
        summary.setGeneratedAt(baseS.getGeneratedAt());

        // Add enhanced metrics
        long totalSuccesses = enhancedHealths.stream()
                .mapToLong(h -> h.getSuccessCount() != null ? h.getSuccessCount() : 0)
                .sum();
        long totalErrors = enhancedHealths.stream()
                .mapToLong(h -> h.getErrorCount() != null ? h.getErrorCount() : 0)
                .sum();

        summary.setTotalSuccesses(totalSuccesses);
        summary.setTotalErrors(totalErrors);

        return summary;
    }

    private static double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
