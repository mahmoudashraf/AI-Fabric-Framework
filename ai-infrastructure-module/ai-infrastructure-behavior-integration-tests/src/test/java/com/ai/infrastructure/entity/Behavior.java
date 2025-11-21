package com.ai.infrastructure.entity;

import com.ai.infrastructure.annotation.AICapable;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Behavior Entity
 * 
 * Generic behavioral data entity for AI analysis and insights.
 * This entity captures user interactions, preferences, and patterns
 * to enable AI-powered behavioral analysis and recommendations.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Entity
@Table(name = "behaviors")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@AICapable(
    entityType = "behavior",
    features = {"embedding", "search", "analysis", "behavioral"},
    enableSearch = true,
    enableRecommendations = false,
    autoEmbedding = true,
    indexable = true
)
public class Behavior {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @NotNull(message = "User ID is required")
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "behavior_type", nullable = false)
    private BehaviorType behaviorType;
    
    @Column(name = "entity_type")
    private String entityType; // product, order, page, etc.
    
    @Column(name = "entity_id")
    private String entityId;
    
    @Column(name = "action")
    private String action;
    
    @Column(name = "context")
    private String context;
    
    @Column(name = "metadata")
    private String metadata;
    
    @Column(name = "session_id")
    private String sessionId;
    
    @Column(name = "device_info")
    private String deviceInfo;
    
    @Column(name = "location_info")
    private String locationInfo;
    
    @Column(name = "duration_seconds")
    private Long durationSeconds;
    
    @Column(name = "behavior_value")
    private String value;
    
    @Column(name = "search_vector")
    private String searchVector;
    
    // AI Analysis fields
    @Column(name = "ai_analysis")
    private String aiAnalysis;
    
    @Column(name = "ai_insights")
    private String aiInsights;
    
    @Column(name = "behavior_score")
    private Double behaviorScore;
    
    @Column(name = "significance_score")
    private Double significanceScore;
    
    @Column(name = "pattern_flags")
    private String patternFlags;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    public enum BehaviorType {
        VIEW,
        CLICK,
        SEARCH,
        ADD_TO_CART,
        REMOVE_FROM_CART,
        PURCHASE,
        WISHLIST,
        SHARE,
        REVIEW,
        RATING,
        NAVIGATION,
        SESSION_START,
        SESSION_END,
        PAGE_VIEW,
        PRODUCT_VIEW,
        CATEGORY_VIEW,
        BRAND_VIEW,
        PRICE_FILTER,
        SORT_CHANGE,
        FILTER_APPLIED,
        SEARCH_QUERY,
        RECOMMENDATION_CLICK,
        RECOMMENDATION_VIEW,
        CART_ABANDONMENT,
        CHECKOUT_START,
        CHECKOUT_COMPLETE,
        PAYMENT_SUCCESS,
        PAYMENT_FAILED,
        REFUND_REQUEST,
        RETURN_REQUEST,
        CUSTOMER_SUPPORT,
        FEEDBACK,
        SURVEY_RESPONSE,
        EMAIL_OPEN,
        EMAIL_CLICK,
        PUSH_NOTIFICATION,
        SMS_RECEIVED,
        APP_OPEN,
        APP_CLOSE,
        FEATURE_USAGE,
        ERROR_ENCOUNTERED,
        HELP_REQUEST,
        TUTORIAL_COMPLETED,
        ONBOARDING_STEP,
        PREFERENCE_CHANGE,
        SETTING_UPDATE,
        PROFILE_UPDATE,
        ADDRESS_CHANGE,
        PAYMENT_METHOD_CHANGE,
        SUBSCRIPTION_CHANGE,
        NOTIFICATION_PREFERENCE,
        PRIVACY_SETTING,
        SECURITY_ACTION,
        AUTHENTICATION,
        LOGIN,
        LOGOUT,
        PASSWORD_CHANGE,
        EMAIL_VERIFICATION,
        PHONE_VERIFICATION,
        TWO_FACTOR_AUTH,
        ACCOUNT_LOCKED,
        ACCOUNT_UNLOCKED,
        SUSPICIOUS_ACTIVITY,
        FRAUD_DETECTED,
        RISK_ASSESSMENT,
        COMPLIANCE_CHECK,
        AUDIT_LOG,
        SYSTEM_EVENT,
        MAINTENANCE,
        UPDATE_NOTIFICATION,
        FEATURE_ANNOUNCEMENT,
        PROMOTION_VIEW,
        PROMOTION_CLICK,
        COUPON_USE,
        LOYALTY_POINTS,
        REWARD_REDEMPTION,
        REFERRAL_SENT,
        REFERRAL_RECEIVED,
        SOCIAL_SHARE,
        SOCIAL_LOGIN,
        THIRD_PARTY_INTEGRATION,
        API_CALL,
        WEBHOOK_RECEIVED,
        DATA_EXPORT,
        DATA_IMPORT,
        BACKUP_CREATED,
        RESTORE_PERFORMED,
        MIGRATION_COMPLETED,
        UPGRADE_PERFORMED,
        CONFIGURATION_CHANGE,
        PERMISSION_CHANGE,
        ROLE_ASSIGNMENT,
        ACCESS_GRANTED,
        ACCESS_REVOKED,
        SESSION_TIMEOUT,
        RATE_LIMIT_EXCEEDED,
        QUOTA_EXCEEDED,
        STORAGE_FULL,
        PERFORMANCE_ISSUE,
        CONNECTIVITY_ISSUE,
        SERVICE_UNAVAILABLE,
        MAINTENANCE_MODE,
        EMERGENCY_SHUTDOWN,
        RECOVERY_STARTED,
        RECOVERY_COMPLETED,
        HEALTH_CHECK,
        MONITORING_ALERT,
        SECURITY_ALERT,
        COMPLIANCE_ALERT,
        BUSINESS_ALERT,
        TECHNICAL_ALERT,
        USER_ALERT,
        SYSTEM_ALERT,
        CUSTOM_ALERT,
        UNKNOWN
    }
    
    // Helper methods for AI operations
    
    /**
     * Get the primary searchable text for AI operations
     */
    public String getSearchableText() {
        StringBuilder text = new StringBuilder();
        text.append(behaviorType.toString()).append(" ");
        if (entityType != null) {
            text.append(entityType).append(" ");
        }
        if (entityId != null) {
            text.append(entityId).append(" ");
        }
        if (action != null) {
            text.append(action).append(" ");
        }
        if (context != null) {
            text.append(context).append(" ");
        }
        if (metadata != null) {
            text.append(metadata).append(" ");
        }
        if (value != null) {
            text.append(value).append(" ");
        }
        if (aiAnalysis != null) {
            text.append(aiAnalysis).append(" ");
        }
        if (aiInsights != null) {
            text.append(aiInsights).append(" ");
        }
        return text.toString().trim();
    }
    
    /**
     * Get metadata for AI operations
     */
    public Map<String, Object> getAIMetadata() {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("id", id.toString());
        metadata.put("userId", userId.toString());
        metadata.put("behaviorType", behaviorType.toString());
        metadata.put("entityType", entityType != null ? entityType : "");
        metadata.put("entityId", entityId != null ? entityId : "");
        metadata.put("action", action != null ? action : "");
        metadata.put("durationSeconds", durationSeconds != null ? durationSeconds : 0L);
        metadata.put("behaviorScore", behaviorScore != null ? behaviorScore : 0.0);
        metadata.put("significanceScore", significanceScore != null ? significanceScore : 0.0);
        metadata.put("patternFlags", patternFlags != null ? patternFlags : "");
        metadata.put("createdAt", createdAt.toString());
        return metadata;
    }
}