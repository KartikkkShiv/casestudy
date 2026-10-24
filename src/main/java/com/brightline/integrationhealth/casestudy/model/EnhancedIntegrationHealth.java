package com.brightline.integrationhealth.casestudy.model;

import java.util.List;

/**
 * Enhanced per-integration health with detailed metrics and history.
 */
public class EnhancedIntegrationHealth extends IntegrationHealth {

    private Long successCount;                    // Number of successful requests
    private Long errorCount;                      // Number of explicit errors
    private Long timeoutCount;                    // Number of timeouts
    private Double successRate;                   // Percentage (0-100)
    private Double unsuccessfulRate;              // Percentage (0-100)
    private Long maxLatencyMs;                    // Maximum observed latency
    private String latestRequestTimestamp;        // ISO-8601
    private String latestSuccessfulTimestamp;     // ISO-8601 or null
    private String latestFailureTimestamp;        // ISO-8601 or null
    private Integer latestStatusCode;             // Latest HTTP status code
    private Integer consecutiveFailures;          // Current streak
    private List<InvestigationRecommendation> recommendations;  // Rule-based suggestions

    public Long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Long successCount) {
        this.successCount = successCount;
    }

    public Long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(Long errorCount) {
        this.errorCount = errorCount;
    }

    public Long getTimeoutCount() {
        return timeoutCount;
    }

    public void setTimeoutCount(Long timeoutCount) {
        this.timeoutCount = timeoutCount;
    }

    public Double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(Double successRate) {
        this.successRate = successRate;
    }

    public Double getUnsuccessfulRate() {
        return unsuccessfulRate;
    }

    public void setUnsuccessfulRate(Double unsuccessfulRate) {
        this.unsuccessfulRate = unsuccessfulRate;
    }

    public Long getMaxLatencyMs() {
        return maxLatencyMs;
    }

    public void setMaxLatencyMs(Long maxLatencyMs) {
        this.maxLatencyMs = maxLatencyMs;
    }

    public String getLatestRequestTimestamp() {
        return latestRequestTimestamp;
    }

    public void setLatestRequestTimestamp(String latestRequestTimestamp) {
        this.latestRequestTimestamp = latestRequestTimestamp;
    }

    public String getLatestSuccessfulTimestamp() {
        return latestSuccessfulTimestamp;
    }

    public void setLatestSuccessfulTimestamp(String latestSuccessfulTimestamp) {
        this.latestSuccessfulTimestamp = latestSuccessfulTimestamp;
    }

    public String getLatestFailureTimestamp() {
        return latestFailureTimestamp;
    }

    public void setLatestFailureTimestamp(String latestFailureTimestamp) {
        this.latestFailureTimestamp = latestFailureTimestamp;
    }

    public Integer getLatestStatusCode() {
        return latestStatusCode;
    }

    public void setLatestStatusCode(Integer latestStatusCode) {
        this.latestStatusCode = latestStatusCode;
    }

    public Integer getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(Integer consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public List<InvestigationRecommendation> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<InvestigationRecommendation> recommendations) {
        this.recommendations = recommendations;
    }
}
