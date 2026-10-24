package com.brightline.integrationhealth.casestudy.controller;

import com.brightline.integrationhealth.casestudy.model.DashboardResponse;
import com.brightline.integrationhealth.casestudy.service.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the integration health dashboard API.
 */
@RestController
@RequestMapping("/api/integrations")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * Returns the health dashboard with per-integration metrics and overall summary.
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
}
