package com.brightline.integrationhealth.casestudy.model;

import java.util.List;

/**
 * Wrapper for the top-level JSON structure: { "logs": [...] }
 */
public class IntegrationLogWrapper {

    private List<IntegrationLog> logs;

    public IntegrationLogWrapper() {
    }

    public List<IntegrationLog> getLogs() {
        return logs;
    }

    public void setLogs(List<IntegrationLog> logs) {
        this.logs = logs;
    }
}
