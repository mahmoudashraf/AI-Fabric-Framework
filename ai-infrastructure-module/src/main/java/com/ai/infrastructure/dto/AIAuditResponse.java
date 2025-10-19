package com.ai.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for AI Audit Responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIAuditResponse {
    
    /**
     * Log ID
     */
    private String logId;
    
    /**
     * User ID
     */
    private String userId;
    
    /**
     * Operation type
     */
    private String operationType;
    
    /**
     * Risk level
     */
    private String riskLevel;
    
    /**
     * Has anomalies
     */
    private Boolean hasAnomalies;
    
    /**
     * Audit insights
     */
    private List<String> insights;
    
    /**
     * Processing time in milliseconds
     */
    private Long processingTimeMs;
    
    /**
     * Response timestamp
     */
    private LocalDateTime timestamp;
    
    /**
     * Whether the response was successful
     */
    private Boolean success;
    
    /**
     * Error message if failed
     */
    private String errorMessage;
    
    /**
     * Audit metrics
     */
    private Map<String, Object> auditMetrics;
    
    /**
     * Security recommendations
     */
    private List<String> securityRecommendations;
    
    /**
     * Compliance recommendations
     */
    private List<String> complianceRecommendations;
    
    /**
     * Risk assessment
     */
    private String riskAssessment;
    
    /**
     * Threat level
     */
    private String threatLevel;
    
    /**
     * Confidence score
     */
    private Double confidenceScore;
    
    /**
     * False positive probability
     */
    private Double falsePositiveProbability;
    
    /**
     * True positive probability
     */
    private Double truePositiveProbability;
    
    /**
     * Event severity
     */
    private String severity;
    
    /**
     * Event category
     */
    private String category;
    
    /**
     * Event source
     */
    private String source;
    
    /**
     * Event hash
     */
    private String eventHash;
    
    /**
     * Related event IDs
     */
    private List<String> relatedEventIds;
    
    /**
     * Event tags
     */
    private List<String> tags;
    
    /**
     * Geographic location
     */
    private String location;
    
    /**
     * Device information
     */
    private String deviceInfo;
    
    /**
     * Event duration in milliseconds
     */
    private Long durationMs;
    
    /**
     * Event impact level
     */
    private String impactLevel;
    
    /**
     * Event escalation level
     */
    private Integer escalationLevel;
    
    /**
     * Event priority
     */
    private String priority;
    
    /**
     * Source IP country
     */
    private String sourceCountry;
    
    /**
     * Source IP city
     */
    private String sourceCity;
    
    /**
     * Source IP organization
     */
    private String sourceOrganization;
    
    /**
     * Source IP ISP
     */
    private String sourceISP;
    
    /**
     * Threat intelligence
     */
    private Map<String, Object> threatIntelligence;
    
    /**
     * Remediation actions
     */
    private List<String> remediationActions;
    
    /**
     * Compliance status
     */
    private String complianceStatus;
    
    /**
     * Data classification
     */
    private String dataClassification;
    
    /**
     * Retention period in days
     */
    private Integer retentionPeriodDays;
    
    /**
     * Archive status
     */
    private Boolean archived;
    
    /**
     * Archive timestamp
     */
    private LocalDateTime archivedAt;
    
    /**
     * Archive reason
     */
    private String archiveReason;
}