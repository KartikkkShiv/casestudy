package com.brightline.integrationhealth.casestudy.model;

/**
 * Enhanced request history entry for operations console.
 */
public class RequestHistoryEntry {

    private String integration;
    private String timestamp;
    private String status;
    private Integer statusCode;
    private Long latencyMs;
    private int consecutiveFailureCount;

    public RequestHistoryEntry() {
    }

    public RequestHistoryEntry(String integration, String timestamp, String status,
                               Integer statusCode, Long latencyMs, int consecutiveFailureCount) {
        this.integration = integration;
        this.timestamp = timestamp;
        this.status = status;
        this.statusCode = statusCode;
        this.latencyMs = latencyMs;
        this.consecutiveFailureCount = consecutiveFailureCount;
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

    public int getConsecutiveFailureCount() {
        return consecutiveFailureCount;
    }

    public void setConsecutiveFailureCount(int consecutiveFailureCount) {
        this.consecutiveFailureCount = consecutiveFailureCount;
    }
}
