---# Solution Design — API Integration Health Dashboard

## Problem Statement

Brightline HR integrates with five third-party services (Slack Notifications, ADP Payroll Sync, DocuSign Envelope API, Workday Directory Sync, and Greenhouse ATS Sync). The Platform Operations team currently discovers integration failures only after customers report problems — often days after an issue begins.

They need a dashboard that analyzes historical request logs and provides an at-a-glance view of:

- Which integrations are healthy and which need attention.
- Key metrics: request counts, failures, timeouts, error rates, and latency.
- Clear explanations for why an integration is flagged.

**This is historical log analysis**, not live monitoring. The dashboard processes a supplied dataset of 60 synthetic request-log entries.

## Assumptions and Health Thresholds

Since the case study intentionally leaves the definition of "unhealthy" as a judgment call, the following thresholds are used as **project assumptions** — they are not universal standards and should be tuned based on real operational experience.

### Error Rate (failures ÷ total requests)

Failures include errors, timeouts, and any unrecognized status values.

| Threshold      | Status    |
| -------------- | --------- |
| ≤ 5%           | HEALTHY   |
| > 5% and ≤ 25% | DEGRADED  |
| > 25%          | UNHEALTHY |

### Timeout Rate (timeouts ÷ total requests)

Timeouts are tracked separately because they often indicate network or upstream issues distinct from application errors.

| Threshold      | Status    |
| -------------- | --------- |
| 0%             | No impact |
| > 0% and ≤ 20% | DEGRADED  |
| > 20%          | UNHEALTHY |

### Average Latency (successful requests only)

Only successful-request latency is used, since error/timeout latency is not representative of normal performance.

| Threshold             | Status    |
| --------------------- | --------- |
| ≤ 1000ms              | HEALTHY   |
| > 1000ms and ≤ 3000ms | DEGRADED  |
| > 3000ms              | UNHEALTHY |

### Worst-Case Rule

The final health status for an integration is the **worst status** triggered by any of the three rules above. This prevents an integration from appearing healthy when one dimension (e.g., timeouts) is problematic even if another (e.g., error rate) looks acceptable.

### UNKNOWN Status

Applied when there are no log records for an integration.

## Architecture and Data Flow

```
integration_logs.json  (classpath resource)
        │
        ▼
   LogReader            (parses JSON via Jackson)
        │
        ▼
   HealthService        (groups by integration, calculates metrics, classifies health)
        │
        ▼
   HealthController     (GET /api/integrations/health → JSON)
        │
        ▼
   index.html           (static frontend, fetches from REST API)
```

### Component Responsibilities

| Component               | Responsibility                                               |
| ----------------------- | ------------------------------------------------------------ |
| `IntegrationLog`        | Java model mapping one JSON log entry                        |
| `IntegrationLogWrapper` | Wraps the `{ "logs": [...] }` JSON structure                 |
| `HealthStatus`          | Enum: HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN                  |
| `IntegrationHealth`     | Per-integration health summary with metrics and reasons      |
| `DashboardResponse`     | API response: overall summary + per-integration list         |
| `LogReader`             | Reads and parses `data/integration_logs.json` from classpath |
| `HealthService`         | Business logic: metric calculation and health classification |
| `HealthController`      | REST endpoint exposing the dashboard data                    |
| `index.html`            | Static HTML/CSS/JS dashboard served by Spring Boot           |

### Technology Choices

- **Java 21 + Spring Boot 3.4.x** — matches the project scaffold.
- **Jackson** — JSON parsing via Spring Boot's auto-configured `ObjectMapper`.
- **Vanilla HTML/CSS/JS** — no build tools, no React, served directly from `src/main/resources/static/`.
- **JUnit 5 + MockMvc** — unit and integration tests using existing Spring Boot test dependencies.

## Design Choices and Trade-offs

### 1. Re-read logs on every request vs. caching

**Choice:** Logs are re-read from the file on each API call.
**Rationale:** The dataset is small (60 entries). This keeps the implementation simple and ensures any file changes are immediately reflected. For a production system with large datasets, logs should be cached or stored in a database.

### 2. Separate timeout tracking

**Choice:** Timeouts are counted as failures in the overall error rate _and_ tracked separately with their own threshold.
**Rationale:** Timeouts often indicate a different class of problem (network, upstream unresponsiveness) than HTTP errors (application bugs, rate limits). Ops teams benefit from seeing them distinguished.

### 3. Unknown statuses treated as failures

**Choice:** Any status value other than "success", "error", or "timeout" is counted as a non-success outcome.
**Rationale:** Unknown statuses should not be silently ignored — they likely represent unexpected failure modes that warrant investigation.

### 4. Average latency uses successful requests only

**Choice:** Error and timeout latencies are excluded from the average latency metric.
**Rationale:** Timeout latencies (30,000ms) would heavily skew averages and misrepresent typical performance. Error latencies may also be artificially long or short.

### 5. Frontend fetches from API

**Choice:** The dashboard fetches all data from `/api/integrations/health` — no hardcoded values.
**Rationale:** Single source of truth for all metrics and health classifications. Allows the frontend to be a pure presentation layer.

## Expected Health Results (from supplied dataset)

| Integration            | Total | Success | Fail | Timeout | Error Rate | Status    |
| ---------------------- | ----- | ------- | ---- | ------- | ---------- | --------- |
| Slack Notifications    | 12    | 11      | 1    | 0       | 8.3%       | HEALTHY\* |
| ADP Payroll Sync       | 12    | 7       | 5    | 0       | 41.7%      | UNHEALTHY |
| DocuSign Envelope API  | 12    | 8       | 4    | 0       | 33.3%      | UNHEALTHY |
| Workday Directory Sync | 12    | 11      | 1    | 0       | 8.3%       | HEALTHY\* |
| Greenhouse ATS Sync    | 12    | 6       | 6    | 5       | 50.0%      | UNHEALTHY |

\*Slack and Workday have an 8.3% error rate which is > 5% DEGRADED threshold, so they are classified as DEGRADED, not HEALTHY.

## Limitations

- **Static dataset** — analyzes supplied logs only; does not connect to live services.
- **No persistence** — logs are re-parsed on each API request; no database.
- **No time-windowed analysis** — all logs are treated as one batch; no "last 15 minutes" windows.
- **No alerting** — dashboard is pull-based (manual refresh); no push notifications.
- **No authentication** — the dashboard is publicly accessible on the configured port.
- **Threshold tuning** — thresholds are reasonable defaults but have not been validated against real production data.

## Future Improvements

With more time, data, or budget:

1. **Time-windowed analysis** — Calculate health over sliding windows (e.g., last 15/30/60 minutes) to detect trends.
2. **Trend visualization** — Add charts showing error rate and latency over time.
3. **Alerting** — Push notifications (email, Slack, PagerDuty) when an integration degrades.
4. **Database storage** — Persist logs for historical analysis and richer queries.
5. **Configurable thresholds** — Allow ops team to adjust thresholds per integration via an admin UI.
6. **Auto-refresh** — WebSocket or polling-based dashboard updates.
7. **Per-status-code breakdown** — Show distribution of HTTP status codes (429 vs. 500 vs. 503).
8. **AI-powered summaries** — Use an LLM to generate plain-English incident summaries from raw metrics.
