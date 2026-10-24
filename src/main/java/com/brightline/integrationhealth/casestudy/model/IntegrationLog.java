package com.brightline.integrationhealth.casestudy.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single request-log entry from the integration_logs.json dataset.
 *
 * Fields map directly to the JSON schema:
 *   integration  — the third-party service name
 *   timestamp    — ISO-8601 local datetime of the request
 *   status       — outcome: "success", "error", or "timeout"
 *   statusCode   — HTTP status code (null for timeouts)
 *   latencyMs    — request duration in milliseconds
 */
public class IntegrationLog {

    private String integration;
    private String timestamp;
    private String status;

    @JsonProperty("status_code")
    private Integer statusCode;

    @JsonProperty("latency_ms")
    private Long latencyMs;

    public IntegrationLog() {
    }

    public IntegrationLog(String integration, String timestamp, String status,
                          Integer statusCode, Long latencyMs) {
        this.integration = integration;
        this.timestamp = timestamp;
        this.status = status;
        this.statusCode = statusCode;
        this.latencyMs = latencyMs;
    }

    public String getIntegration() {
        return integration;
    }

    public void setIntegration(String integration) {
        this.integration = integration;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }
}
