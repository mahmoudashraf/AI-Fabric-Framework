package com.ai.infrastructure.controller;

import com.ai.infrastructure.dto.AIAuditLog;
import com.ai.infrastructure.dto.AIAuditRequest;
import com.ai.infrastructure.dto.AIAuditResponse;
import com.ai.infrastructure.audit.AIAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for AI Audit operations
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/audit")
@RequiredArgsConstructor
public class AIAuditController {

    private final AIAuditService aiAuditService;

    /**
     * Log an audit event
     */
    @PostMapping("/log")
    public ResponseEntity<AIAuditResponse> logAuditEvent(
            @Valid @RequestBody AIAuditRequest request) {
        log.info("Logging audit event for user: {}", request.getUserId());
        
        try {
            AIAuditResponse response = aiAuditService.logAuditEvent(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error logging audit event", e);
            return ResponseEntity.internalServerError()
                .body(AIAuditResponse.builder()
                    .userId(request.getUserId())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build());
        }
    }

    /**
     * Get audit logs for a user
     */
    @GetMapping("/logs/{userId}")
    public ResponseEntity<List<AIAuditLog>> getAuditLogs(
            @PathVariable String userId) {
        log.info("Retrieving audit logs for user: {}", userId);
        
        try {
            List<AIAuditLog> logs = aiAuditService.getAuditLogs(userId);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("Error retrieving audit logs for user: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get all audit logs
     */
    @GetMapping("/logs")
    public ResponseEntity<List<AIAuditLog>> getAllAuditLogs() {
        log.info("Retrieving all audit logs");
        
        try {
            List<AIAuditLog> logs = aiAuditService.getAllAuditLogs();
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("Error retrieving all audit logs", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get audit logs by risk level
     */
    @GetMapping("/logs/risk/{riskLevel}")
    public ResponseEntity<List<AIAuditLog>> getAuditLogsByRiskLevel(
            @PathVariable String riskLevel) {
        log.info("Retrieving audit logs for risk level: {}", riskLevel);
        
        try {
            List<AIAuditLog> logs = aiAuditService.getAuditLogsByRiskLevel(riskLevel);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("Error retrieving audit logs for risk level: {}", riskLevel, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get audit logs with anomalies
     */
    @GetMapping("/logs/anomalies")
    public ResponseEntity<List<AIAuditLog>> getAuditLogsWithAnomalies() {
        log.info("Retrieving audit logs with anomalies");
        
        try {
            List<AIAuditLog> logs = aiAuditService.getAuditLogsWithAnomalies();
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("Error retrieving audit logs with anomalies", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Search audit logs
     */
    @PostMapping("/logs/search")
    public ResponseEntity<List<AIAuditLog>> searchAuditLogs(
            @RequestParam String query,
            @RequestBody(required = false) Map<String, Object> filters) {
        log.info("Searching audit logs with query: {}", query);
        
        try {
            List<AIAuditLog> logs = aiAuditService.searchAuditLogs(query, filters);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("Error searching audit logs", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Generate audit report
     */
    @GetMapping("/reports/{userId}")
    public ResponseEntity<Map<String, Object>> generateAuditReport(
            @PathVariable String userId,
            @RequestParam(defaultValue = "30") String period) {
        log.info("Generating audit report for user: {} for period: {}", userId, period);
        
        try {
            Map<String, Object> report = aiAuditService.generateAuditReport(userId, period);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Error generating audit report for user: {}", userId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get audit statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getAuditStatistics() {
        log.info("Retrieving audit statistics");
        
        try {
            Map<String, Object> stats = aiAuditService.getAuditStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error retrieving audit statistics", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Clear audit logs for a user
     */
    @DeleteMapping("/logs/{userId}")
    public ResponseEntity<Void> clearAuditLogs(@PathVariable String userId) {
        log.info("Clearing audit logs for user: {}", userId);
        
        try {
            aiAuditService.clearAuditLogs(userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error clearing audit logs for user: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Clear all audit logs
     */
    @DeleteMapping("/logs")
    public ResponseEntity<Void> clearAllAuditLogs() {
        log.info("Clearing all audit logs");
        
        try {
            aiAuditService.clearAllAuditLogs();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error clearing all audit logs", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Health check for audit service
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        log.info("Performing audit service health check");
        
        try {
            Map<String, Object> health = Map.of(
                "status", "UP",
                "service", "AIAuditService",
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Error performing audit service health check", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "DOWN",
                    "service", "AIAuditService",
                    "error", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
                ));
        }
    }
}