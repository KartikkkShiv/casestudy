package com.brightline.integrationhealth.casestudy.model;

import java.util.List;

/**
 * Health summary for a single integration, returned as part of the API response.
 */
public class IntegrationHealth {

    private String integration;
    private long totalRequests;
    private long successfulRequests;
    private long failedRequests;
    private long timeouts;
    private double errorRate;
    private double timeoutRate;
    private Double averageLatencyMs;
    private HealthStatus status;
    private List<String> reasons;

    public IntegrationHealth() {
    }

    public String getIntegration() {
        return integration;
    }

    public void setIntegration(String integration) {
        this.integration = integration;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public long getSuccessfulRequests() {
        return successfulRequests;
    }

    public void setSuccessfulRequests(long successfulRequests) {
        this.successfulRequests = successfulRequests;
    }

    public long getFailedRequests() {
        return failedRequests;
    }

    public void setFailedRequests(long failedRequests) {
        this.failedRequests = failedRequests;
    }

    public long getTimeouts() {
        return timeouts;
    }

    public void setTimeouts(long timeouts) {
        this.timeouts = timeouts;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(double errorRate) {
        this.errorRate = errorRate;
    }

    public double getTimeoutRate() {
        return timeoutRate;
    }

    public void setTimeoutRate(double timeoutRate) {
        this.timeoutRate = timeoutRate;
    }

    public Double getAverageLatencyMs() {
        return averageLatencyMs;
    }

    public void setAverageLatencyMs(Double averageLatencyMs) {
        this.averageLatencyMs = averageLatencyMs;
    }

    public HealthStatus getStatus() {
        return status;
    }

    public void setStatus(HealthStatus status) {
        this.status = status;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }
}
