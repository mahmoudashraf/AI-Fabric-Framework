package com.ai.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for AI Audit Requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIAuditRequest {
    
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
     * Request timestamp
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
     * Action to perform
     */
    private String action;
    
    /**
     * Request context
     */
    private String context;
    
    /**
     * Request metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Request priority
     */
    private String priority;
    
    /**
     * Request severity
     */
    private String severity;
    
    /**
     * Request category
     */
    private String category;
    
    /**
     * Request tags
     */
    private List<String> tags;
    
    /**
     * Request notes
     */
    private String notes;
    
    /**
     * Request attachments
     */
    private List<String> attachments;
    
    /**
     * Request version
     */
    private String version;
    
    /**
     * Request author
     */
    private String author;
    
    /**
     * Request reviewer
     */
    private String reviewer;
    
    /**
     * Request approver
     */
    private String approver;
    
    /**
     * Request approval date
     */
    private LocalDateTime approvalDate;
    
    /**
     * Request distribution list
     */
    private List<String> distributionList;
    
    /**
     * Request confidentiality level
     */
    private String confidentialityLevel;
    
    /**
     * Request retention period
     */
    private Integer requestRetentionPeriod;
    
    /**
     * Request archive status
     */
    private Boolean archived;
    
    /**
     * Request archive date
     */
    private LocalDateTime archivedDate;
    
    /**
     * Request archive reason
     */
    private String archiveReason;
}