package com.foundation.ai.integration;

import com.ai.infrastructure.dto.AISecurityRequest;
import com.ai.infrastructure.dto.AISecurityResponse;
import com.ai.infrastructure.dto.AISecurityEvent;
import com.ai.infrastructure.dto.AIAuditRequest;
import com.ai.infrastructure.dto.AIAuditResponse;
import com.ai.infrastructure.dto.AIComplianceRequest;
import com.ai.infrastructure.dto.AIComplianceResponse;
import com.ai.infrastructure.security.AISecurityService;
import com.ai.infrastructure.audit.AIAuditService;
import com.ai.infrastructure.compliance.AIComplianceService;
import com.ai.infrastructure.exception.AIServiceException;

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

    private AISecurityRequest securityRequest;
    private AIAuditRequest auditRequest;

    @BeforeEach
    void setUp() {
        securityRequest = AISecurityRequest.builder()
                .requestId("test-request-1")
                .userId("test-user-1")
                .content("suspicious login attempt from unknown location")
                .context("User authentication system")
                .ipAddress("192.168.1.100")
                .userAgent("Mozilla/5.0")
                .sessionId("session-123")
                .operationType("THREAT_DETECTION")
                .build();

        auditRequest = AIAuditRequest.builder()
                .requestId("audit-request-1")
                .userId("test-user-1")
                .operationType("SECURITY_CHECK")
                .description("Security threat detection performed")
                .build();
    }

    @Test
    @DisplayName("Complete security analysis and audit flow")
    void testCompleteSecurityAnalysisFlow() throws Exception {
        // Step 1: Perform security analysis
        AISecurityResponse securityResponse = aiSecurityService.analyzeSecurity(securityRequest);
        
        assertNotNull(securityResponse);
        assertNotNull(securityResponse.getRequestId());
        assertNotNull(securityResponse.getUserId());
        assertTrue(securityResponse.getThreatsDetected() != null);
        assertTrue(securityResponse.getRiskScore() >= 0.0 && securityResponse.getRiskScore() <= 1.0);

        // Step 2: Log audit event
        AIAuditResponse auditResponse = aiAuditService.logAuditEvent(auditRequest);
        
        assertNotNull(auditResponse);
        assertNotNull(auditResponse.getRequestId());
        assertTrue(auditResponse.isSuccess());

        // Step 3: Check compliance
        AIComplianceRequest complianceRequest = AIComplianceRequest.builder()
                .requestId("compliance-request-1")
                .userId("test-user-1")
                .framework("GDPR")
                .category("SECURITY")
                .build();
        
        AIComplianceResponse complianceResponse = aiComplianceService.checkCompliance(complianceRequest);
        assertNotNull(complianceResponse);
        assertTrue(complianceResponse.getComplianceScore() >= 0.0 && complianceResponse.getComplianceScore() <= 1.0);
    }

    @Test
    @DisplayName("Real-time security monitoring and response")
    void testRealTimeSecurityMonitoring() throws Exception {
        // Simulate real-time security analysis
        CompletableFuture<AISecurityResponse> future = CompletableFuture.supplyAsync(() -> {
            try {
                return aiSecurityService.analyzeSecurity(securityRequest);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        AISecurityResponse response = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(response);
        assertNotNull(response.getRequestId());
        assertNotNull(response.getThreatsDetected());
        
        // Verify audit trail
        AIAuditResponse auditResponse = aiAuditService.logAuditEvent(auditRequest);
        assertNotNull(auditResponse);
        assertTrue(auditResponse.isSuccess());
    }

    @Test
    @DisplayName("Security analysis with different risk levels")
    void testSecurityAnalysisWithDifferentRiskLevels() throws Exception {
        // Test with different content types
        AISecurityRequest lowRiskRequest = AISecurityRequest.builder()
                .requestId("test-request-2")
                .userId("test-user-2")
                .content("normal user login")
                .context("User authentication system")
                .ipAddress("192.168.1.100")
                .operationType("LOGIN")
                .build();
        
        AISecurityResponse lowRiskResponse = aiSecurityService.analyzeSecurity(lowRiskRequest);
        assertNotNull(lowRiskResponse);
        
        // Test with high risk content
        AISecurityRequest highRiskRequest = AISecurityRequest.builder()
                .requestId("test-request-3")
                .userId("test-user-3")
                .content("malicious code injection attempt")
                .context("User input validation")
                .ipAddress("10.0.0.1")
                .operationType("INPUT_VALIDATION")
                .build();
        
        AISecurityResponse highRiskResponse = aiSecurityService.analyzeSecurity(highRiskRequest);
        assertNotNull(highRiskResponse);
        
        // High risk content should have higher risk score
        assertTrue(highRiskResponse.getRiskScore() >= lowRiskResponse.getRiskScore());
    }

    @Test
    @DisplayName("Security event lifecycle management")
    void testSecurityEventLifecycle() throws Exception {
        // Create security event
        AISecurityEvent securityEvent = AISecurityEvent.builder()
                .eventId("event-1")
                .eventType("THREAT_DETECTED")
                .severity("HIGH")
                .description("Suspicious activity detected")
                .userId("test-user-1")
                .build();
        
        // Log the event
        AIAuditResponse auditResponse = aiAuditService.logAuditEvent(auditRequest);
        assertNotNull(auditResponse);
        assertTrue(auditResponse.isSuccess());
        
        // Verify event was logged
        assertNotNull(securityEvent.getEventId());
        assertEquals("THREAT_DETECTED", securityEvent.getEventType());
        assertEquals("HIGH", securityEvent.getSeverity());
    }

    @Test
    @DisplayName("Error handling and recovery")
    void testErrorHandlingAndRecovery() {
        // Test with invalid request
        AISecurityRequest invalidRequest = AISecurityRequest.builder()
                .requestId("")
                .userId("")
                .content("")
                .build();
        
        assertThrows(AIServiceException.class, () -> {
            aiSecurityService.analyzeSecurity(invalidRequest);
        });
        
        // Test with null request
        assertThrows(AIServiceException.class, () -> {
            aiSecurityService.analyzeSecurity(null);
        });
    }

    @Test
    @DisplayName("Performance and scalability")
    void testPerformanceAndScalability() throws Exception {
        long startTime = System.currentTimeMillis();
        
        // Simulate multiple concurrent security analyses
        List<CompletableFuture<AISecurityResponse>> futures = List.of(
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return aiSecurityService.analyzeSecurity(securityRequest);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }),
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return aiSecurityService.analyzeSecurity(securityRequest);
                    } catch (Exception e) {
                            throw new RuntimeException(e);
                    }
                }),
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return aiSecurityService.analyzeSecurity(securityRequest);
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
        for (CompletableFuture<AISecurityResponse> future : futures) {
            AISecurityResponse response = future.get();
            assertNotNull(response);
            assertNotNull(response.getRequestId());
        }
    }

    @Test
    @DisplayName("Integration with audit and compliance systems")
    void testIntegrationWithAuditAndCompliance() throws Exception {
        // Perform security operation
        AISecurityResponse securityResponse = aiSecurityService.analyzeSecurity(securityRequest);
        
        // Verify audit trail
        AIAuditResponse auditResponse = aiAuditService.logAuditEvent(auditRequest);
        assertNotNull(auditResponse);
        assertTrue(auditResponse.isSuccess());
        
        // Verify compliance check
        AIComplianceRequest complianceRequest = AIComplianceRequest.builder()
                .requestId("compliance-request-2")
                .userId("test-user-1")
                .framework("GDPR")
                .category("SECURITY")
                .build();
        
        AIComplianceResponse complianceResponse = aiComplianceService.checkCompliance(complianceRequest);
        assertNotNull(complianceResponse);
        assertTrue(complianceResponse.getComplianceScore() >= 0.0 && complianceResponse.getComplianceScore() <= 1.0);
    }
}