package com.brightline.integrationhealth.casestudy.model;

/**
 * Health classification for an integration.
 *
 * HEALTHY   — error rate ≤ 5% and average latency ≤ 1000ms and no timeouts
 * DEGRADED  — error rate > 5% and ≤ 25%, or average latency > 1000ms,
 *             or timeout rate > 0% and ≤ 20%
 * UNHEALTHY — error rate > 25%, or timeout rate > 20%
 * UNKNOWN   — no log records available for the integration
 */
public enum HealthStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    UNKNOWN
}
