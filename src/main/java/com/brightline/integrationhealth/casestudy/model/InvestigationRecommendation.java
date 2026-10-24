package com.brightline.integrationhealth.casestudy.model;

/**
 * Investigation recommendation for anomalies detected in integration logs.
 */
public class InvestigationRecommendation {

    private String type;          // e.g., "REPEATED_500_ERRORS", "RATE_LIMITING", "SERVICE_UNAVAILABLE", "REPEATED_TIMEOUTS"
    private String title;         // Short title
    private String description;   // Longer explanation
    private int count;            // How many occurrences
    private String lastObserved;  // ISO-8601 timestamp of most recent occurrence

    public InvestigationRecommendation() {
    }

    public InvestigationRecommendation(String type, String title, String description,
                                        int count, String lastObserved) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.count = count;
        this.lastObserved = lastObserved;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getLastObserved() {
        return lastObserved;
    }

    public void setLastObserved(String lastObserved) {
        this.lastObserved = lastObserved;
    }
}
