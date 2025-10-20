package com.foundation.ai.integration;

import com.foundation.ai.dto.ComplianceCheckRequest;
import com.foundation.ai.dto.ComplianceCheckResponse;
import com.foundation.ai.dto.ComplianceMetricsResponse;
import com.foundation.ai.dto.ComplianceViolationRequest;
import com.foundation.ai.dto.ComplianceViolationResponse;
import com.foundation.ai.dto.ComplianceFrameworkRequest;
import com.foundation.ai.dto.ComplianceFrameworkResponse;
import com.foundation.ai.service.AIComplianceService;
import com.foundation.ai.service.AIAuditService;
import com.foundation.ai.service.AISecurityService;
import com.foundation.ai.exception.AIComplianceException;
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

    private ComplianceCheckRequest complianceCheckRequest;
    private ComplianceViolationRequest violationRequest;
    private ComplianceFrameworkRequest frameworkRequest;

    @BeforeEach
    void setUp() {
        complianceCheckRequest = ComplianceCheckRequest.builder()
                .framework("GDPR")
                .category("DATA_PRIVACY")
                .scope("USER_DATA")
                .enableRealTimeMonitoring(true)
                .enableAutomatedRemediation(true)
                .build();

        violationRequest = ComplianceViolationRequest.builder()
                .title("Data Retention Violation")
                .description("Personal data retained beyond specified period")
                .severity("HIGH")
                .framework("GDPR")
                .category("DATA_RETENTION")
                .affectedData("USER_PROFILES")
                .build();

        frameworkRequest = ComplianceFrameworkRequest.builder()
                .name("GDPR Compliance Framework")
                .description("General Data Protection Regulation compliance framework")
                .version("1.0")
                .applicableRegions(List.of("EU", "UK"))
                .requirements(List.of("DATA_MINIMIZATION", "PURPOSE_LIMITATION", "STORAGE_LIMITATION"))
                .build();
    }

    @Test
    @DisplayName("Complete compliance check and violation management flow")
    void testCompleteComplianceFlow() throws Exception {
        // Step 1: Create compliance framework
        ComplianceFrameworkResponse framework = aiComplianceService.createFramework(frameworkRequest);
        
        assertNotNull(framework);
        assertNotNull(framework.getFrameworkId());
        assertEquals("GDPR Compliance Framework", framework.getName());
        assertEquals("1.0", framework.getVersion());

        // Step 2: Perform compliance check
        ComplianceCheckResponse checkResponse = aiComplianceService.checkCompliance(complianceCheckRequest);
        
        assertNotNull(checkResponse);
        assertNotNull(checkResponse.getViolations());
        assertTrue(checkResponse.getComplianceScore() >= 0.0 && checkResponse.getComplianceScore() <= 1.0);
        assertNotNull(checkResponse.getProcessingTimeMs());
        assertTrue(checkResponse.getProcessingTimeMs() > 0);

        // Step 3: Create violation if found
        if (!checkResponse.getViolations().isEmpty()) {
            ComplianceViolationResponse violation = aiComplianceService.createViolation(violationRequest);
            
            assertNotNull(violation);
            assertNotNull(violation.getViolationId());
            assertEquals("Data Retention Violation", violation.getTitle());
            assertEquals("HIGH", violation.getSeverity());
            assertNotNull(violation.getCreatedAt());
        }

        // Step 4: Verify audit trail
        var auditLogs = aiAuditService.getAuditLogs(0, 10, "timestamp", "desc");
        assertNotNull(auditLogs);
        assertTrue(auditLogs.getLogs().size() > 0);

        // Step 5: Check metrics
        ComplianceMetricsResponse metrics = aiComplianceService.getComplianceMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.getTotalViolations() >= 0);
        assertTrue(metrics.getComplianceScore() >= 0.0 && metrics.getComplianceScore() <= 1.0);
    }

    @Test
    @DisplayName("Real-time compliance monitoring")
    void testRealTimeComplianceMonitoring() throws Exception {
        // Simulate real-time compliance monitoring
        CompletableFuture<ComplianceCheckResponse> future = CompletableFuture.supplyAsync(() -> {
            try {
                return aiComplianceService.checkCompliance(complianceCheckRequest);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ComplianceCheckResponse response = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(response);
        assertTrue(response.getProcessingTimeMs() < 5000); // Should be fast for real-time
        
        // Verify metrics are updated
        ComplianceMetricsResponse metrics = aiComplianceService.getComplianceMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.getTotalViolations() >= 0);
        assertTrue(metrics.getAverageResponseTime() > 0);
    }

    @Test
    @DisplayName("Compliance check with different frameworks")
    void testComplianceCheckWithDifferentFrameworks() throws Exception {
        // Test GDPR compliance
        ComplianceCheckRequest gdprRequest = complianceCheckRequest.toBuilder()
                .framework("GDPR")
                .category("DATA_PRIVACY")
                .build();
        
        ComplianceCheckResponse gdprResponse = aiComplianceService.checkCompliance(gdprRequest);
        assertNotNull(gdprResponse);
        assertEquals("GDPR", gdprResponse.getFramework());
        
        // Test SOX compliance
        ComplianceCheckRequest soxRequest = complianceCheckRequest.toBuilder()
                .framework("SOX")
                .category("FINANCIAL_CONTROLS")
                .build();
        
        ComplianceCheckResponse soxResponse = aiComplianceService.checkCompliance(soxRequest);
        assertNotNull(soxResponse);
        assertEquals("SOX", soxResponse.getFramework());
        
        // Test HIPAA compliance
        ComplianceCheckRequest hipaaRequest = complianceCheckRequest.toBuilder()
                .framework("HIPAA")
                .category("HEALTHCARE_DATA")
                .build();
        
        ComplianceCheckResponse hipaaResponse = aiComplianceService.checkCompliance(hipaaRequest);
        assertNotNull(hipaaResponse);
        assertEquals("HIPAA", hipaaResponse.getFramework());
    }

    @Test
    @DisplayName("Violation lifecycle management")
    void testViolationLifecycleManagement() throws Exception {
        // Create violation
        ComplianceViolationResponse violation = aiComplianceService.createViolation(violationRequest);
        assertNotNull(violation);
        assertNotNull(violation.getViolationId());
        
        // Update violation
        var updateRequest = ComplianceViolationRequest.builder()
                .title("Updated: Data Retention Violation")
                .description("Additional analysis completed")
                .severity("CRITICAL")
                .status("INVESTIGATING")
                .build();
        
        ComplianceViolationResponse updatedViolation = aiComplianceService.updateViolation(
                violation.getViolationId(), updateRequest);
        
        assertNotNull(updatedViolation);
        assertEquals("Updated: Data Retention Violation", updatedViolation.getTitle());
        assertEquals("CRITICAL", updatedViolation.getSeverity());
        
        // Resolve violation
        ComplianceViolationResponse resolvedViolation = aiComplianceService.resolveViolation(violation.getViolationId());
        assertNotNull(resolvedViolation);
        assertEquals("RESOLVED", resolvedViolation.getStatus());
    }

    @Test
    @DisplayName("Framework management")
    void testFrameworkManagement() throws Exception {
        // Create framework
        ComplianceFrameworkResponse framework = aiComplianceService.createFramework(frameworkRequest);
        assertNotNull(framework);
        assertNotNull(framework.getFrameworkId());
        
        // Update framework
        var updateRequest = ComplianceFrameworkRequest.builder()
                .name("Updated GDPR Compliance Framework")
                .description("Updated General Data Protection Regulation compliance framework")
                .version("1.1")
                .applicableRegions(List.of("EU", "UK", "US"))
                .requirements(List.of("DATA_MINIMIZATION", "PURPOSE_LIMITATION", "STORAGE_LIMITATION", "ACCURACY"))
                .build();
        
        ComplianceFrameworkResponse updatedFramework = aiComplianceService.updateFramework(
                framework.getFrameworkId(), updateRequest);
        
        assertNotNull(updatedFramework);
        assertEquals("Updated GDPR Compliance Framework", updatedFramework.getName());
        assertEquals("1.1", updatedFramework.getVersion());
        
        // Delete framework
        aiComplianceService.deleteFramework(framework.getFrameworkId());
        
        // Verify framework is deleted
        assertThrows(AIComplianceException.class, () -> {
            aiComplianceService.getFramework(framework.getFrameworkId());
        });
    }

    @Test
    @DisplayName("Error handling and recovery")
    void testErrorHandlingAndRecovery() {
        // Test with invalid request
        ComplianceCheckRequest invalidRequest = ComplianceCheckRequest.builder()
                .framework("")
                .category("")
                .scope("")
                .build();
        
        assertThrows(AIProcessingException.class, () -> {
            aiComplianceService.checkCompliance(invalidRequest);
        });
        
        // Test with null request
        assertThrows(AIProcessingException.class, () -> {
            aiComplianceService.checkCompliance(null);
        });
    }

    @Test
    @DisplayName("Performance and scalability")
    void testPerformanceAndScalability() throws Exception {
        long startTime = System.currentTimeMillis();
        
        // Simulate multiple concurrent compliance checks
        List<CompletableFuture<ComplianceCheckResponse>> futures = List.of(
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return aiComplianceService.checkCompliance(complianceCheckRequest);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }),
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return aiComplianceService.checkCompliance(complianceCheckRequest);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }),
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return aiComplianceService.checkCompliance(complianceCheckRequest);
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
        for (CompletableFuture<ComplianceCheckResponse> future : futures) {
            ComplianceCheckResponse response = future.get();
            assertNotNull(response);
            assertNotNull(response.getViolations());
        }
    }

    @Test
    @DisplayName("Integration with audit and security systems")
    void testIntegrationWithAuditAndSecurity() throws Exception {
        // Perform compliance operation
        ComplianceCheckResponse complianceResponse = aiComplianceService.checkCompliance(complianceCheckRequest);
        
        // Verify audit trail
        var auditLogs = aiAuditService.getAuditLogs(0, 10, "timestamp", "desc");
        assertNotNull(auditLogs);
        
        // Check if compliance operation was logged
        boolean complianceOperationLogged = auditLogs.getLogs().stream()
                .anyMatch(log -> log.getOperationType().equals("COMPLIANCE_CHECK"));
        assertTrue(complianceOperationLogged);
        
        // Verify security integration
        var securityMetrics = aiSecurityService.getSecurityMetrics();
        assertNotNull(securityMetrics);
        
        // Verify metrics are updated
        ComplianceMetricsResponse metrics = aiComplianceService.getComplianceMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.getTotalViolations() >= 0);
    }
}