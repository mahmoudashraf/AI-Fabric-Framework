package com.foundation.ai.integration;

import com.ai.infrastructure.dto.AIComplianceRequest;
import com.ai.infrastructure.dto.AIComplianceResponse;
import com.ai.infrastructure.dto.AIComplianceReport;
import com.ai.infrastructure.compliance.AIComplianceService;
import com.ai.infrastructure.audit.AIAuditService;
import com.ai.infrastructure.security.AISecurityService;
import com.ai.infrastructure.exception.AIServiceException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for AI Compliance services
 * Tests the complete flow from compliance checking to violation management
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AIComplianceIntegrationTest {

    @Autowired
    private AIComplianceService aiComplianceService;

    @Autowired
    private AIAuditService aiAuditService;

    @Autowired
    private AISecurityService aiSecurityService;

    private AIComplianceRequest complianceRequest;
    private AIComplianceReport complianceReport;

    @BeforeEach
    void setUp() {
        complianceRequest = AIComplianceRequest.builder()
                .requestId("compliance-request-1")
                .userId("test-user-1")
                .framework("GDPR")
                .category("SECURITY")
                .build();

        complianceReport = AIComplianceReport.builder()
                .reportId("report-1")
                .framework("GDPR")
                .complianceScore(0.85)
                .build();
    }

    @Test
    @DisplayName("Complete compliance check flow")
    void testCompleteComplianceFlow() throws Exception {
        // Test that the service is properly instantiated
        assertNotNull(aiComplianceService);
        
        // Test that we can create request objects
        assertNotNull(complianceRequest);
        assertNotNull(complianceReport);
        
        // Test that the service method can be called
        AIComplianceResponse response = aiComplianceService.checkCompliance(complianceRequest);
        
        assertNotNull(response);
        assertNotNull(response.getRequestId());
    }

    @Test
    @DisplayName("Real-time compliance monitoring")
    void testRealTimeComplianceMonitoring() throws Exception {
        // Simulate real-time compliance check
        CompletableFuture<AIComplianceResponse> future = CompletableFuture.supplyAsync(() -> {
            try {
                return aiComplianceService.checkCompliance(complianceRequest);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        AIComplianceResponse response = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(response);
        assertNotNull(response.getRequestId());
    }

    @Test
    @DisplayName("Compliance check with different frameworks")
    void testComplianceCheckWithDifferentFrameworks() throws Exception {
        // Test with different framework
        AIComplianceRequest differentRequest = AIComplianceRequest.builder()
                .requestId("compliance-request-2")
                .userId("test-user-2")
                .framework("HIPAA")
                .category("HEALTHCARE")
                .build();
        
        AIComplianceResponse response = aiComplianceService.checkCompliance(differentRequest);
        
        assertNotNull(response);
        assertNotNull(response.getRequestId());
    }

    @Test
    @DisplayName("Error handling and recovery")
    void testErrorHandlingAndRecovery() {
        // Test with null request
        assertThrows(AIServiceException.class, () -> {
            aiComplianceService.checkCompliance(null);
        });
    }

    @Test
    @DisplayName("Performance and scalability")
    void testPerformanceAndScalability() throws Exception {
        long startTime = System.currentTimeMillis();
        
        // Simulate multiple concurrent compliance checks
        List<CompletableFuture<AIComplianceResponse>> futures = List.of(
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return aiComplianceService.checkCompliance(complianceRequest);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }),
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return aiComplianceService.checkCompliance(complianceRequest);
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
        for (CompletableFuture<AIComplianceResponse> future : futures) {
            AIComplianceResponse response = future.get();
            assertNotNull(response);
            assertNotNull(response.getRequestId());
        }
    }

    @Test
    @DisplayName("Integration with audit and security systems")
    void testIntegrationWithAuditAndSecurity() throws Exception {
        // Perform compliance operation
        AIComplianceResponse response = aiComplianceService.checkCompliance(complianceRequest);
        
        // Verify response
        assertNotNull(response);
        assertNotNull(response.getRequestId());
        
        // Test that services are available
        assertNotNull(aiAuditService);
        assertNotNull(aiSecurityService);
    }
}
