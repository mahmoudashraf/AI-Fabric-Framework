package com.foundation.ai.integration;

import com.foundation.ai.dto.ThreatDetectionRequest;
import com.foundation.ai.dto.ThreatDetectionResponse;
import com.foundation.ai.dto.SecurityMetricsResponse;
import com.foundation.ai.dto.SecurityIncidentRequest;
import com.foundation.ai.dto.SecurityIncidentResponse;
import com.foundation.ai.service.AISecurityService;
import com.foundation.ai.service.AIAuditService;
import com.foundation.ai.service.AIComplianceService;
import com.foundation.ai.exception.AISecurityException;
import com.foundation.ai.exception.AIComplianceException;
import com.foundation.ai.exception.AIAuditException;
import com.foundation.ai.exception.AIProcessingException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for AI Security services
 * Tests the complete flow from threat detection to incident response
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AISecurityIntegrationTest {

    @Autowired
    private AISecurityService aiSecurityService;

    @Autowired
    private AIAuditService aiAuditService;

    @Autowired
    private AIComplianceService aiComplianceService;

    private ThreatDetectionRequest threatDetectionRequest;
    private SecurityIncidentRequest securityIncidentRequest;

    @BeforeEach
    void setUp() {
        threatDetectionRequest = ThreatDetectionRequest.builder()
                .query("suspicious login attempt from unknown location")
                .context("User authentication system")
                .riskThreshold(0.7)
                .enableRealTimeAnalysis(true)
                .enableThreatIntelligence(true)
                .enableBehavioralAnalysis(true)
                .build();

        securityIncidentRequest = SecurityIncidentRequest.builder()
                .title("Suspicious Login Attempt")
                .description("Multiple failed login attempts detected")
                .severity("HIGH")
                .category("AUTHENTICATION")
                .source("AI_SECURITY_SYSTEM")
                .affectedSystems(List.of("AUTH_SERVICE", "USER_DB"))
                .build();
    }

    @Test
    @DisplayName("Complete threat detection and incident response flow")
    void testCompleteThreatDetectionFlow() throws Exception {
        // Step 1: Detect threats
        ThreatDetectionResponse threatResponse = aiSecurityService.detectThreats(threatDetectionRequest);
        
        assertNotNull(threatResponse);
        assertNotNull(threatResponse.getThreats());
        assertTrue(threatResponse.getConfidenceScore() >= 0.0 && threatResponse.getConfidenceScore() <= 1.0);
        assertNotNull(threatResponse.getProcessingTimeMs());
        assertTrue(threatResponse.getProcessingTimeMs() > 0);

        // Step 2: Create security incident if threats detected
        if (!threatResponse.getThreats().isEmpty()) {
            SecurityIncidentResponse incidentResponse = aiSecurityService.createIncident(securityIncidentRequest);
            
            assertNotNull(incidentResponse);
            assertNotNull(incidentResponse.getIncidentId());
            assertEquals("Suspicious Login Attempt", incidentResponse.getTitle());
            assertEquals("HIGH", incidentResponse.getSeverity());
            assertNotNull(incidentResponse.getCreatedAt());
        }

        // Step 3: Verify audit trail
        var auditLogs = aiAuditService.getAuditLogs(0, 10, "timestamp", "desc");
        assertNotNull(auditLogs);
        assertTrue(auditLogs.getLogs().size() > 0);

        // Step 4: Check compliance
        var complianceCheck = aiComplianceService.checkCompliance("GDPR", "SECURITY");
        assertNotNull(complianceCheck);
        assertTrue(complianceCheck.getComplianceScore() >= 0.0 && complianceCheck.getComplianceScore() <= 1.0);
    }

    @Test
    @DisplayName("Real-time threat monitoring and response")
    void testRealTimeThreatMonitoring() throws Exception {
        // Simulate real-time threat detection
        CompletableFuture<ThreatDetectionResponse> future = CompletableFuture.supplyAsync(() -> {
            try {
                return aiSecurityService.detectThreats(threatDetectionRequest);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ThreatDetectionResponse response = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(response);
        assertTrue(response.getProcessingTimeMs() < 5000); // Should be fast for real-time
        
        // Verify metrics are updated
        SecurityMetricsResponse metrics = aiSecurityService.getSecurityMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.getTotalThreatsDetected() >= 0);
        assertTrue(metrics.getAverageResponseTime() > 0);
    }

    @Test
    @DisplayName("Threat detection with different risk levels")
    void testThreatDetectionWithDifferentRiskLevels() throws Exception {
        // Test low risk threshold
        ThreatDetectionRequest lowRiskRequest = threatDetectionRequest.toBuilder()
                .riskThreshold(0.3)
                .build();
        
        ThreatDetectionResponse lowRiskResponse = aiSecurityService.detectThreats(lowRiskRequest);
        assertNotNull(lowRiskResponse);
        
        // Test high risk threshold
        ThreatDetectionRequest highRiskRequest = threatDetectionRequest.toBuilder()
                .riskThreshold(0.9)
                .build();
        
        ThreatDetectionResponse highRiskResponse = aiSecurityService.detectThreats(highRiskRequest);
        assertNotNull(highRiskResponse);
        
        // High risk threshold should detect fewer threats
        assertTrue(highRiskResponse.getThreats().size() <= lowRiskResponse.getThreats().size());
    }

    @Test
    @DisplayName("Security incident lifecycle management")
    void testSecurityIncidentLifecycle() throws Exception {
        // Create incident
        SecurityIncidentResponse incident = aiSecurityService.createIncident(securityIncidentRequest);
        assertNotNull(incident);
        assertNotNull(incident.getIncidentId());
        
        // Update incident
        var updateRequest = SecurityIncidentRequest.builder()
                .title("Updated: Suspicious Login Attempt")
                .description("Additional analysis completed")
                .severity("CRITICAL")
                .status("INVESTIGATING")
                .build();
        
        SecurityIncidentResponse updatedIncident = aiSecurityService.updateIncident(
                incident.getIncidentId(), updateRequest);
        
        assertNotNull(updatedIncident);
        assertEquals("Updated: Suspicious Login Attempt", updatedIncident.getTitle());
        assertEquals("CRITICAL", updatedIncident.getSeverity());
        
        // Resolve incident
        SecurityIncidentResponse resolvedIncident = aiSecurityService.resolveIncident(incident.getIncidentId());
        assertNotNull(resolvedIncident);
        assertEquals("RESOLVED", resolvedIncident.getStatus());
    }

    @Test
    @DisplayName("Error handling and recovery")
    void testErrorHandlingAndRecovery() {
        // Test with invalid request
        ThreatDetectionRequest invalidRequest = ThreatDetectionRequest.builder()
                .query("")
                .context("")
                .riskThreshold(-1.0)
                .build();
        
        assertThrows(AIProcessingException.class, () -> {
            aiSecurityService.detectThreats(invalidRequest);
        });
        
        // Test with null request
        assertThrows(AIProcessingException.class, () -> {
            aiSecurityService.detectThreats(null);
        });
    }

    @Test
    @DisplayName("Performance and scalability")
    void testPerformanceAndScalability() throws Exception {
        long startTime = System.currentTimeMillis();
        
        // Simulate multiple concurrent threat detections
        List<CompletableFuture<ThreatDetectionResponse>> futures = List.of(
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return aiSecurityService.detectThreats(threatDetectionRequest);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }),
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return aiSecurityService.detectThreats(threatDetectionRequest);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }),
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return aiSecurityService.detectThreats(threatDetectionRequest);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
        );
        
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        // Should complete within reasonable time
        assertTrue(totalTime < 10000); // 10 seconds
        
        // All responses should be valid
        for (CompletableFuture<ThreatDetectionResponse> future : futures) {
            ThreatDetectionResponse response = future.get();
            assertNotNull(response);
            assertNotNull(response.getThreats());
        }
    }

    @Test
    @DisplayName("Integration with audit and compliance systems")
    void testIntegrationWithAuditAndCompliance() throws Exception {
        // Perform security operation
        ThreatDetectionResponse threatResponse = aiSecurityService.detectThreats(threatDetectionRequest);
        
        // Verify audit trail
        var auditLogs = aiAuditService.getAuditLogs(0, 10, "timestamp", "desc");
        assertNotNull(auditLogs);
        
        // Check if security operation was logged
        boolean securityOperationLogged = auditLogs.getLogs().stream()
                .anyMatch(log -> log.getOperationType().equals("THREAT_DETECTION"));
        assertTrue(securityOperationLogged);
        
        // Verify compliance check
        var complianceCheck = aiComplianceService.checkCompliance("GDPR", "SECURITY");
        assertNotNull(complianceCheck);
        
        // Verify metrics are updated
        SecurityMetricsResponse metrics = aiSecurityService.getSecurityMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.getTotalThreatsDetected() >= 0);
    }
}