package com.ai.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for AI Content Filter Requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIContentFilterRequest {
    
    /**
     * Request ID
     */
    private String requestId;
    
    /**
     * User ID
     */
    private String userId;
    
    /**
     * Content to filter
     */
    private String content;
    
    /**
     * Request context
     */
    private String context;
    
    /**
     * Filter type
     */
    private String filterType;
    
    /**
     * Filter level
     */
    private String filterLevel;
    
    /**
     * Maximum violations allowed
     */
    private Integer maxViolations;
    
    /**
     * Minimum content score
     */
    private Double minContentScore;
    
    /**
     * Filter categories
     */
    private List<String> filterCategories;
    
    /**
     * Blocked content patterns
     */
    private List<String> blockedPatterns;
    
    /**
     * Allowed content patterns
     */
    private List<String> allowedPatterns;
    
    /**
     * Custom filter rules
     */
    private List<String> customRules;
    
    /**
     * Request metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Request timestamp
     */
    private LocalDateTime timestamp;
    
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