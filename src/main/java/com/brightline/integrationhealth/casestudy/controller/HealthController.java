package com.brightline.integrationhealth.casestudy.controller;

import com.brightline.integrationhealth.casestudy.model.DashboardResponse;
import com.brightline.integrationhealth.casestudy.model.OperationsConsoleResponse;
import com.brightline.integrationhealth.casestudy.service.HealthService;
import com.brightline.integrationhealth.casestudy.service.OperationsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the integration health and operations console APIs.
 *
 * Endpoint /api/integrations/health — backward-compatible dashboard (existing API).
 * Endpoint /api/integrations/console — enhanced operations console with request history.
 */
@RestController
@RequestMapping("/api/integrations")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final HealthService healthService;
    private final OperationsService operationsService;

    public HealthController(HealthService healthService, OperationsService operationsService) {
        this.healthService = healthService;
        this.operationsService = operationsService;
    }

    /**
     * Returns the health dashboard with per-integration metrics and overall summary.
     * This endpoint maintains backward compatibility with the existing API.
     *
     * GET /api/integrations/health
     */
    @GetMapping("/health")
    public ResponseEntity<DashboardResponse> getHealth() {
        try {
            DashboardResponse response = healthService.buildDashboard();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error building health dashboard: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Returns the enhanced operations console with rich metrics, request history,
     * failure history, and investigation recommendations.
     *
     * GET /api/integrations/console
     */
    @GetMapping("/console")
    public ResponseEntity<OperationsConsoleResponse> getOperationsConsole() {
        try {
            OperationsConsoleResponse response = operationsService.buildOperationsConsole();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error building operations console: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
