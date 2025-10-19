package com.ai.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for AI Audit Logs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIAuditLog {
    
    /**
     * Log ID
     */
    private String logId;
    
    /**
     * Request ID
     */
    private String requestId;
    
    /**
     * User ID
     */
    private String userId;
    
    /**
     * Operation type
     */
    private String operationType;
    
    /**
     * Log timestamp
     */
    private LocalDateTime timestamp;
    
    /**
     * IP address
     */
    private String ipAddress;
    
    /**
     * User agent
     */
    private String userAgent;
    
    /**
     * Session ID
     */
    private String sessionId;
    
    /**
     * Resource type
     */
    private String resourceType;
    
    /**
     * Resource ID
     */
    private String resourceId;
    
    /**
     * Action performed
     */
    private String action;
    
    /**
     * Result of the action
     */
    private String result;
    
    /**
     * Additional details
     */
    private String details;
    
    /**
     * Log metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Risk level
     */
    private String riskLevel;
    
    /**
     * Severity level
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
     * Event confidence score
     */
    private Double confidenceScore;
    
    /**
     * False positive flag
     */
    private Boolean falsePositive;
    
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
    
    /**
     * Has anomalies
     */
    private Boolean hasAnomalies;
    
    /**
     * Audit insights
     */
    private List<String> insights;
}