package com.ai.infrastructure.controller;

import com.ai.infrastructure.dto.AIComplianceReport;
import com.ai.infrastructure.dto.AIComplianceRequest;
import com.ai.infrastructure.dto.AIComplianceResponse;
import com.ai.infrastructure.compliance.AIComplianceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for AI Compliance operations
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/compliance")
@RequiredArgsConstructor
public class AIComplianceController {

    private final AIComplianceService aiComplianceService;

    /**
     * Check compliance for a request
     */
    @PostMapping("/check")
    public ResponseEntity<AIComplianceResponse> checkCompliance(
            @Valid @RequestBody AIComplianceRequest request) {
        log.info("Checking compliance for request: {}", request.getRequestId());
        
        try {
            AIComplianceResponse response = aiComplianceService.checkCompliance(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error checking compliance", e);
            return ResponseEntity.internalServerError()
                .body(AIComplianceResponse.builder()
                    .requestId(request.getRequestId())
                    .userId(request.getUserId())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build());
        }
    }

    /**
     * Get compliance reports for a user
     */
    @GetMapping("/reports/{userId}")
    public ResponseEntity<List<AIComplianceReport>> getComplianceReports(
            @PathVariable String userId) {
        log.info("Retrieving compliance reports for user: {}", userId);
        
        try {
            List<AIComplianceReport> reports = aiComplianceService.getComplianceReports(userId);
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            log.error("Error retrieving compliance reports for user: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Generate compliance summary report
     */
    @GetMapping("/reports/{userId}/summary")
    public ResponseEntity<AIComplianceReport> generateSummaryReport(
            @PathVariable String userId,
            @RequestParam(defaultValue = "30") String period) {
        log.info("Generating compliance summary report for user: {} for period: {}", userId, period);
        
        try {
            AIComplianceReport report = aiComplianceService.generateSummaryReport(userId, period);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Error generating compliance summary report for user: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get compliance statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getComplianceStatistics() {
        log.info("Retrieving compliance statistics");
        
        try {
            Map<String, Object> stats = aiComplianceService.getComplianceStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error retrieving compliance statistics", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Health check for compliance service
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        log.info("Performing compliance service health check");
        
        try {
            Map<String, Object> health = Map.of(
                "status", "UP",
                "service", "AIComplianceService",
                "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Error performing compliance service health check", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "DOWN",
                    "service", "AIComplianceService",
                    "error", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
                ));
        }
    }
}