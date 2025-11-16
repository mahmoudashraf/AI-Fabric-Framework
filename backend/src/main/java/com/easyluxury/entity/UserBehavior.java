package com.easyluxury.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight behavior model used by backend services.
 * Delegates persistence to the ai-behavior module.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehavior {

    private UUID id;
    private UUID userId;
    private BehaviorType behaviorType;
    private String entityType;
    private String entityId;
    private String action;
    private String context;
    private String metadata;
    private String sessionId;
    private String deviceInfo;
    private String locationInfo;
    private Long durationSeconds;
    private String value;
    private String aiAnalysis;
    private String aiInsights;
    private Double behaviorScore;
    private Double significanceScore;
    private String patternFlags;
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

    public String getSearchableText() {
        StringBuilder text = new StringBuilder();
        if (behaviorType != null) {
            text.append(behaviorType).append(" ");
        }
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

    public Map<String, Object> getAIMetadata() {
        Map<String, Object> metadataMap = new java.util.HashMap<>();
        if (id != null) {
            metadataMap.put("id", id.toString());
        }
        if (userId != null) {
            metadataMap.put("userId", userId.toString());
        }
        metadataMap.put("behaviorType", behaviorType != null ? behaviorType.toString() : "");
        metadataMap.put("entityType", entityType != null ? entityType : "");
        metadataMap.put("entityId", entityId != null ? entityId : "");
        metadataMap.put("action", action != null ? action : "");
        metadataMap.put("durationSeconds", durationSeconds != null ? durationSeconds : 0L);
        metadataMap.put("behaviorScore", behaviorScore != null ? behaviorScore : 0.0);
        metadataMap.put("significanceScore", significanceScore != null ? significanceScore : 0.0);
        metadataMap.put("patternFlags", patternFlags != null ? patternFlags : "");
        metadataMap.put("createdAt", createdAt != null ? createdAt.toString() : "");
        return metadataMap;
    }
}
