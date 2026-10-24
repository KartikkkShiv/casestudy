package com.brightline.integrationhealth.casestudy.model;

import java.util.List;

/**
 * Top-level API response for GET /api/integrations/health.
 *
 * Contains an overall summary and per-integration health details.
 */
public class DashboardResponse {

    private OverallSummary summary;
    private List<IntegrationHealth> integrations;

    public DashboardResponse() {
    }

    public DashboardResponse(OverallSummary summary, List<IntegrationHealth> integrations) {
        this.summary = summary;
        this.integrations = integrations;
    }

    public OverallSummary getSummary() {
        return summary;
    }

    public void setSummary(OverallSummary summary) {
        this.summary = summary;
    }

    public List<IntegrationHealth> getIntegrations() {
        return integrations;
    }

    public void setIntegrations(List<IntegrationHealth> integrations) {
        this.integrations = integrations;
    }

    /**
     * Overall health summary across all integrations.
     */
    public static class OverallSummary {

        private long totalIntegrations;
        private long healthyCount;
        private long degradedCount;
        private long unhealthyCount;
        private long unknownCount;
        private long totalRequests;
        private long totalFailures;
        private long totalTimeouts;
        private double overallErrorRate;
        private HealthStatus overallStatus;
        private String generatedAt;

        public long getTotalIntegrations() {
            return totalIntegrations;
        }

        public void setTotalIntegrations(long totalIntegrations) {
            this.totalIntegrations = totalIntegrations;
        }

        public long getHealthyCount() {
            return healthyCount;
        }

        public void setHealthyCount(long healthyCount) {
            this.healthyCount = healthyCount;
        }

        public long getDegradedCount() {
            return degradedCount;
        }

        public void setDegradedCount(long degradedCount) {
            this.degradedCount = degradedCount;
        }

        public long getUnhealthyCount() {
            return unhealthyCount;
        }

        public void setUnhealthyCount(long unhealthyCount) {
            this.unhealthyCount = unhealthyCount;
        }

        public long getUnknownCount() {
            return unknownCount;
        }

        public void setUnknownCount(long unknownCount) {
            this.unknownCount = unknownCount;
        }

        public long getTotalRequests() {
            return totalRequests;
        }

        public void setTotalRequests(long totalRequests) {
            this.totalRequests = totalRequests;
        }

        public long getTotalFailures() {
            return totalFailures;
        }

        public void setTotalFailures(long totalFailures) {
            this.totalFailures = totalFailures;
        }

        public long getTotalTimeouts() {
            return totalTimeouts;
        }

        public void setTotalTimeouts(long totalTimeouts) {
            this.totalTimeouts = totalTimeouts;
        }

        public double getOverallErrorRate() {
            return overallErrorRate;
        }

        public void setOverallErrorRate(double overallErrorRate) {
            this.overallErrorRate = overallErrorRate;
        }

        public HealthStatus getOverallStatus() {
            return overallStatus;
        }

        public void setOverallStatus(HealthStatus overallStatus) {
            this.overallStatus = overallStatus;
        }

        public String getGeneratedAt() {
            return generatedAt;
        }

        public void setGeneratedAt(String generatedAt) {
            this.generatedAt = generatedAt;
        }
    }
}
