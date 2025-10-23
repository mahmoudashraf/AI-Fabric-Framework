package com.ai.infrastructure.compliance;

import com.ai.infrastructure.dto.AIComplianceReport;
import com.ai.infrastructure.dto.AIComplianceRequest;
import com.ai.infrastructure.dto.AIComplianceResponse;
import com.ai.infrastructure.dto.AIAuditLog;
import com.ai.infrastructure.core.AICoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * AI Compliance Service for regulatory compliance and audit logging
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIComplianceService {

    private final AICoreService aiCoreService;
    private final Map<String, AIAuditLog> auditLogs = new ConcurrentHashMap<>();
    private final Map<String, List<AIComplianceReport>> complianceReports = new ConcurrentHashMap<>();
    private final AtomicLong logCounter = new AtomicLong(0);

    /**
     * Check compliance for a request
     */
    public AIComplianceResponse checkCompliance(AIComplianceRequest request) {
        log.info("Checking compliance for request: {}", request.getRequestId());
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Check data privacy compliance
            boolean dataPrivacyCompliant = checkDataPrivacyCompliance(request);
            
            // Check regulatory compliance
            boolean regulatoryCompliant = checkRegulatoryCompliance(request);
            
            // Check audit requirements
            boolean auditCompliant = checkAuditRequirements(request);
            
            // Check data retention compliance
            boolean retentionCompliant = checkDataRetentionCompliance(request);
            
            // Generate compliance report
            AIComplianceReport report = generateComplianceReport(request, dataPrivacyCompliant, 
                regulatoryCompliant, auditCompliant, retentionCompliant);
            
            // Log audit event
            logAuditEvent(request, report);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            return AIComplianceResponse.builder()
                .requestId(request.getRequestId())
                .userId(request.getUserId())
                .dataPrivacyCompliant(dataPrivacyCompliant)
                .regulatoryCompliant(regulatoryCompliant)
                .auditCompliant(auditCompliant)
                .retentionCompliant(retentionCompliant)
                .overallCompliant(dataPrivacyCompliant && regulatoryCompliant && auditCompliant && retentionCompliant)
                .complianceScore(calculateComplianceScore(dataPrivacyCompliant, regulatoryCompliant, 
                    auditCompliant, retentionCompliant))
                .violations(identifyViolations(dataPrivacyCompliant, regulatoryCompliant, 
                    auditCompliant, retentionCompliant))
                .recommendations(generateComplianceRecommendations(report))
                .report(report)
                .processingTimeMs(processingTime)
                .timestamp(LocalDateTime.now())
                .success(true)
                .build();
                
        } catch (Exception e) {
            log.error("Error checking compliance", e);
            return AIComplianceResponse.builder()
                .requestId(request.getRequestId())
                .userId(request.getUserId())
                .overallCompliant(false)
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Check data privacy compliance (GDPR, CCPA, etc.)
     */
    private boolean checkDataPrivacyCompliance(AIComplianceRequest request) {
        // Check if personal data is being processed
        if (containsPersonalData(request.getContent())) {
            // Check for consent
            if (!hasValidConsent(request)) {
                return false;
            }
            
            // Check for data minimization
            if (!isDataMinimized(request)) {
                return false;
            }
            
            // Check for purpose limitation
            if (!isPurposeLimited(request)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Check regulatory compliance (SOX, HIPAA, PCI-DSS, etc.)
     */
    private boolean checkRegulatoryCompliance(AIComplianceRequest request) {
        // Check SOX compliance
        if (request.getRegulationTypes().contains("SOX")) {
            if (!isSOXCompliant(request)) {
                return false;
            }
        }
        
        // Check HIPAA compliance
        if (request.getRegulationTypes().contains("HIPAA")) {
            if (!isHIPAACompliant(request)) {
                return false;
            }
        }
        
        // Check PCI-DSS compliance
        if (request.getRegulationTypes().contains("PCI-DSS")) {
            if (!isPCIDSSCompliant(request)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Check audit requirements
     */
    private boolean checkAuditRequirements(AIComplianceRequest request) {
        // Check if audit logging is enabled
        if (!request.isAuditLoggingEnabled()) {
            return false;
        }
        
        // Check if required audit fields are present
        if (request.getUserId() == null || request.getRequestId() == null) {
            return false;
        }
        
        // Check if audit trail is complete
        if (!isAuditTrailComplete(request)) {
            return false;
        }
        
        return true;
    }

    /**
     * Check data retention compliance
     */
    private boolean checkDataRetentionCompliance(AIComplianceRequest request) {
        // Check if data retention policy is followed
        if (request.getDataRetentionPeriod() != null) {
            if (!isDataRetentionPolicyFollowed(request)) {
                return false;
            }
        }
        
        // Check if data is properly classified
        if (!isDataProperlyClassified(request)) {
            return false;
        }
        
        return true;
    }

    /**
     * Check if content contains personal data
     */
    private boolean containsPersonalData(String content) {
        if (content == null) return false;
        
        String[] personalDataPatterns = {
            "email", "phone", "address", "ssn", "social security",
            "credit card", "passport", "driver's license", "date of birth"
        };
        
        String lowerContent = content.toLowerCase();
        return Arrays.stream(personalDataPatterns)
            .anyMatch(lowerContent::contains);
    }

    /**
     * Check if user has valid consent
     */
    private boolean hasValidConsent(AIComplianceRequest request) {
        // Simplified: check if consent is explicitly given
        return request.isConsentGiven() && request.getConsentTimestamp() != null;
    }

    /**
     * Check if data is minimized
     */
    private boolean isDataMinimized(AIComplianceRequest request) {
        // Check if only necessary data is being processed
        if (request.getContent() != null && request.getContent().length() > 10000) {
            return false; // Too much data
        }
        
        return true;
    }

    /**
     * Check if purpose is limited
     */
    private boolean isPurposeLimited(AIComplianceRequest request) {
        // Check if the purpose is clearly defined and limited
        return request.getPurpose() != null && !request.getPurpose().trim().isEmpty();
    }

    /**
     * Check SOX compliance
     */
    private boolean isSOXCompliant(AIComplianceRequest request) {
        // Check for financial data handling
        if (containsFinancialData(request.getContent())) {
            // Ensure proper controls are in place
            return request.isInternalControlsEnabled() && request.isAuditLoggingEnabled();
        }
        
        return true;
    }

    /**
     * Check HIPAA compliance
     */
    private boolean isHIPAACompliant(AIComplianceRequest request) {
        // Check for health information
        if (containsHealthInformation(request.getContent())) {
            // Ensure proper safeguards are in place
            return request.isDataEncryptionEnabled() && request.isAccessControlEnabled();
        }
        
        return true;
    }

    /**
     * Check PCI-DSS compliance
     */
    private boolean isPCIDSSCompliant(AIComplianceRequest request) {
        // Check for payment card data
        if (containsPaymentCardData(request.getContent())) {
            // Ensure proper security measures are in place
            return request.isDataEncryptionEnabled() && request.isNetworkSecurityEnabled();
        }
        
        return true;
    }

    /**
     * Check if audit trail is complete
     */
    private boolean isAuditTrailComplete(AIComplianceRequest request) {
        // Check if all required audit fields are present
        return request.getUserId() != null && 
               request.getRequestId() != null && 
               request.getTimestamp() != null &&
               request.getOperationType() != null;
    }

    /**
     * Check if data retention policy is followed
     */
    private boolean isDataRetentionPolicyFollowed(AIComplianceRequest request) {
        // Check if data age is within retention period
        if (request.getDataAge() != null) {
            return request.getDataAge() <= request.getDataRetentionPeriod();
        }
        
        return true;
    }

    /**
     * Check if data is properly classified
     */
    private boolean isDataProperlyClassified(AIComplianceRequest request) {
        // Check if data classification is present
        return request.getDataClassification() != null && 
               !request.getDataClassification().trim().isEmpty();
    }

    /**
     * Check if content contains financial data
     */
    private boolean containsFinancialData(String content) {
        if (content == null) return false;
        
        String[] financialPatterns = {
            "revenue", "profit", "loss", "balance sheet", "income statement",
            "cash flow", "assets", "liabilities", "equity"
        };
        
        String lowerContent = content.toLowerCase();
        return Arrays.stream(financialPatterns)
            .anyMatch(lowerContent::contains);
    }

    /**
     * Check if content contains health information
     */
    private boolean containsHealthInformation(String content) {
        if (content == null) return false;
        
        String[] healthPatterns = {
            "medical", "health", "diagnosis", "treatment", "patient",
            "doctor", "hospital", "medication", "symptoms"
        };
        
        String lowerContent = content.toLowerCase();
        return Arrays.stream(healthPatterns)
            .anyMatch(lowerContent::contains);
    }

    /**
     * Check if content contains payment card data
     */
    private boolean containsPaymentCardData(String content) {
        if (content == null) return false;
        
        // Check for credit card number patterns
        return content.matches(".*\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b.*");
    }

    /**
     * Generate compliance report
     */
    private AIComplianceReport generateComplianceReport(AIComplianceRequest request, 
                                                       boolean dataPrivacyCompliant,
                                                       boolean regulatoryCompliant,
                                                       boolean auditCompliant,
                                                       boolean retentionCompliant) {
        
        return AIComplianceReport.builder()
            .reportId("COMP_" + System.currentTimeMillis())
            .requestId(request.getRequestId())
            .userId(request.getUserId())
            .timestamp(LocalDateTime.now())
            .dataPrivacyCompliant(dataPrivacyCompliant)
            .regulatoryCompliant(regulatoryCompliant)
            .auditCompliant(auditCompliant)
            .retentionCompliant(retentionCompliant)
            .overallCompliant(dataPrivacyCompliant && regulatoryCompliant && auditCompliant && retentionCompliant)
            .complianceScore(calculateComplianceScore(dataPrivacyCompliant, regulatoryCompliant, 
                auditCompliant, retentionCompliant))
            .violations(identifyViolations(dataPrivacyCompliant, regulatoryCompliant, 
                auditCompliant, retentionCompliant))
            .recommendations(generateComplianceRecommendations(dataPrivacyCompliant, regulatoryCompliant, 
                auditCompliant, retentionCompliant))
            .regulationTypes(request.getRegulationTypes())
            .dataClassification(request.getDataClassification())
            .purpose(request.getPurpose())
            .build();
    }

    /**
     * Calculate compliance score
     */
    private double calculateComplianceScore(boolean dataPrivacyCompliant, boolean regulatoryCompliant,
                                          boolean auditCompliant, boolean retentionCompliant) {
        int compliantCount = 0;
        if (dataPrivacyCompliant) compliantCount++;
        if (regulatoryCompliant) compliantCount++;
        if (auditCompliant) compliantCount++;
        if (retentionCompliant) compliantCount++;
        
        return (compliantCount / 4.0) * 100.0;
    }

    /**
     * Identify compliance violations
     */
    private List<String> identifyViolations(boolean dataPrivacyCompliant, boolean regulatoryCompliant,
                                          boolean auditCompliant, boolean retentionCompliant) {
        List<String> violations = new ArrayList<>();
        
        if (!dataPrivacyCompliant) {
            violations.add("DATA_PRIVACY_VIOLATION");
        }
        
        if (!regulatoryCompliant) {
            violations.add("REGULATORY_VIOLATION");
        }
        
        if (!auditCompliant) {
            violations.add("AUDIT_VIOLATION");
        }
        
        if (!retentionCompliant) {
            violations.add("RETENTION_VIOLATION");
        }
        
        return violations;
    }

    /**
     * Generate compliance recommendations
     */
    private List<String> generateComplianceRecommendations(AIComplianceReport report) {
        List<String> recommendations = new ArrayList<>();
        
        if (!report.isDataPrivacyCompliant()) {
            recommendations.add("Implement data privacy controls and consent management");
        }
        
        if (!report.isRegulatoryCompliant()) {
            recommendations.add("Review and implement regulatory compliance measures");
        }
        
        if (!report.isAuditCompliant()) {
            recommendations.add("Enable comprehensive audit logging and monitoring");
        }
        
        if (!report.isRetentionCompliant()) {
            recommendations.add("Implement data retention policies and classification");
        }
        
        if (report.getComplianceScore() < 75.0) {
            recommendations.add("Conduct comprehensive compliance review");
        }
        
        return recommendations;
    }

    /**
     * Generate compliance recommendations based on boolean flags
     */
    private List<String> generateComplianceRecommendations(boolean dataPrivacyCompliant, 
                                                          boolean regulatoryCompliant,
                                                          boolean auditCompliant, 
                                                          boolean retentionCompliant) {
        List<String> recommendations = new ArrayList<>();
        
        if (!dataPrivacyCompliant) {
            recommendations.add("Implement data privacy controls and consent management");
        }
        
        if (!regulatoryCompliant) {
            recommendations.add("Review and implement regulatory compliance measures");
        }
        
        if (!auditCompliant) {
            recommendations.add("Enable comprehensive audit logging and monitoring");
        }
        
        if (!retentionCompliant) {
            recommendations.add("Implement data retention policies and classification");
        }
        
        return recommendations;
    }

    /**
     * Log audit event
     */
    private void logAuditEvent(AIComplianceRequest request, AIComplianceReport report) {
        AIAuditLog auditLog = AIAuditLog.builder()
            .logId("AUDIT_" + logCounter.incrementAndGet())
            .requestId(request.getRequestId())
            .userId(request.getUserId())
            .operationType(request.getOperationType())
            .timestamp(LocalDateTime.now())
            .ipAddress(request.getIpAddress())
            .userAgent(request.getUserAgent())
            .complianceStatus(report.isOverallCompliant() ? "COMPLIANT" : "NON_COMPLIANT")
            .violations(report.getViolations())
            .dataClassification(request.getDataClassification())
            .purpose(request.getPurpose())
            .regulationTypes(request.getRegulationTypes())
            .build();
        
        auditLogs.put(request.getRequestId(), auditLog);
        
        // Store compliance report
        complianceReports.computeIfAbsent(request.getUserId(), k -> new ArrayList<>()).add(report);
        
        log.info("Audit event logged: {} for user: {}", auditLog.getOperationType(), request.getUserId());
    }

    /**
     * Get audit logs for a user
     */
    public List<AIAuditLog> getAuditLogs(String userId) {
        return auditLogs.values().stream()
            .filter(log -> userId.equals(log.getUserId()))
            .sorted((l1, l2) -> l2.getTimestamp().compareTo(l1.getTimestamp()))
            .collect(Collectors.toList());
    }

    /**
     * Get all audit logs
     */
    public List<AIAuditLog> getAllAuditLogs() {
        return auditLogs.values().stream()
            .sorted((l1, l2) -> l2.getTimestamp().compareTo(l1.getTimestamp()))
            .collect(Collectors.toList());
    }

    /**
     * Get compliance reports for a user
     */
    public List<AIComplianceReport> getComplianceReports(String userId) {
        return complianceReports.getOrDefault(userId, Collections.emptyList());
    }

    /**
     * Get compliance statistics
     */
    public Map<String, Object> getComplianceStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalReports = complianceReports.values().stream()
            .mapToLong(List::size)
            .sum();
        
        long compliantReports = complianceReports.values().stream()
            .flatMap(List::stream)
            .mapToLong(report -> report.isOverallCompliant() ? 1 : 0)
            .sum();
        
        stats.put("totalReports", totalReports);
        stats.put("compliantReports", compliantReports);
        stats.put("complianceRate", totalReports > 0 ? (double) compliantReports / totalReports : 0.0);
        stats.put("totalAuditLogs", auditLogs.size());
        
        return stats;
    }

    /**
     * Generate compliance summary report
     */
    public AIComplianceReport generateSummaryReport(String userId, String period) {
        List<AIComplianceReport> userReports = getComplianceReports(userId);
        
        if (userReports.isEmpty()) {
            return AIComplianceReport.builder()
                .reportId("SUMMARY_" + System.currentTimeMillis())
                .userId(userId)
                .timestamp(LocalDateTime.now())
                .overallCompliant(true)
                .complianceScore(100.0)
                .violations(Collections.emptyList())
                .recommendations(Collections.emptyList())
                .build();
        }
        
        // Calculate average compliance score
        double avgScore = userReports.stream()
            .mapToDouble(AIComplianceReport::getComplianceScore)
            .average()
            .orElse(0.0);
        
        // Count violations
        long totalViolations = userReports.stream()
            .mapToLong(report -> report.getViolations().size())
            .sum();
        
        // Generate recommendations
        List<String> recommendations = generateSummaryRecommendations(userReports, avgScore);
        
        return AIComplianceReport.builder()
            .reportId("SUMMARY_" + System.currentTimeMillis())
            .userId(userId)
            .timestamp(LocalDateTime.now())
            .overallCompliant(avgScore >= 75.0)
            .complianceScore(avgScore)
            .violations(totalViolations > 0 ? List.of("MULTIPLE_VIOLATIONS") : Collections.emptyList())
            .recommendations(recommendations)
            .build();
    }

    /**
     * Generate summary recommendations
     */
    private List<String> generateSummaryRecommendations(List<AIComplianceReport> reports, double avgScore) {
        List<String> recommendations = new ArrayList<>();
        
        if (avgScore < 75.0) {
            recommendations.add("Overall compliance score is below acceptable threshold");
        }
        
        if (avgScore < 50.0) {
            recommendations.add("Immediate compliance review and remediation required");
        }
        
        // Check for common violations
        long dataPrivacyViolations = reports.stream()
            .mapToLong(r -> r.getViolations().contains("DATA_PRIVACY_VIOLATION") ? 1 : 0)
            .sum();
        
        if (dataPrivacyViolations > 0) {
            recommendations.add("Address data privacy compliance issues");
        }
        
        return recommendations;
    }
}