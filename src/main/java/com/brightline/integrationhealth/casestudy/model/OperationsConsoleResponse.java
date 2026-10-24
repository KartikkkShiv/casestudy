package com.brightline.integrationhealth.casestudy.model;

import java.util.List;

/**
 * Enhanced operations console response with rich request history and recommendations.
 */
public class OperationsConsoleResponse {

    private OperationsSummary summary;
    private List<EnhancedIntegrationHealth> integrations;
    private List<RequestHistoryEntry> recentFailures;
    private long refreshedAt;

    public OperationsSummary getSummary() {
        return summary;
    }

    public void setSummary(OperationsSummary summary) {
        this.summary = summary;
    }

    public List<EnhancedIntegrationHealth> getIntegrations() {
        return integrations;
    }

    public void setIntegrations(List<EnhancedIntegrationHealth> integrations) {
        this.integrations = integrations;
    }

    public List<RequestHistoryEntry> getRecentFailures() {
        return recentFailures;
    }

    public void setRecentFailures(List<RequestHistoryEntry> recentFailures) {
        this.recentFailures = recentFailures;
    }

    public long getRefreshedAt() {
        return refreshedAt;
    }

    public void setRefreshedAt(long refreshedAt) {
        this.refreshedAt = refreshedAt;
    }

    /**
     * Enhanced summary with status distribution.
     */
    public static class OperationsSummary extends DashboardResponse.OverallSummary {

        private Long totalSuccesses;
        private Long totalErrors;

        public Long getTotalSuccesses() {
            return totalSuccesses;
        }

        public void setTotalSuccesses(Long totalSuccesses) {
            this.totalSuccesses = totalSuccesses;
        }

        public Long getTotalErrors() {
            return totalErrors;
        }

        public void setTotalErrors(Long totalErrors) {
            this.totalErrors = totalErrors;
        }
    }
}
