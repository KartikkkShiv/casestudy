# PROJECT FLOW AND ARCHITECTURE

## Brightline HR — API Integration Health Monitoring Dashboard

> **Document type:** Architecture & Flow Guide  
> **Scope:** End-to-end system design, component interactions, execution traces, and sequence flows  
> **Constraint:** Documentation only — no source files were modified  

---

## Table of Contents

1. [System Architecture Overview](#1-system-architecture-overview)
2. [Application Startup Flow](#2-application-startup-flow)
3. [Backend Request / Response Lifecycle](#3-backend-request--response-lifecycle)
4. [Frontend Data Flow and Rendering Lifecycle](#4-frontend-data-flow-and-rendering-lifecycle)
5. [Card Rendering and Expansion Mechanism](#5-card-rendering-and-expansion-mechanism)
6. [Auto-Refresh and Manual Refresh Flow](#6-auto-refresh-and-manual-refresh-flow)
7. [Theme Switching Mechanism](#7-theme-switching-mechanism)
8. [View Switching Architecture (Overview, Matrix, Detailed)](#8-view-switching-architecture-overview-matrix-detailed)
9. [Error Handling and Resilience Flows](#9-error-handling-and-resilience-flows)
10. [File Relationship Matrix](#10-file-relationship-matrix)
11. [End-to-End Execution Trace (Example: `/console`)](#11-end-to-end-execution-trace-example-console)
12. [Key Technical Concepts and Design Patterns](#12-key-technical-concepts-and-design-patterns)

---

## 1. System Architecture Overview

The application follows a classic **Client-Server architecture** built on Spring Boot for the backend and a single-file vanilla HTML/CSS/JavaScript single-page application (SPA) for the frontend.

```mermaid
graph TB
    subgraph Browser ["Client (Browser SPA)"]
        UI["index.html<br/>(DOM, CSS Grid, Theme Manager, View Switcher)"]
    end

    subgraph Server ["Spring Boot Server (Port 8081)"]
        subgraph Controller ["REST Controller Layer"]
            HC["HealthController<br/>/api/integrations/health<br/>/api/integrations/console"]
        end

        subgraph Service ["Business Service Layer"]
            HS["HealthService<br/>(Health Calculation & Classification)"]
            OS["OperationsService<br/>(Enhanced Metrics, Recommendations, Failures)"]
        end

        subgraph Data ["Data Access Layer"]
            LR["LogReader<br/>(Jackson ObjectMapper)"]
        end
    end

    subgraph Storage ["Classpath Storage"]
        JSON["data/integration_logs.json<br/>(60 synthetic log entries)"]
    end

    UI -- "HTTP GET (fetch)" --> HC
    HC --> HS
    HC --> OS
    HS --> LR
    OS --> HS
    OS --> LR
    LR -- "ClassPathResource" --> JSON
```

### Architectural Characteristics

- **Stateless Backend:** The Spring Boot backend holds no conversational state. Every request independently reads, parses, and analyzes the log dataset.
- **Embedded Static Assets:** Frontend assets (`index.html`) are served directly from Spring Boot's static resource directory (`src/main/resources/static/`), eliminating the need for a separate frontend build or hosting server.
- **Pure Client-Side Rendering:** The backend provides raw JSON payloads; the browser constructs the entire DOM dynamically.

---

## 2. Application Startup Flow

When the application starts, Spring Boot initializes the application context, configures beans, and starts the embedded Tomcat server on port `8081`.

```mermaid
sequenceDiagram
    autonumber
    participant Main as CasestudyApplication
    participant Spring as Spring Boot Container
    participant Bean as Spring Beans (@Service, @RestController)
    participant Tomcat as Embedded Tomcat (Port 8081)

    Main->>Spring: SpringApplication.run(CasestudyApplication.class, args)
    Spring->>Spring: Scan package com.brightline.integrationhealth.casestudy
    Spring->>Bean: Instantiate LogReader bean
    Spring->>Bean: Instantiate HealthService bean (injects LogReader)
    Spring->>Bean: Instantiate OperationsService bean (injects LogReader, HealthService)
    Spring->>Bean: Instantiate HealthController bean (injects HealthService, OperationsService)
    Spring->>Tomcat: Start embedded web server on port 8081
    Tomcat-->>Main: Server ready and listening
    Note over Main,Tomcat: Application is now ready to accept HTTP requests.
```

---

## 3. Backend Request / Response Lifecycle

### 3.1 Endpoint Routing & Payload Structure

The `HealthController` exposes two REST endpoints under `/api/integrations`:

```mermaid
graph LR
    Client["HTTP Client (Browser)"] -->|GET /api/integrations/health| HC1["HealthController.getHealth()"]
    Client -->|GET /api/integrations/console| HC2["HealthController.getOperationsConsole()"]

    HC1 --> HS["HealthService.buildDashboard()"]
    HS --> LR["LogReader.loadLogs()"]

    HC2 --> OS["OperationsService.buildOperationsConsole()"]
    OS --> HS
    OS --> LR
```

### 3.2 Detailed Sequence: `/api/integrations/console`

```mermaid
sequenceDiagram
    autonumber
    participant Client as Browser (fetch)
    participant Controller as HealthController
    participant OpsSvc as OperationsService
    participant HealthSvc as HealthService
    participant LogReader as LogReader
    participant JSON as integration_logs.json

    Client->>Controller: GET /api/integrations/console
    Controller->>OpsSvc: buildOperationsConsole()
    
    OpsSvc->>LogReader: loadLogs()
    LogReader->>JSON: Read ClassPathResource("data/integration_logs.json")
    JSON-->>LogReader: JSON String / InputStream
    LogReader->>LogReader: Jackson readValue() into IntegrationLogWrapper
    LogReader-->>OpsSvc: List<IntegrationLog> (60 entries)

    OpsSvc->>OpsSvc: calculateEnhancedHealth(allLogs)
    Note over OpsSvc: Group by integration name (LinkedHashMap)

    loop For each integration
        OpsSvc->>HealthSvc: calculateHealth(integrationName, logs)
        HealthSvc-->>OpsSvc: IntegrationHealth (base metrics)
        OpsSvc->>OpsSvc: Calculate successCount, errorCount, timeoutCount
        OpsSvc->>OpsSvc: Calculate successRate, unsuccessfulRate, maxLatencyMs
        OpsSvc->>OpsSvc: Extract latest timestamps & status code
        OpsSvc->>OpsSvc: Calculate consecutiveFailures (from end backwards)
        OpsSvc->>OpsSvc: generateRecommendations()
        OpsSvc-->>OpsSvc: EnhancedIntegrationHealth
    end

    OpsSvc->>HealthSvc: buildOverallSummary(baseHealths)
    HealthSvc-->>OpsSvc: OverallSummary
    OpsSvc->>OpsSvc: buildOperationsSummary() (adds totalSuccesses, totalErrors)
    OpsSvc->>OpsSvc: extractRecentFailures(allLogs) (sorted newest first)

    OpsSvc-->>Controller: OperationsConsoleResponse
    Controller-->>Client: ResponseEntity.ok(response) (JSON)
```

---

## 4. Frontend Data Flow and Rendering Lifecycle

When the user opens the page or when the auto-refresh timer fires, the frontend executes its data-fetching and rendering cycle.

```mermaid
sequenceDiagram
    autonumber
    participant User as Browser User
    participant Page as index.html (JS Engine)
    participant API as Spring Boot API (/api/integrations/console)

    User-->>Page: Loads page or clicks Refresh / Timer ticks
    Page->>Page: showLoadingState() (spinner, disable refresh button)
    Page->>API: fetch("/api/integrations/console")
    
    alt Fetch Success (200 OK)
        API-->>Page: JSON Response (OperationsConsoleResponse)
        Page->>Page: Store data in global state (currentData)
        Page->>Page: renderContent()
        
        alt Current View == "overview"
            Page->>Page: renderOverviewMode()
            Note over Page: Renders Summary + Matrix + Recent Failures
        else Current View == "matrix"
            Page->>Page: renderMatrixMode()
            Note over Page: Renders Integration Matrix Only
        else Current View == "detailed"
            Page->>Page: renderDetailedMode()
            Note over Page: Renders Summary + Detailed Cards + Recent Failures
        end
        
        Page->>Page: hideLoadingState()
        Page->>Page: restartCountdownTimer()
    else Fetch Error / Network Failure
        API-->>Page: HTTP Error or Network Exception
        Page->>Page: renderErrorState(errorMessage)
        Page->>Page: hideLoadingState()
    end
```

---

## 5. Card Rendering and Expansion Mechanism

Each integration card in the dashboard supports a drill-down expansion to view detailed metrics and investigation recommendations.

```mermaid
sequenceDiagram
    autonumber
    participant User as User
    participant DOM as Browser DOM
    participant JS as index.html (Script)

    User->>DOM: Click "View Details" button on integration card
    DOM->>JS: toggleCardDetails(integrationName)
    
    JS->>DOM: Find card element by ID (id="card-{name}")
    JS->>DOM: Find expandable details container (id="details-{name}")
    
    alt Card is currently collapsed
        JS->>DOM: Add class "expanded" to card element
        JS->>JS: renderExpandedDetails(integration)
        JS->>DOM: Populate details container innerHTML
        DOM-->>User: Card expands, showing metrics & recommendations
    else Card is currently expanded
        JS->>DOM: Remove class "expanded" from card element
        DOM-->>User: Card collapses (CSS display: none)
    end
```

### CSS / DOM State Transition

```
[ Collapsed State ]
  .integration-window { ... }
  .expandable-details { display: none; }
        │
        │  User clicks "View Details"
        ▼
[ Expanded State ]
  .integration-window.expanded { ... }
  .integration-window.expanded .expandable-details { display: block; }
```

---

## 6. Auto-Refresh and Manual Refresh Flow

The dashboard includes a 30-second auto-refresh countdown timer and a manual refresh button.

```mermaid
stateDiagram-v2
    [*] --> Idle: Page Loaded
    Idle --> CountdownActive: Auto-refresh enabled (default)
    
    state CountdownActive {
        [*] --> Ticking
        Ticking --> Ticking: Decrement timer every 1s (update progress bar)
        Ticking --> TriggerFetch: Timer reaches 0
    }

    TriggerFetch --> Fetching: loadConsole() invoked
    Fetching --> Success: API returns 200 OK
    Fetching --> Error: API returns error / timeout

    Success --> CountdownActive: Reset timer to 30s, update UI
    Error --> CountdownActive: Show error banner, reset timer to 30s

    Idle --> Fetching: User clicks manual "Refresh Now" button
    ManualToggle --> CountdownActive: User toggles auto-refresh switch
```

---

## 7. Theme Switching Mechanism

The dashboard supports three theme modes: Light, Dark, and System (auto-detected via OS preference).

```mermaid
graph TD
    User["User clicks Theme Toggle (Sun / Moon / System)"] --> JS["setTheme(themeName)"]
    JS --> Save["localStorage.setItem('theme', themeName)"]
    JS --> Apply["Apply data-theme attribute to <html>"]

    subgraph CSS ["CSS Theme Rules"]
        Root["root:not([data-theme='light'])"] --> DarkMedia["@media (prefers-color-scheme: dark)"]
        DarkAttr["root[data-theme='dark']"]
        LightAttr["root[data-theme='light']"]
    end

    Apply --> CSS
    DarkMedia --> RenderDark["Render Dark Palette"]
    DarkAttr --> RenderDark
    LightAttr --> RenderLight["Render Light Palette"]
```

---

## 8. View Switching Architecture (Overview, Matrix, Detailed)

The frontend provides three distinct operational views, controlled by the view switcher navigation bar.

```mermaid
graph TD
    User["User clicks View Tab"] --> Switch["switchView(viewName)"]
    Switch --> Save["Update active tab state"]
    Switch --> Render["renderContent()"]

    Render --> Check{View Name?}
    
    Check -->|overview| OV["renderOverviewMode()<br/>• Overall Summary Cards<br/>• Integration Health Grid<br/>• Recent Failures Timeline"]
    Check -->|matrix| MX["renderMatrixMode()<br/>• Integration Health Grid Only<br/>(Clean, focused status scan)"]
    Check -->|detailed| DT["renderDetailedMode()<br/>• Overall Summary Cards<br/>• All Cards Expanded by Default<br/>• Recent Failures Timeline"]
```

---

## 9. Error Handling and Resilience Flows

```mermaid
sequenceDiagram
    autonumber
    participant App as Spring Boot Backend
    participant Client as Frontend JavaScript

    Note over App: Backend Exception Handling
    App->>App: LogReader throws IOException (missing/corrupt file)
    App->>App: HealthController catch(Exception e)
    App-->>Client: ResponseEntity.internalServerError().build() (HTTP 500)

    Note over Client: Frontend Resilience
    Client->>Client: fetch() receives non-200 or network error
    Client->>Client: Catch block catches error
    Client->>Client: renderErrorState("Failed to load operations console: ...")
    Client->>Client: Display user-friendly error banner in DOM
    Client->>Client: Keep last known good state or show Retry button
```

---

## 10. File Relationship Matrix

This matrix maps how backend and frontend files interact across the system lifecycle.

| Source File | Interacts With | Relationship / Dependency |
|---|---|---|
| `CasestudyApplication.java` | Spring Boot Container | Entry point; boots application. |
| `HealthController.java` | `HealthService`, `OperationsService` | Controller layer; exposes `/health` and `/console` REST APIs. |
| `LogReader.java` | `integration_logs.json`, Jackson `ObjectMapper` | Data access layer; parses JSON into POJOs. |
| `HealthService.java` | `LogReader`, `IntegrationHealth`, `HealthStatus` | Core business logic; calculates health metrics and classification rules. |
| `OperationsService.java` | `LogReader`, `HealthService`, `EnhancedIntegrationHealth` | Operations business logic; computes enhanced metrics, recommendations, and failure history. |
| `IntegrationLog.java` | Jackson `ObjectMapper` | POJO mapping single log entry. |
| `EnhancedIntegrationHealth.java` | `IntegrationHealth` | Model inheritance; adds 12 operations metrics fields. |
| `index.html` | `HealthController` (`/api/integrations/console`) | Single-file frontend SPA; consumes REST API via `fetch()`. |
| `HealthServiceTests.java` | `HealthService`, `LogReader` | Unit test suite for health calculations. |
| `HealthControllerTests.java` | `HealthController`, MockMvc | Integration test suite for REST endpoints. |
| `OperationsServiceTests.java` | `OperationsService`, `LogReader`, `HealthService` | Unit test suite for operations metrics and recommendations. |

---

## 11. End-to-End Execution Trace (Example: `/console`)

The following trace traces a complete end-to-end request when an operator clicks the dashboard or the auto-refresh timer fires.

```
1. [Browser] User opens http://localhost:8081/
2. [Browser] index.html loads and executes DOMContentLoaded event listener.
3. [Browser] loadConsole() initiates fetch GET /api/integrations/console.
4. [Tomcat] HealthController.getOperationsConsole() receives HTTP request.
5. [Spring] OperationsService.buildOperationsConsole() is invoked.
6. [Service] OperationsService calls logReader.loadLogs().
7. [Disk/Classpath] LogReader opens data/integration_logs.json via ClassPathResource.
8. [Jackson] ObjectMapper parses JSON array into List<IntegrationLog> (60 objects).
9. [OperationsService] Groups logs by integration name (Slack, ADP, DocuSign, Workday, Greenhouse).
10. [Loop - 5 Integrations]:
    a. Calls healthService.calculateHealth(name, logs) -> computes base health status, error rate, latency.
    b. Calculates enhanced metrics: successCount, errorCount, timeoutCount, successRate, unsuccessfulRate, maxLatencyMs.
    c. Extracts latest request, success, and failure timestamps.
    d. Computes consecutiveFailures by iterating logs backwards from the end.
    e. Generates InvestigationRecommendations (e.g. REPEATED_500_ERRORS, RATE_LIMITING) via rule engine.
11. [OperationsService] Calls healthService.buildOverallSummary() -> aggregates totals, overall error rate, worst status.
12. [OperationsService] Computes overall successes and errors, builds OperationsSummary.
13. [OperationsService] Extracts and sorts all recent failures (newest first) with consecutive failure counts.
14. [Controller] Wraps OperationsConsoleResponse in ResponseEntity.ok() and serializes to JSON.
15. [Browser] fetch() receives 200 OK JSON response.
16. [Browser] renderContent() executes based on active view mode (Overview).
17. [Browser] DOM is updated with summary cards, integration grid, and recent failures timeline.
18. [Browser] Countdown timer resets to 30 seconds.
```

---

## 12. Key Technical Concepts and Design Patterns

### 12.1 Worst-Case Health Aggregation
The dashboard uses a hierarchical classification model (`HEALTHY` → `DEGRADED` → `UNHEALTHY`). When calculating overall status or individual integration status across multiple rules, the **worst status always wins**. For example, if an integration has a low error rate (HEALTHY) but experiences a single timeout (DEGRADED), its final status is DEGRADED.

### 12.2 Backward-Compatible API Design
The application maintains two endpoints:
- `/api/integrations/health`: Returns the legacy `DashboardResponse` model used by earlier versions or simple monitors.
- `/api/integrations/console`: Returns the extended `OperationsConsoleResponse` model containing enhanced metrics, recommendations, and failure histories.
This ensures existing consumers of `/health` are unaffected while powering the new operations console.

### 12.3 Separation of Concerns (Backend)
- **LogReader:** Pure I/O and deserialization. Knows nothing about business rules.
- **HealthService:** Pure metric calculation and threshold classification. Knows nothing about HTTP or file paths.
- **OperationsService:** Composition service that orchestrates `LogReader` and `HealthService` to build enhanced operations views.
- **HealthController:** Thin REST adapter translating HTTP requests to service calls.

### 12.4 Client-Side View State Management
The frontend maintains an in-memory application state object tracking:
- Current view mode (`overview`, `matrix`, `detailed`)
- Current theme (`light`, `dark`, `system`)
- Auto-refresh toggle state and countdown seconds
- Last fetched data payload (`currentData`)
This enables instant view switching and theme toggling without round-trips to the server.

---

*This document was generated from source code analysis only. No application files were modified.*
