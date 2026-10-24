package com.brightline.integrationhealth.casestudy.service;

import com.brightline.integrationhealth.casestudy.model.*;
import com.brightline.integrationhealth.casestudy.model.DashboardResponse.OverallSummary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Calculates health metrics and status for each integration based on log data.
 *
 * Health classification thresholds (configurable as project assumptions):
 *
 *   Error rate (errors + timeouts as a fraction of total requests):
 *     - HEALTHY:   ≤ 5%
 *     - DEGRADED:  > 5%  and ≤ 25%
 *     - UNHEALTHY: > 25%
 *
 *   Timeout rate (timeouts as a fraction of total requests):
 *     - Triggers DEGRADED if > 0% and ≤ 20%
 *     - Triggers UNHEALTHY if > 20%
 *
 *   Average latency of successful requests:
 *     - Triggers DEGRADED if > 1000ms
 *     - Triggers UNHEALTHY if > 3000ms
 *
 *   The worst status from any rule wins. For example, an integration with
 *   a low error rate but a high timeout rate is still marked UNHEALTHY.
 */
@Service
public class HealthService {

    // ----- Configurable thresholds (project assumptions) -----

    /** Error-rate boundary between HEALTHY and DEGRADED */
    static final double ERROR_RATE_DEGRADED_THRESHOLD = 0.05;
    /** Error-rate boundary between DEGRADED and UNHEALTHY */
    static final double ERROR_RATE_UNHEALTHY_THRESHOLD = 0.25;

    /** Timeout-rate boundary between HEALTHY and DEGRADED */
    static final double TIMEOUT_RATE_DEGRADED_THRESHOLD = 0.0;
    /** Timeout-rate boundary between DEGRADED and UNHEALTHY */
    static final double TIMEOUT_RATE_UNHEALTHY_THRESHOLD = 0.20;

    /** Average latency (ms) boundary between HEALTHY and DEGRADED */
    static final double LATENCY_DEGRADED_THRESHOLD = 1000.0;
    /** Average latency (ms) boundary between DEGRADED and UNHEALTHY */
    static final double LATENCY_UNHEALTHY_THRESHOLD = 3000.0;

    // Recognized status values
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_ERROR = "error";
    private static final String STATUS_TIMEOUT = "timeout";

    private final LogReader logReader;

    public HealthService(LogReader logReader) {
        this.logReader = logReader;
    }

    /**
     * Builds the full dashboard response: per-integration health + overall summary.
     */
    public DashboardResponse buildDashboard() {
        List<IntegrationLog> allLogs = logReader.loadLogs();
        List<IntegrationHealth> healths = calculateAllHealth(allLogs);
        OverallSummary summary = buildOverallSummary(healths);
        return new DashboardResponse(summary, healths);
    }

