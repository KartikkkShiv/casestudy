# Brightline HR — API Integration Health Dashboard

A Spring Boot application that analyzes historical API integration request logs and presents a health dashboard for Brightline HR's third-party integrations.

This dashboard processes a supplied dataset of 60 synthetic request-log entries across 5 integrations and provides at-a-glance health status, metrics, and explanations for each integration.

> **Note:** This is historical log analysis, not live monitoring of third-party services.

## Prerequisites

- **Java 21** (or later)
- **Maven** (or use the included Maven Wrapper)

## How to Run

### 1. Build and run tests

```bash
./mvnw clean test
```

On Windows:

```cmd
mvnw.cmd clean test
```

### 2. Start the application

```bash
./mvnw spring-boot:run
```

On Windows:

```cmd
mvnw.cmd spring-boot:run
```

### 3. Open the dashboard

Open your browser to: **http://localhost:8081**

### 4. Access the REST API

```
GET http://localhost:8081/api/integrations/health
```

Returns JSON with an overall summary and per-integration health metrics.

Example response structure:

```json
{
  "summary": {
    "totalIntegrations": 5,
    "healthyCount": 0,
    "degradedCount": 2,
    "unhealthyCount": 3,
    "totalRequests": 60,
    "totalFailures": 17,
    "totalTimeouts": 5,
    "overallErrorRate": 0.2833,
    "overallStatus": "UNHEALTHY",
    "generatedAt": "2026-08-04T12:00:00Z"
  },
  "integrations": [
    {
      "integration": "Slack Notifications",
      "totalRequests": 12,
      "successfulRequests": 11,
      "failedRequests": 1,
      "timeouts": 0,
      "errorRate": 0.0833,
      "timeoutRate": 0.0,
      "averageLatencyMs": 137.36,
      "status": "DEGRADED",
      "reasons": ["Error rate 8.3% exceeds degraded threshold of 5%."]
    }
  ]
}
```

## How to Run Tests

```bash
./mvnw clean test
```

The test suite includes:

- **JSON parsing** — verifies all 60 records load correctly, field mapping, timeout entries with null status codes.
- **Metric calculations** — success/failure/timeout counting, error rate, average latency for successful requests only.
- **Health classification** — HEALTHY, DEGRADED, UNHEALTHY thresholds; worst-case status rule; boundary conditions.
- **Edge cases** — empty datasets, null datasets, missing latency values, unknown status values, all-failure datasets.
- **API integration tests** — verifies the REST endpoint returns correct JSON structure and data.

## Project Structure

```
src/
├── main/
│   ├── java/com/brightline/integrationhealth/casestudy/
│   │   ├── CasestudyApplication.java          # Spring Boot entry point
│   │   ├── controller/
│   │   │   └── HealthController.java           # REST API endpoint
│   │   ├── model/
│   │   │   ├── IntegrationLog.java             # Log entry model
│   │   │   ├── IntegrationLogWrapper.java      # JSON wrapper
│   │   │   ├── HealthStatus.java               # HEALTHY/DEGRADED/UNHEALTHY/UNKNOWN
│   │   │   ├── IntegrationHealth.java          # Per-integration health summary
│   │   │   └── DashboardResponse.java          # API response with overall summary
│   │   └── service/
│   │       ├── LogReader.java                  # JSON parsing from classpath
│   │       └── HealthService.java              # Health calculation engine
│   └── resources/
│       ├── application.properties
│       ├── data/integration_logs.json          # Supplied dataset (unchanged)
│       └── static/index.html                   # Dashboard frontend
└── test/
    └── java/com/brightline/integrationhealth/casestudy/
        ├── CasestudyApplicationTests.java      # Context load test
        ├── HealthServiceTests.java             # Unit tests for calculations
        └── HealthControllerTests.java          # API integration tests
```

## Health Classification Rules

See [SOLUTION_DESIGN.md](SOLUTION_DESIGN.md) for detailed thresholds and rationale.

| Rule         | HEALTHY  | DEGRADED             | UNHEALTHY |
| ------------ | -------- | -------------------- | --------- |
| Error rate   | ≤ 5%     | > 5% to ≤ 25%        | > 25%     |
| Timeout rate | 0%       | > 0% to ≤ 20%        | > 20%     |
| Avg latency  | ≤ 1000ms | > 1000ms to ≤ 3000ms | > 3000ms  |

The **worst-case** status from any rule becomes the integration's final status.

## Current Limitations

- Processes the supplied static dataset only — not connected to live services.
- No persistence layer (no database) — logs are re-parsed on each API request.
- No time-windowed or trend analysis.
- No alerting or push notifications.
- No authentication or authorization.
- Thresholds are project assumptions, not validated against production data.

## Configuration

| Property      | Value                        | File                     |
| ------------- | ---------------------------- | ------------------------ |
| Server port   | 8081                         | `application.properties` |
| Log data file | `data/integration_logs.json` | classpath                |