    /**
     * Groups logs by integration name and calculates health for each.
     */
    public List<IntegrationHealth> calculateAllHealth(List<IntegrationLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<IntegrationLog>> grouped = logs.stream()
                .filter(l -> l.getIntegration() != null)
                .collect(Collectors.groupingBy(IntegrationLog::getIntegration,
                        LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> calculateHealth(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Calculates health metrics for a single integration.
     */
    public IntegrationHealth calculateHealth(String integrationName, List<IntegrationLog> logs) {
        IntegrationHealth health = new IntegrationHealth();
        health.setIntegration(integrationName);

        if (logs == null || logs.isEmpty()) {
            health.setTotalRequests(0);
            health.setSuccessfulRequests(0);
            health.setFailedRequests(0);
            health.setTimeouts(0);
            health.setErrorRate(0.0);
            health.setTimeoutRate(0.0);
            health.setAverageLatencyMs(null);
            health.setStatus(HealthStatus.UNKNOWN);
            health.setReasons(List.of("No log records available for this integration."));
            return health;
        }

        long total = logs.size();
        long successes = 0;
        long errors = 0;
        long timeouts = 0;
        long unknownStatuses = 0;

        for (IntegrationLog logEntry : logs) {
            String status = logEntry.getStatus() != null ? logEntry.getStatus().toLowerCase() : "";
            switch (status) {
                case STATUS_SUCCESS -> successes++;
                case STATUS_ERROR -> errors++;
                case STATUS_TIMEOUT -> timeouts++;
                default -> unknownStatuses++;
            }
        }

        // Failures = errors + timeouts + unknown statuses (all non-success outcomes)
        long failures = errors + timeouts + unknownStatuses;

        double errorRate = total > 0 ? (double) failures / total : 0.0;
        double timeoutRate = total > 0 ? (double) timeouts / total : 0.0;

        // Average latency for successful requests only
        Double avgLatency = calculateAverageSuccessLatency(logs);

        health.setTotalRequests(total);
        health.setSuccessfulRequests(successes);
        health.setFailedRequests(failures);
        health.setTimeouts(timeouts);
        health.setErrorRate(roundToFourDecimals(errorRate));
        health.setTimeoutRate(roundToFourDecimals(timeoutRate));
        health.setAverageLatencyMs(avgLatency != null ? roundToTwoDecimals(avgLatency) : null);

        // Classify health status and collect reasons
        classifyHealth(health);

        return health;
    }

    /**
     * Calculates the average latency of successful requests.
     * Returns null if there are no successful requests with latency data.
     */
    Double calculateAverageSuccessLatency(List<IntegrationLog> logs) {
        List<Long> latencies = logs.stream()
                .filter(l -> STATUS_SUCCESS.equalsIgnoreCase(l.getStatus()))
                .filter(l -> l.getLatencyMs() != null)
                .map(IntegrationLog::getLatencyMs)
                .toList();

        if (latencies.isEmpty()) {
            return null;
        }

        return latencies.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
    }

    /**
     * Applies health rules and determines the worst-case status.
     * Populates human-readable reasons for any non-HEALTHY conditions.
     */
    void classifyHealth(IntegrationHealth health) {
        List<String> reasons = new ArrayList<>();
        HealthStatus worstStatus = HealthStatus.HEALTHY;

        // Rule 1: Error rate
        if (health.getErrorRate() > ERROR_RATE_UNHEALTHY_THRESHOLD) {
            worstStatus = worstOf(worstStatus, HealthStatus.UNHEALTHY);
            reasons.add(String.format("Error rate %.1f%% exceeds unhealthy threshold of %.0f%%.",
                    health.getErrorRate() * 100, ERROR_RATE_UNHEALTHY_THRESHOLD * 100));
        } else if (health.getErrorRate() > ERROR_RATE_DEGRADED_THRESHOLD) {
            worstStatus = worstOf(worstStatus, HealthStatus.DEGRADED);
            reasons.add(String.format("Error rate %.1f%% exceeds degraded threshold of %.0f%%.",
                    health.getErrorRate() * 100, ERROR_RATE_DEGRADED_THRESHOLD * 100));
        }

        // Rule 2: Timeout rate (separate from general error rate)
        if (health.getTimeoutRate() > TIMEOUT_RATE_UNHEALTHY_THRESHOLD) {
            worstStatus = worstOf(worstStatus, HealthStatus.UNHEALTHY);
            reasons.add(String.format("Timeout rate %.1f%% exceeds unhealthy threshold of %.0f%%.",
                    health.getTimeoutRate() * 100, TIMEOUT_RATE_UNHEALTHY_THRESHOLD * 100));
        } else if (health.getTimeoutRate() > TIMEOUT_RATE_DEGRADED_THRESHOLD) {
            worstStatus = worstOf(worstStatus, HealthStatus.DEGRADED);
            reasons.add(String.format("Timeout rate %.1f%% indicates intermittent connectivity issues.",
                    health.getTimeoutRate() * 100));
        }

        // Rule 3: Latency
        if (health.getAverageLatencyMs() != null) {
            if (health.getAverageLatencyMs() > LATENCY_UNHEALTHY_THRESHOLD) {
                worstStatus = worstOf(worstStatus, HealthStatus.UNHEALTHY);
                reasons.add(String.format("Average latency %.0fms exceeds unhealthy threshold of %.0fms.",
                        health.getAverageLatencyMs(), LATENCY_UNHEALTHY_THRESHOLD));
            } else if (health.getAverageLatencyMs() > LATENCY_DEGRADED_THRESHOLD) {
                worstStatus = worstOf(worstStatus, HealthStatus.DEGRADED);
                reasons.add(String.format("Average latency %.0fms exceeds degraded threshold of %.0fms.",
                        health.getAverageLatencyMs(), LATENCY_DEGRADED_THRESHOLD));
            }
        }

        if (reasons.isEmpty()) {
            reasons.add("All metrics are within healthy thresholds.");
        }

        health.setStatus(worstStatus);
        health.setReasons(reasons);
    }

    /**
     * Builds the overall summary by aggregating per-integration health data.
     */
    public OverallSummary buildOverallSummary(List<IntegrationHealth> healths) {
        OverallSummary summary = new OverallSummary();

        summary.setTotalIntegrations(healths.size());
        summary.setHealthyCount(healths.stream().filter(h -> h.getStatus() == HealthStatus.HEALTHY).count());
        summary.setDegradedCount(healths.stream().filter(h -> h.getStatus() == HealthStatus.DEGRADED).count());
        summary.setUnhealthyCount(healths.stream().filter(h -> h.getStatus() == HealthStatus.UNHEALTHY).count());
        summary.setUnknownCount(healths.stream().filter(h -> h.getStatus() == HealthStatus.UNKNOWN).count());

        long totalReqs = healths.stream().mapToLong(IntegrationHealth::getTotalRequests).sum();
        long totalFail = healths.stream().mapToLong(IntegrationHealth::getFailedRequests).sum();
        long totalTimeout = healths.stream().mapToLong(IntegrationHealth::getTimeouts).sum();

        summary.setTotalRequests(totalReqs);
        summary.setTotalFailures(totalFail);
        summary.setTotalTimeouts(totalTimeout);
        summary.setOverallErrorRate(totalReqs > 0
                ? roundToFourDecimals((double) totalFail / totalReqs)
                : 0.0);

        // Overall status is the worst status across all integrations
        HealthStatus overall = HealthStatus.HEALTHY;
        for (IntegrationHealth h : healths) {
            overall = worstOf(overall, h.getStatus());
        }
        if (healths.isEmpty()) {
            overall = HealthStatus.UNKNOWN;
        }
        summary.setOverallStatus(overall);
        summary.setGeneratedAt(Instant.now().toString());

        return summary;
    }

    /**
     * Returns the worse of two health statuses.
     * Ordering: HEALTHY < DEGRADED < UNHEALTHY < UNKNOWN (for classification).
     */
    static HealthStatus worstOf(HealthStatus a, HealthStatus b) {
        return severity(a) >= severity(b) ? a : b;
    }

    private static int severity(HealthStatus s) {
        return switch (s) {
            case HEALTHY -> 0;
            case DEGRADED -> 1;
            case UNHEALTHY -> 2;
            case UNKNOWN -> 3;
        };
    }

    private static double roundToFourDecimals(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
