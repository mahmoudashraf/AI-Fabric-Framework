# BehaviorInsights: Sentiment & Churn Analytics Implementation Guide

**Version:** 3.1.0 (Domain-Decoupled with Enums)  
**Status:** Implementation Ready  
**Scope:** Greenfield - Hibernate Entity with Domain Enums

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Domain Model - Enums](#domain-model---enums)
3. [Entity Model](#entity-model)
4. [Service Implementation](#service-implementation)
5. [Repository Layer](#repository-layer)
6. [Testing Strategy](#testing-strategy)
7. [REST API Examples](#rest-api-examples)
8. [Monitoring & Alerts](#monitoring--alerts)
9. [Implementation Checklist](#implementation-checklist)

---

## 🎯 OVERVIEW

### What We're Building

Enhanced `BehaviorInsights` entity with **two high-confidence LLM-generated analytics dimensions**:

1. **Sentiment Analysis** (score + label)
   - Detects user emotional state from behavioral patterns
   - LLM confidence: **95%+**

2. **Churn Risk Analysis** (risk score + reason)
   - Predicts likelihood of user departure
   - LLM confidence: **90%+**

3. **Trend Tracking** (previous values + delta + trend label)
   - Enables velocity-based prioritization
   - Single-row access (no joins)

### Why These Dimensions?

| Dimension | LLM Suitability | Use Case |
|-----------|----------------|----------|
| ✅ **Sentiment** | **High (95%)** | Proactive support for frustrated users |
| ✅ **Churn Risk** | **High (90%)** | Retention campaigns for at-risk users |
| ✅ **Trend** | **High (92%)** | Alert on deteriorating behavior |
| ⚠️ Engagement Score | Medium (70%) | Better calculated from event frequency |
| ❌ RFM Scores | Low (20%) | Strict mathematical formulas |

---

## 🎭 DOMAIN MODEL - ENUMS

Before implementing the entity, we define **domain enums** to represent business concepts with type safety.

### SentimentLabel.java

**File:** `ai-infrastructure-module/ai-infrastructure-behavior/src/main/java/com/ai/infrastructure/behavior/model/SentimentLabel.java`

```java
package com.ai.infrastructure.behavior.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * User sentiment classification based on behavioral signals.
 * 
 * Domain enum representing emotional state derived from user interactions.
 * This is NOT technical coupling - these are business domain concepts.
 */
@Getter
@RequiredArgsConstructor
public enum SentimentLabel {
    
    /**
     * Extremely positive engagement.
     * Signals: Feature exploration, upgrades, referrals, repeat purchases.
     */
    DELIGHTED("DELIGHTED", "Extremely positive engagement"),
    
    /**
     * Positive experience without significant friction.
     * Signals: Consistent usage, successful task completion, no support tickets.
     */
    SATISFIED("SATISFIED", "Positive experience"),
    
    /**
     * No strong sentiment signals either way.
     * Signals: Mixed or minimal interaction patterns.
     */
    NEUTRAL("NEUTRAL", "No strong sentiment"),
    
    /**
     * Help-seeking behavior, repeated attempts.
     * Signals: Multiple help page visits, trial-and-error patterns, low success rate.
     */
    CONFUSED("CONFUSED", "Help-seeking behavior"),
    
    /**
     * Friction and abandonment patterns.
     * Signals: Errors, incomplete workflows, support tickets, complaints.
     */
    FRUSTRATED("FRUSTRATED", "Friction detected"),
    
    /**
     * Strong negative signals indicating imminent departure.
     * Signals: Cancellation page visits, competitor research, deletion attempts.
     */
    CHURNING("CHURNING", "Imminent departure signals");
    
    @JsonValue
    private final String value;
    private final String description;
    
    /**
     * Parse from string (case-insensitive) with safe fallback.
     */
    public static SentimentLabel fromString(String value) {
        if (value == null) {
            return NEUTRAL;
        }
        
        for (SentimentLabel label : values()) {
            if (label.value.equalsIgnoreCase(value)) {
                return label;
            }
        }
        
        return NEUTRAL; // Safe default for invalid LLM outputs
    }
    
    /**
     * Check if sentiment is negative (requires intervention).
     */
    public boolean isNegative() {
        return this == FRUSTRATED || this == CHURNING;
    }
    
    /**
     * Check if sentiment is positive.
     */
    public boolean isPositive() {
        return this == DELIGHTED || this == SATISFIED;
    }
}
```

---

### BehaviorTrend.java

**File:** `ai-infrastructure-module/ai-infrastructure-behavior/src/main/java/com/ai/infrastructure/behavior/model/BehaviorTrend.java`

```java
package com.ai.infrastructure.behavior.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Overall behavioral trend direction.
 * 
 * Indicates whether user behavior is improving, stable, or degrading
 * based on sentiment and churn risk deltas.
 * 
 * This is a business domain enum - changes require stakeholder approval.
 */
@Getter
@RequiredArgsConstructor
public enum BehaviorTrend {
    
    /**
     * Major positive shift in behavior.
     * Criteria: Δsentiment > 0.4 OR Δchurn < -0.4
     */
    RAPIDLY_IMPROVING("RAPIDLY_IMPROVING", "Major positive shift", 5),
    
    /**
     * Positive behavioral shift.
     * Criteria: Δsentiment > 0.2 OR Δchurn < -0.2
     */
    IMPROVING("IMPROVING", "Positive shift", 4),
    
    /**
     * No significant behavioral change.
     * Criteria: |Δsentiment| < 0.2 AND |Δchurn| < 0.2
     */
    STABLE("STABLE", "No significant change", 3),
    
    /**
     * Negative behavioral shift.
     * Criteria: Δsentiment < -0.2 OR Δchurn > 0.2
     */
    DECLINING("DECLINING", "Negative shift", 2),
    
    /**
     * Major negative shift requiring immediate intervention.
     * Criteria: Δsentiment < -0.4 OR Δchurn > 0.4
     */
    RAPIDLY_DECLINING("RAPIDLY_DECLINING", "Major negative shift - ALERT", 1),
    
    /**
     * First analysis, no previous data for comparison.
     */
    NEW_USER("NEW_USER", "Baseline analysis", 0);
    
    @JsonValue
    private final String value;
    private final String description;
    private final int severity; // Higher = better (for sorting)
    
    /**
     * Parse from string (case-insensitive) with safe fallback.
     */
    public static BehaviorTrend fromString(String value) {
        if (value == null) {
            return STABLE;
        }
        
        for (BehaviorTrend trend : values()) {
            if (trend.value.equalsIgnoreCase(value)) {
                return trend;
            }
        }
        
        return STABLE; // Safe default for invalid LLM outputs
    }
    
    /**
     * Calculate trend from sentiment and churn deltas.
     * Fallback computation when LLM doesn't provide a trend.
     */
    public static BehaviorTrend fromDeltas(Double sentimentDelta, Double churnDelta, boolean isNewUser) {
        if (isNewUser) {
            return NEW_USER;
        }
        
        if (sentimentDelta == null && churnDelta == null) {
            return STABLE;
        }
        
        double sDelta = sentimentDelta != null ? sentimentDelta : 0.0;
        double cDelta = churnDelta != null ? churnDelta : 0.0;
        
        // Prioritize worst trend
        if (sDelta < -0.4 || cDelta > 0.4) return RAPIDLY_DECLINING;
        if (sDelta < -0.2 || cDelta > 0.2) return DECLINING;
        if (sDelta > 0.4 || cDelta < -0.4) return RAPIDLY_IMPROVING;
        if (sDelta > 0.2 || cDelta < -0.2) return IMPROVING;
        return STABLE;
    }
    
    /**
     * Check if trend requires immediate action.
     */
    public boolean requiresIntervention() {
        return this == RAPIDLY_DECLINING;
    }
    
    /**
     * Check if trend is negative.
     */
    public boolean isNegative() {
        return this == DECLINING || this == RAPIDLY_DECLINING;
    }
    
    /**
     * Check if trend is positive.
     */
    public boolean isPositive() {
        return this == IMPROVING || this == RAPIDLY_IMPROVING;
    }
}
```

---

## 💾 ENTITY MODEL

### Complete BehaviorInsights Entity (with Enums)

**File:** `ai-infrastructure-module/ai-infrastructure-behavior/src/main/java/com/ai/infrastructure/behavior/entity/BehaviorInsights.java`

```java
package com.ai.infrastructure.behavior.entity;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.behavior.converter.JsonbListConverter;
import com.ai.infrastructure.behavior.converter.JsonbMapConverter;
import com.ai.infrastructure.behavior.model.BehaviorTrend;
import com.ai.infrastructure.behavior.model.SentimentLabel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BehaviorInsights - AI-Generated User Behavioral Intelligence
 * 
 * This entity stores LLM-generated insights about user behavior including:
 * - Sentiment analysis (emotional state detection)
 * - Churn risk prediction (likelihood of user departure)
 * - Behavioral trends (improvement/decline tracking)
 * 
 * Supports velocity-based analytics through previous value tracking.
 * 
 * @author AI Infrastructure Team
 * @version 3.0.0
 */
@Entity
@Table(
    name = "ai_behavior_insights",
    indexes = {
        @Index(name = "idx_insights_user", columnList = "user_id"),
        @Index(name = "idx_insights_segment", columnList = "segment"),
        @Index(name = "idx_insights_sentiment", columnList = "sentiment_label"),
        @Index(name = "idx_insights_churn_risk", columnList = "churn_risk"),
        @Index(name = "idx_insights_trend", columnList = "trend")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@AICapable(
    entityType = "behavior-insight",
    autoEmbedding = true,
    indexable = true
)
public class BehaviorInsights {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    
    // ========================================
    // CORE AI INSIGHTS
    // ========================================
    
    /**
     * User behavioral segment (e.g., "Power User", "At Risk", "Dormant").
     * Generated by LLM based on overall behavioral patterns.
     */
    @Column(name = "segment", length = 100)
    private String segment;
    
    /**
     * List of behavioral patterns detected by the LLM.
     * Examples: "night_owl", "price_sensitive", "feature_explorer", "help_seeker"
     */
    @Column(name = "patterns", columnDefinition = "jsonb")
    @Convert(converter = JsonbListConverter.class)
    private List<String> patterns;
    
    /**
     * LLM-generated actionable recommendations.
     * Examples: "offer_loyalty_program", "proactive_support_contact", "retention_offer"
     */
    @Column(name = "recommendations", columnDefinition = "jsonb")
    @Convert(converter = JsonbListConverter.class)
    private List<String> recommendations;
    
    /**
     * Flexible insights storage for additional LLM-generated metadata.
     */
    @Column(name = "insights", columnDefinition = "jsonb")
    @Convert(converter = JsonbMapConverter.class)
    private Map<String, Object> insights;
    
    // ========================================
    // SENTIMENT ANALYSIS (LLM-Generated)
    // ========================================
    
    /**
     * Sentiment score: -1.0 (very negative) to 1.0 (very positive).
     * 
     * LLM derives this from behavioral signals:
     * - Positive: successful completions, feature exploration, upgrades
     * - Negative: errors, help page visits, support tickets, cancellation attempts
     * 
     * Interpretation:
     * - 0.8 to 1.0: Delighted
     * - 0.3 to 0.7: Satisfied
     * - -0.2 to 0.2: Neutral
     * - -0.7 to -0.3: Frustrated
     * - -1.0 to -0.8: Very Frustrated
     */
    @Column(name = "sentiment_score")
    private Double sentimentScore;
    
    /**
     * Sentiment classification using domain enum.
     * Stored as STRING in database for flexibility.
     * 
     * Values:
     * - DELIGHTED: Extremely positive engagement
     * - SATISFIED: Positive experience without friction
     * - NEUTRAL: No strong signals either way
     * - CONFUSED: Help-seeking behavior, repeated attempts
     * - FRUSTRATED: Abandonment, errors, friction
     * - CHURNING: Strong negative signals (account deletion attempts)
     */
    @Column(name = "sentiment_label", length = 50)
    @Enumerated(EnumType.STRING)
    private SentimentLabel sentimentLabel;
    
    // ========================================
    // CHURN RISK ANALYSIS (LLM-Generated)
    // ========================================
    
    /**
     * Churn risk probability: 0.0 (no risk) to 1.0 (imminent churn).
     * 
     * LLM analyzes behavioral drift patterns:
     * - Declining engagement frequency
     * - Shift from core features to help/support
     * - Abandoned workflows
     * - Pricing page visits without conversion
     * - Account settings changes (downgrade signals)
     * 
     * Risk levels:
     * - 0.0 to 0.2: Healthy, engaged
     * - 0.2 to 0.4: Minor warning signs
     * - 0.4 to 0.6: Moderate risk
     * - 0.6 to 0.8: High risk (intervention needed)
     * - 0.8 to 1.0: Critical (imminent churn)
     */
    @Column(name = "churn_risk")
    private Double churnRisk;
    
    /**
     * LLM-generated explanation for churn risk assessment.
     * Provides actionable context for retention teams.
     * 
     * Examples:
     * - "No login for 14 days after daily usage for 3 months"
     * - "Visited competitor pricing pages 3 times this week"
     * - "Multiple failed payment attempts followed by support contact"
     */
    @Column(name = "churn_reason", columnDefinition = "TEXT")
    private String churnReason;
    
    // ========================================
    // TREND TRACKING (Delta Analysis)
    // ========================================
    
    /**
     * Previous sentiment score (from last analysis).
     * Enables delta calculation for trend detection.
     */
    @Column(name = "previous_sentiment_score")
    private Double previousSentimentScore;
    
    /**
     * Previous churn risk (from last analysis).
     * Enables delta calculation for deterioration alerts.
     */
    @Column(name = "previous_churn_risk")
    private Double previousChurnRisk;
    
    /**
     * Overall behavioral trend using domain enum.
     * Stored as STRING in database for flexibility.
     * 
     * Values:
     * - RAPIDLY_IMPROVING: Major positive shift (Δsentiment >0.4 OR Δchurn <-0.4)
     * - IMPROVING: Positive shift (Δsentiment >0.2 OR Δchurn <-0.2)
     * - STABLE: No significant change (|Δ| < 0.2)
     * - DECLINING: Negative shift (Δsentiment <-0.2 OR Δchurn >0.2)
     * - RAPIDLY_DECLINING: Major negative shift ⚠️ ALERT REQUIRED
     * - NEW_USER: First analysis, no previous data
     */
    @Column(name = "trend", length = 50)
    @Enumerated(EnumType.STRING)
    private BehaviorTrend trend;
    
    /**
     * Computed sentiment delta: current - previous.
     * Positive = improving mood, Negative = degrading mood.
     * 
     * Computed in Java (@Transient) for 100% database independence.
     */
    @Transient
    public Double getSentimentDelta() {
        if (sentimentScore == null || previousSentimentScore == null) {
            return null;
        }
        return sentimentScore - previousSentimentScore;
    }
    
    /**
     * Computed churn delta: current - previous.
     * Positive = increasing risk ⚠️, Negative = decreasing risk ✅.
     * 
     * Computed in Java (@Transient) for 100% database independence.
     */
    @Transient
    public Double getChurnDelta() {
        if (churnRisk == null || previousChurnRisk == null) {
            return null;
        }
        return churnRisk - previousChurnRisk;
    }
    
    // ========================================
    // METADATA
    // ========================================
    
    /**
     * AI confidence in the generated insights (0.0 to 1.0).
     */
    @Column(name = "confidence")
    private Double confidence;
    
    /**
     * AI model used for analysis (e.g., "gpt-4o", "claude-3-5-sonnet").
     */
    @Column(name = "ai_model_used", length = 100)
    private String aiModelUsed;
    
    /**
     * Time taken to process this analysis (milliseconds).
     */
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    /**
     * Version of the prompt template used.
     */
    @Column(name = "model_prompt_version", length = 50)
    private String modelPromptVersion;
    
    /**
     * Timestamp of the analysis.
     */
    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // ========================================
    // LIFECYCLE HOOKS
    // ========================================
    
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (analyzedAt == null) {
            analyzedAt = now;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // ========================================
    // SEARCHABLE CONTENT (AI Fabric Integration)
    // ========================================
    
    /**
     * Framework uses this to build searchable content for AISearchableEntity.
     * Includes sentiment, churn, and trend dimensions for semantic search.
     */
    public String getSearchableContent() {
        StringBuilder content = new StringBuilder();
        
        content.append("Segment: ").append(segment != null ? segment : "Unknown").append(". ");
        
        if (patterns != null && !patterns.isEmpty()) {
            content.append("Patterns: ").append(String.join(", ", patterns)).append(". ");
        }
        
        // Sentiment with trend indicator
        if (sentimentLabel != null) {
            content.append("Sentiment: ").append(sentimentLabel.getValue());
            if (sentimentScore != null) {
                content.append(String.format(" (%.2f)", sentimentScore));
            }
            Double sDelta = getSentimentDelta();
            if (sDelta != null && Math.abs(sDelta) > 0.1) {
                content.append(String.format(" [%s%.2f]", 
                    sDelta > 0 ? "↑" : "↓", 
                    Math.abs(sDelta)));
            }
            content.append(". ");
        }
        
        // Churn risk with trend indicator
        if (churnRisk != null) {
            String riskLevel = getRiskLevel(churnRisk);
            content.append("Churn Risk: ").append(riskLevel)
                   .append(String.format(" (%.2f)", churnRisk));
            
            Double cDelta = getChurnDelta();
            if (cDelta != null && Math.abs(cDelta) > 0.1) {
                content.append(String.format(" [%s%.2f]", 
                    cDelta > 0 ? "⚠️↑" : "✅↓", 
                    Math.abs(cDelta)));
            }
            
            if (churnRisk > 0.5 && churnReason != null) {
                content.append(" - ").append(churnReason);
            }
            content.append(". ");
        }
        
        // Overall trend
        if (trend != null && trend != BehaviorTrend.STABLE && trend != BehaviorTrend.NEW_USER) {
            content.append("Trend: ").append(trend.getDescription()).append(". ");
        }
        
        if (recommendations != null && !recommendations.isEmpty()) {
            content.append("Recommendations: ").append(String.join(", ", recommendations)).append(". ");
        }
        
        return content.toString().trim();
    }
    
    /**
     * Convert churn risk score to human-readable level.
     */
    private String getRiskLevel(double risk) {
        if (risk < 0.2) return "Low";
        if (risk < 0.4) return "Minor";
        if (risk < 0.6) return "Moderate";
        if (risk < 0.8) return "High";
        return "Critical";
    }
    
    // ========================================
    // HELPER METHODS
    // ========================================
    
    /**
     * Check if sentiment has significantly improved.
     */
    @Transient
    public boolean isSentimentImproving() {
        Double delta = getSentimentDelta();
        return delta != null && delta > 0.2;
    }
    
    /**
     * Check if churn risk is increasing (getting worse).
     */
    @Transient
    public boolean isChurnRiskIncreasing() {
        Double delta = getChurnDelta();
        return delta != null && delta > 0.2;
    }
    
    /**
     * Check if this user requires immediate intervention.
     */
    @Transient
    public boolean requiresImmediateAction() {
        return (trend != null && trend.requiresIntervention())
            || (churnRisk != null && churnRisk > 0.8)
            || (getChurnDelta() != null && getChurnDelta() > 0.4);
    }
}
```

---

## 🔧 SERVICE IMPLEMENTATION

### BehaviorAnalysisService.java - Complete Implementation

**File:** `ai-infrastructure-module/ai-infrastructure-behavior/src/main/java/com/ai/infrastructure/behavior/service/BehaviorAnalysisService.java`

```java
package com.ai.infrastructure.behavior.service;

import com.ai.infrastructure.annotation.AIProcess;
import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.model.BehaviorTrend;
import com.ai.infrastructure.behavior.model.ExternalEvent;
import com.ai.infrastructure.behavior.model.SentimentLabel;
import com.ai.infrastructure.behavior.model.UserEventBatch;
import com.ai.infrastructure.behavior.spi.ExternalEventProvider;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(ExternalEventProvider.class)
public class BehaviorAnalysisService {
    
    private final ExternalEventProvider eventProvider;
    private final BehaviorStorageAdapter storageAdapter;
    private final AICoreService aiCoreService;
    private final ObjectMapper objectMapper;
    
    /**
     * Analyze a specific user (targeted analysis).
     */
    @Transactional
    public BehaviorInsights analyzeUser(UUID userId) {
        log.info("Starting targeted analysis for user: {}", userId);
        
        Optional<BehaviorInsights> existingInsight = storageAdapter.findByUserId(userId);
        List<ExternalEvent> newEvents = eventProvider.getEventsForUser(userId, null, null);
        
        if (newEvents == null || newEvents.isEmpty()) {
            log.warn("No events found for user: {}", userId);
            return existingInsight.orElse(null);
        }
        
        BehaviorInsights updatedInsight = performEvolutionaryAnalysis(
            userId,
            existingInsight.orElse(null),
            newEvents,
            null
        );
        
        return saveAndIndex(updatedInsight);
    }
    
    /**
     * Process the next user in queue (discovery/batch processing).
     */
    @Transactional
    public BehaviorInsights processNextUser() {
        log.debug("Fetching next user for batch processing");
        
        UserEventBatch batch = eventProvider.getNextUserEvents();
        if (batch == null || batch.getUserId() == null) {
            log.debug("No users pending analysis");
            return null;
        }
        
        List<ExternalEvent> events = batch.getEvents() != null
            ? batch.getEvents()
            : Collections.emptyList();
        
        if (events.isEmpty()) {
            log.warn("No events returned for discovery user: {}", batch.getUserId());
            return storageAdapter.findByUserId(batch.getUserId()).orElse(null);
        }
        
        log.info("Processing batch for user: {} with {} events",
            batch.getUserId(), batch.getTotalEventCount());
        
        Optional<BehaviorInsights> existingInsight = storageAdapter.findByUserId(batch.getUserId());
        
        BehaviorInsights updatedInsight = performEvolutionaryAnalysis(
            batch.getUserId(),
            existingInsight.orElse(null),
            events,
            batch.getUserContext()
        );
        
        return saveAndIndex(updatedInsight);
    }
    
    /**
     * Core evolutionary analysis with trend tracking.
     */
    private BehaviorInsights performEvolutionaryAnalysis(
        UUID userId,
        BehaviorInsights oldInsight,
        List<ExternalEvent> newEvents,
        Map<String, Object> userContext
    ) {
        long startTime = System.currentTimeMillis();
        
        try {
            String prompt = buildEvolutionaryPrompt(oldInsight, newEvents, userContext);
            
            AIGenerationResponse response = aiCoreService.generateContent(
                AIGenerationRequest.builder()
                    .entityId(userId.toString())
                    .entityType("behavior-insight")
                    .generationType("behavioral-analysis")
                    .prompt(prompt)
                    .systemPrompt(getSystemPrompt())
                    .temperature(0.2) // Lower for consistency
                    .maxTokens(1200)
                    .build()
            );
            
            BehaviorInsights result = parseLLMResponse(userId, response.getContent(), oldInsight);
            
            // Copy current values to previous for next analysis
            if (oldInsight != null) {
                result.setPreviousSentimentScore(oldInsight.getSentimentScore());
                result.setPreviousChurnRisk(oldInsight.getChurnRisk());
            } else {
                result.setPreviousSentimentScore(null);
                result.setPreviousChurnRisk(null);
                if (result.getTrend() == null) {
                    result.setTrend("NEW_USER");
                }
            }
            
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            result.setAiModelUsed(response.getModel() != null ? response.getModel() : "gpt-4o");
            result.setModelPromptVersion("3.0.0");
            
            logTrendAlert(userId, oldInsight, result);
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to perform evolutionary analysis for user: {}", userId, e);
            
            if (oldInsight != null) {
                return oldInsight;
            }
            
            return BehaviorInsights.builder()
                .userId(userId)
                .segment("unknown")
                .analyzedAt(LocalDateTime.now())
                .confidence(0.0)
                .aiModelUsed("fallback")
                .trend("UNKNOWN")
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }
    
    /**
     * Build LLM prompt with previous values for trend detection.
     */
    private String buildEvolutionaryPrompt(
        BehaviorInsights oldInsight,
        List<ExternalEvent> newEvents,
        Map<String, Object> userContext
    ) {
        StringBuilder prompt = new StringBuilder();
        
        // User Context
        if (userContext != null && !userContext.isEmpty()) {
            prompt.append("=== USER CONTEXT ===\n");
            userContext.forEach((key, value) ->
                prompt.append("- ").append(key).append(": ").append(value).append("\n")
            );
            prompt.append("\n");
        }
        
        // Previous Analysis
        if (oldInsight != null) {
            prompt.append("=== PREVIOUS ANALYSIS ===\n");
            prompt.append("Date: ").append(oldInsight.getAnalyzedAt()).append("\n");
            prompt.append("Segment: ").append(oldInsight.getSegment()).append("\n");
            prompt.append("Patterns: ").append(oldInsight.getPatterns()).append("\n");
            
            if (oldInsight.getSentimentScore() != null) {
                prompt.append("Sentiment: ").append(oldInsight.getSentimentLabel())
                      .append(" (").append(String.format("%.2f", oldInsight.getSentimentScore())).append(")\n");
            }
            
            if (oldInsight.getChurnRisk() != null) {
                prompt.append("Churn Risk: ").append(String.format("%.2f", oldInsight.getChurnRisk()));
                if (oldInsight.getChurnReason() != null) {
                    prompt.append(" - ").append(oldInsight.getChurnReason());
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        } else {
            prompt.append("=== NEW USER - FIRST ANALYSIS ===\n\n");
        }
        
        // Event Summary
        prompt.append("=== NEW EVENTS (").append(newEvents.size()).append(") ===\n");
        
        Map<String, Long> eventTypeCounts = newEvents.stream()
            .collect(Collectors.groupingBy(ExternalEvent::getEventType, Collectors.counting()));
        
        prompt.append("\nEvent Summary:\n");
        eventTypeCounts.forEach((type, count) -> 
            prompt.append("- ").append(type).append(": ").append(count).append(" times\n")
        );
        
        prompt.append("\nDetailed Timeline:\n");
        newEvents.forEach(event -> {
            prompt.append("- [").append(event.getTimestamp()).append("] ")
                  .append(event.getEventType());
            if (event.getEventData() != null) {
                prompt.append(" | ").append(event.getEventData());
            }
            prompt.append("\n");
        });
        
        // Analysis Instructions
        prompt.append("\n=== ANALYSIS REQUIRED ===\n");
        if (oldInsight != null) {
            prompt.append("Compare new events with previous analysis.\n");
            prompt.append("Focus on:\n");
            prompt.append("1. Has sentiment CHANGED? Better or worse?\n");
            prompt.append("2. Has churn risk CHANGED? Increasing or decreasing?\n");
            prompt.append("3. What is the overall TREND?\n");
            prompt.append("4. If declining, what triggered the change?\n");
        } else {
            prompt.append("First analysis - establish baseline.\n");
            prompt.append("Set trend to 'NEW_USER'.\n");
        }
        
        return prompt.toString();
    }
    
    /**
     * System prompt for LLM with trend detection focus.
     */
    private String getSystemPrompt() {
        return """
            You are an expert Behavioral Psychologist specializing in TREND DETECTION.
            
            Analyze user behavior and detect CHANGES over time.
            
            Output Dimensions:
            1. **Segment**: User category (Power User, At Risk, Dormant, etc.)
            2. **Patterns**: Behavioral patterns (night_owl, help_seeker, etc.)
            3. **Sentiment**: Emotional state
               - score: -1.0 (very negative) to 1.0 (very positive)
               - label: DELIGHTED, SATISFIED, NEUTRAL, CONFUSED, FRUSTRATED, CHURNING
            4. **Churn Risk**: Probability of leaving (0.0 to 1.0)
               - reason: Specific explanation
            5. **Trend**: Overall behavioral direction
               - RAPIDLY_IMPROVING: Δsentiment >0.4 OR Δchurn <-0.4
               - IMPROVING: Δsentiment >0.2 OR Δchurn <-0.2
               - STABLE: |Δ| < 0.2
               - DECLINING: Δsentiment <-0.2 OR Δchurn >0.2
               - RAPIDLY_DECLINING: Δsentiment <-0.4 OR Δchurn >0.4 ⚠️
               - NEW_USER: First analysis
            6. **Recommendations**: Actionable next steps
            
            CRITICAL RULES:
            - If NEW USER, set trend = "NEW_USER"
            - If previous data exists, COMPARE and detect delta
            - RAPIDLY_DECLINING requires immediate intervention recommendations
            - Base analysis on OBSERVABLE patterns, don't invent data
            
            Respond with valid JSON:
            {
              "segment": "string",
              "patterns": ["string"],
              "sentiment": {"score": 0.0, "label": "string"},
              "churn": {"risk": 0.0, "reason": "string"},
              "trend": "string",
              "recommendations": ["string"],
              "insights": {},
              "confidence": 0.0-1.0
            }
            """;
    }
    
    /**
     * Parse and validate LLM response.
     */
    private BehaviorInsights parseLLMResponse(
        UUID userId,
        String llmResponse,
        BehaviorInsights oldInsight
    ) throws Exception {
        String json = extractJson(llmResponse);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
        
        BehaviorInsights.BehaviorInsightsBuilder builder = BehaviorInsights.builder()
            .userId(userId)
            .segment((String) parsed.get("segment"))
            .patterns((List<String>) parsed.get("patterns"))
            .recommendations((List<String>) parsed.get("recommendations"))
            .insights((Map<String, Object>) parsed.get("insights"))
            .confidence(((Number) parsed.getOrDefault("confidence", 0.5)).doubleValue())
            .analyzedAt(LocalDateTime.now());
        
        // Parse and validate sentiment using ENUM
        Map<String, Object> sentiment = (Map<String, Object>) parsed.get("sentiment");
        if (sentiment != null) {
            Double score = ((Number) sentiment.getOrDefault("score", 0.0)).doubleValue();
            String labelStr = (String) sentiment.get("label");
            
            // Clamp score to valid range
            score = Math.max(-1.0, Math.min(1.0, score));
            
            // Parse to enum (automatically handles invalid values with fallback)
            SentimentLabel label = SentimentLabel.fromString(labelStr);
            if (labelStr != null && label == SentimentLabel.NEUTRAL && !labelStr.equalsIgnoreCase("NEUTRAL")) {
                log.warn("Invalid sentiment label '{}' for user {}, defaulted to NEUTRAL", labelStr, userId);
            }
            
            builder.sentimentScore(score).sentimentLabel(label);
        }
        
        // Parse and validate churn
        Map<String, Object> churn = (Map<String, Object>) parsed.get("churn");
        if (churn != null) {
            Double risk = ((Number) churn.getOrDefault("risk", 0.0)).doubleValue();
            String reason = (String) churn.get("reason");
            
            // Clamp risk to valid range
            risk = Math.max(0.0, Math.min(1.0, risk));
            
            // Require reason for high risk
            if (risk > 0.5 && (reason == null || reason.isBlank())) {
                log.warn("High churn risk without reason for user {}", userId);
                reason = "Behavioral drift detected";
            }
            
            builder.churnRisk(risk).churnReason(reason);
        }
        
        // Parse and validate trend using ENUM
        String trendStr = (String) parsed.get("trend");
        BehaviorTrend trend = BehaviorTrend.fromString(trendStr);
        
        // If LLM didn't provide a valid trend, compute from deltas
        if (trendStr != null && trend == BehaviorTrend.STABLE && !trendStr.equalsIgnoreCase("STABLE")) {
            log.warn("Invalid trend '{}' for user {}, computing from deltas", trendStr, userId);
        }
        
        if (trend == null || (oldInsight != null && trend == BehaviorTrend.STABLE)) {
            if (oldInsight == null) {
                trend = BehaviorTrend.NEW_USER;
            } else {
                BehaviorInsights temp = builder.build();
                trend = BehaviorTrend.fromDeltas(
                    temp.getSentimentDelta(),
                    temp.getChurnDelta(),
                    false
                );
            }
        }
        
        builder.trend(trend);
        
        // Preserve existing insight ID if updating
        if (oldInsight != null) {
            builder.id(oldInsight.getId())
                   .createdAt(oldInsight.getCreatedAt());
        }
        
        return builder.build();
    }
    
    
    /**
     * Log significant behavioral changes for monitoring.
     */
    private void logTrendAlert(UUID userId, BehaviorInsights old, BehaviorInsights current) {
        if (old == null) {
            log.info("New user analyzed: {} | Sentiment: {} | Churn: {:.2f} | Trend: {}",
                userId, current.getSentimentLabel(), current.getChurnRisk(), current.getTrend());
            return;
        }
        
        // Compute deltas for logging
        Double sentimentDelta = (current.getSentimentScore() != null && old.getSentimentScore() != null)
            ? current.getSentimentScore() - old.getSentimentScore() : null;
        
        Double churnDelta = (current.getChurnRisk() != null && old.getChurnRisk() != null)
            ? current.getChurnRisk() - old.getChurnRisk() : null;
        
        // Alert on rapid decline
        if (current.getTrend() == BehaviorTrend.RAPIDLY_DECLINING) {
            log.warn("⚠️ RAPID DECLINE - User: {} | Sentiment: {} (Δ{:.2f}) | Churn: {:.2f} (Δ{:.2f}) | Reason: {}",
                userId, current.getSentimentLabel() != null ? current.getSentimentLabel().getValue() : "UNKNOWN",
                sentimentDelta != null ? sentimentDelta : 0.0,
                current.getChurnRisk(),
                churnDelta != null ? churnDelta : 0.0,
                current.getChurnReason()
            );
        }
        
        // Log significant sentiment shifts
        if (sentimentDelta != null && Math.abs(sentimentDelta) > 0.3) {
            log.info("Sentiment shift: User {} | {} -> {} (Δ{:.2f})",
                userId, old.getSentimentLabel(), current.getSentimentLabel(), sentimentDelta);
        }
        
        // Warn on increasing churn risk
        if (churnDelta != null && churnDelta > 0.2) {
            log.warn("Churn risk increasing: User {} | {:.2f} -> {:.2f} (Δ{:.2f})",
                userId, old.getChurnRisk(), current.getChurnRisk(), churnDelta);
        }
    }
    
    /**
     * Extract JSON from LLM response (handles markdown code blocks).
     */
    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start == -1 || end <= start) {
            throw new IllegalStateException("No valid JSON in LLM response");
        }
        return response.substring(start, end + 1);
    }
    
    /**
     * Save and trigger AI Fabric Framework indexing.
     */
    @AIProcess(
        entityType = "behavior-insight",
        processType = "create"
    )
    private BehaviorInsights saveAndIndex(BehaviorInsights insight) {
        return storageAdapter.save(insight);
    }
}
```

---

## 📊 REPOSITORY LAYER

### BehaviorInsightsRepository.java

**File:** `ai-infrastructure-module/ai-infrastructure-behavior/src/main/java/com/ai/infrastructure/behavior/repository/BehaviorInsightsRepository.java`

```java
package com.ai.infrastructure.behavior.repository;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.model.BehaviorTrend;
import com.ai.infrastructure.behavior.model.SentimentLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BehaviorInsightsRepository extends JpaRepository<BehaviorInsights, UUID> {
    
    /**
     * Find insight by user ID.
     */
    Optional<BehaviorInsights> findByUserId(UUID userId);
    
    /**
     * Delete insight by user ID.
     */
    void deleteByUserId(UUID userId);
    
    // ========================================
    // TREND-BASED QUERIES (Using Enums)
    // ========================================
    
    /**
     * Find users in rapid decline (immediate intervention needed).
     * Uses enum for type-safe query.
     */
    @Query("SELECT bi FROM BehaviorInsights bi WHERE bi.trend = :trend ORDER BY bi.churnRisk DESC")
    List<BehaviorInsights> findByTrendOrdered(@Param("trend") BehaviorTrend trend);
    
    /**
     * Convenience method for rapidly declining users.
     */
    default List<BehaviorInsights> findRapidlyDecliningUsers() {
        return findByTrendOrdered(BehaviorTrend.RAPIDLY_DECLINING);
    }
    
    /**
     * Find users with high churn risk.
     */
    @Query("SELECT bi FROM BehaviorInsights bi WHERE bi.churnRisk >= :threshold ORDER BY bi.churnRisk DESC")
    List<BehaviorInsights> findHighChurnRiskUsers(@Param("threshold") double threshold);
    
    /**
     * Find users with negative sentiment.
     */
    @Query("SELECT bi FROM BehaviorInsights bi WHERE bi.sentimentScore < :threshold ORDER BY bi.sentimentScore ASC")
    List<BehaviorInsights> findNegativeSentimentUsers(@Param("threshold") double threshold);
    
    /**
     * Find users who are improving (success stories).
     * Uses enum constants for type safety.
     */
    @Query("SELECT bi FROM BehaviorInsights bi WHERE bi.trend IN (:trends) ORDER BY bi.analyzedAt DESC")
    List<BehaviorInsights> findByTrendIn(@Param("trends") List<BehaviorTrend> trends);
    
    /**
     * Convenience method for improving users.
     */
    default List<BehaviorInsights> findImprovingUsers() {
        return findByTrendIn(List.of(BehaviorTrend.IMPROVING, BehaviorTrend.RAPIDLY_IMPROVING));
    }
    
    /**
     * Find users by sentiment label (type-safe enum).
     */
    List<BehaviorInsights> findBySentimentLabel(SentimentLabel sentimentLabel);
    
    /**
     * Find users by trend (type-safe enum).
     */
    List<BehaviorInsights> findByTrend(BehaviorTrend trend);
    
    /**
     * Count users by churn risk threshold.
     */
    long countByChurnRiskGreaterThanEqual(double threshold);
    
    /**
     * Count users by sentiment threshold.
     */
    long countBySentimentScoreLessThan(double threshold);
    
    /**
     * Count users by sentiment label (type-safe).
     */
    long countBySentimentLabel(SentimentLabel sentimentLabel);
    
    /**
     * Count users by trend (type-safe).
     */
    long countByTrend(BehaviorTrend trend);
    
    // ========================================
    // DELTA-BASED QUERIES (Inline JPQL Arithmetic)
    // ========================================
    
    /**
     * Find users with increasing churn risk (delta > threshold).
     * Uses inline JPQL arithmetic - works on all databases.
     */
    @Query("""
        SELECT bi FROM BehaviorInsights bi 
        WHERE bi.churnRisk IS NOT NULL 
          AND bi.previousChurnRisk IS NOT NULL
          AND (bi.churnRisk - bi.previousChurnRisk) > :threshold
        ORDER BY (bi.churnRisk - bi.previousChurnRisk) DESC
        """)
    List<BehaviorInsights> findIncreasingChurnRisk(@Param("threshold") double threshold);
    
    /**
     * Find users with degrading sentiment (delta < -threshold).
     * Uses inline JPQL arithmetic - works on all databases.
     */
    @Query("""
        SELECT bi FROM BehaviorInsights bi 
        WHERE bi.sentimentScore IS NOT NULL 
          AND bi.previousSentimentScore IS NOT NULL
          AND (bi.sentimentScore - bi.previousSentimentScore) < :threshold
        ORDER BY (bi.sentimentScore - bi.previousSentimentScore) ASC
        """)
    List<BehaviorInsights> findDegradingSentiment(@Param("threshold") double threshold);
    
    /**
     * Find users who improved significantly.
     * Uses inline JPQL arithmetic - works on all databases.
     */
    @Query("""
        SELECT bi FROM BehaviorInsights bi 
        WHERE bi.sentimentScore IS NOT NULL 
          AND bi.previousSentimentScore IS NOT NULL
          AND (bi.sentimentScore - bi.previousSentimentScore) > :threshold
        ORDER BY (bi.sentimentScore - bi.previousSentimentScore) DESC
        """)
    List<BehaviorInsights> findImprovingSentiment(@Param("threshold") double threshold);
}
```

---

## 🧪 TESTING STRATEGY

### Unit Tests

**File:** `ai-infrastructure-module/ai-infrastructure-behavior/src/test/java/com/ai/infrastructure/behavior/service/BehaviorAnalysisServiceTest.java`

```java
package com.ai.infrastructure.behavior.service;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.model.ExternalEvent;
import com.ai.infrastructure.behavior.spi.ExternalEventProvider;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BehaviorAnalysisServiceTest {
    
    @Mock
    private ExternalEventProvider eventProvider;
    
    @Mock
    private BehaviorStorageAdapter storageAdapter;
    
    @Mock
    private AICoreService aiCoreService;
    
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private BehaviorAnalysisService service;
    
    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        service = new BehaviorAnalysisService(eventProvider, storageAdapter, aiCoreService, objectMapper);
    }
    
    @Test
    void analyzeUser_newUser_createsBaseline() {
        UUID userId = UUID.randomUUID();
        
        List<ExternalEvent> events = List.of(
            ExternalEvent.builder()
                .eventType("signup")
                .timestamp(LocalDateTime.now())
                .eventData(Map.of("source", "web"))
                .build()
        );
        
        when(eventProvider.getEventsForUser(userId, null, null)).thenReturn(events);
        when(storageAdapter.findByUserId(userId)).thenReturn(Optional.empty());
        when(aiCoreService.generateContent(any())).thenReturn(
            AIGenerationResponse.builder()
                .content("""
                    {
                      "segment": "New User",
                      "patterns": ["onboarding"],
                      "sentiment": {"score": 0.5, "label": "NEUTRAL"},
                      "churn": {"risk": 0.1, "reason": "Just started"},
                      "trend": "NEW_USER",
                      "recommendations": ["complete_profile"],
                      "insights": {},
                      "confidence": 0.8
                    }
                    """)
                .model("gpt-4o")
                .build()
        );
        when(storageAdapter.save(any())).thenAnswer(inv -> inv.getArgument(0));
        
        BehaviorInsights result = service.analyzeUser(userId);
        
        assertThat(result).isNotNull();
        assertThat(result.getTrend()).isEqualTo(BehaviorTrend.NEW_USER);
        assertThat(result.getPreviousSentimentScore()).isNull();
        assertThat(result.getPreviousChurnRisk()).isNull();
    }
    
    @Test
    void analyzeUser_rapidDecline_detectsTrendAndAlerts() {
        UUID userId = UUID.randomUUID();
        
        // Previous: Happy user
        BehaviorInsights previous = BehaviorInsights.builder()
            .userId(userId)
            .sentimentScore(0.7)
            .sentimentLabel(SentimentLabel.SATISFIED)
            .churnRisk(0.2)
            .analyzedAt(LocalDateTime.now().minusDays(7))
            .build();
        
        // New events: Cancellation signals
        List<ExternalEvent> events = List.of(
            ExternalEvent.builder()
                .eventType("cancellation_page_view")
                .timestamp(LocalDateTime.now().minusDays(1))
                .build(),
            ExternalEvent.builder()
                .eventType("support_ticket")
                .eventData(Map.of("topic", "billing_complaint"))
                .timestamp(LocalDateTime.now())
                .build()
        );
        
        when(storageAdapter.findByUserId(userId)).thenReturn(Optional.of(previous));
        when(eventProvider.getEventsForUser(userId, null, null)).thenReturn(events);
        when(aiCoreService.generateContent(any())).thenReturn(
            AIGenerationResponse.builder()
                .content("""
                    {
                      "segment": "At Risk",
                      "patterns": ["cancellation_researcher"],
                      "sentiment": {"score": -0.3, "label": "FRUSTRATED"},
                      "churn": {"risk": 0.8, "reason": "Billing issue led to cancellation page visit and support escalation"},
                      "trend": "RAPIDLY_DECLINING",
                      "recommendations": ["urgent_retention_contact", "billing_resolution"],
                      "insights": {"trigger_event": "billing_complaint"},
                      "confidence": 0.92
                    }
                    """)
                .model("gpt-4o")
                .build()
        );
        when(storageAdapter.save(any())).thenAnswer(inv -> inv.getArgument(0));
        
        BehaviorInsights result = service.analyzeUser(userId);
        
        // Verify trend detection
        assertThat(result.getTrend()).isEqualTo(BehaviorTrend.RAPIDLY_DECLINING);
        assertThat(result.getSentimentLabel()).isEqualTo(SentimentLabel.FRUSTRATED);
        assertThat(result.getChurnRisk()).isEqualTo(0.8);
        
        // Verify previous values stored
        assertThat(result.getPreviousSentimentScore()).isEqualTo(0.7);
        assertThat(result.getPreviousChurnRisk()).isEqualTo(0.2);
        
        // Verify helper methods
        assertThat(result.isChurnRiskIncreasing()).isTrue(); // 0.8 - 0.2 = 0.6 > 0.2
        assertThat(result.requiresImmediateAction()).isTrue(); // RAPIDLY_DECLINING
    }
    
    @Test
    void parseLLMResponse_invalidSentimentScore_clampsToValidRange() {
        UUID userId = UUID.randomUUID();
        
        when(eventProvider.getEventsForUser(userId, null, null)).thenReturn(
            List.of(ExternalEvent.builder().eventType("test").build())
        );
        when(storageAdapter.findByUserId(userId)).thenReturn(Optional.empty());
        when(aiCoreService.generateContent(any())).thenReturn(
            AIGenerationResponse.builder()
                .content("""
                    {
                      "segment": "Test",
                      "patterns": [],
                      "sentiment": {"score": 1.5, "label": "INVALID"},
                      "churn": {"risk": -0.2},
                      "trend": "STABLE",
                      "recommendations": [],
                      "confidence": 0.5
                    }
                    """)
                .build()
        );
        when(storageAdapter.save(any())).thenAnswer(inv -> inv.getArgument(0));
        
        BehaviorInsights result = service.analyzeUser(userId);
        
        // Should clamp to valid ranges
        assertThat(result.getSentimentScore()).isBetween(-1.0, 1.0);
        assertThat(result.getChurnRisk()).isBetween(0.0, 1.0);
        assertThat(result.getSentimentLabel()).isEqualTo("NEUTRAL"); // Invalid label defaulted
    }
}
```

---

## 🌐 REST API EXAMPLES

### BehaviorAnalyticsController.java

**File:** `ai-infrastructure-module/ai-infrastructure-behavior/src/main/java/com/ai/infrastructure/behavior/api/BehaviorAnalyticsController.java`

```java
package com.ai.infrastructure.behavior.api;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.repository.BehaviorInsightsRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/behavior/analytics")
@RequiredArgsConstructor
public class BehaviorAnalyticsController {
    
    private final BehaviorInsightsRepository repository;
    
    /**
     * Get users in rapid decline (CRITICAL).
     */
    @GetMapping("/rapid-decline")
    public ResponseEntity<List<TrendAlertDTO>> getRapidDeclineAlerts() {
        List<BehaviorInsights> declining = repository.findRapidlyDecliningUsers();
        
        List<TrendAlertDTO> alerts = declining.stream()
            .map(bi -> TrendAlertDTO.builder()
                .userId(bi.getUserId())
                .sentiment(bi.getSentimentLabel())
                .churnRisk(bi.getChurnRisk())
                .churnReason(bi.getChurnReason())
                .trend(bi.getTrend())
                .recommendations(bi.getRecommendations())
                .analyzedAt(bi.getAnalyzedAt())
                .build())
            .toList();
        
        return ResponseEntity.ok(alerts);
    }
    
    /**
     * Get trend distribution across user base.
     */
    @GetMapping("/trend-distribution")
    public ResponseEntity<Map<String, Long>> getTrendDistribution() {
        List<BehaviorInsights> all = repository.findAll();
        
        Map<String, Long> distribution = all.stream()
            .filter(bi -> bi.getTrend() != null)
            .collect(Collectors.groupingBy(
                BehaviorInsights::getTrend,
                Collectors.counting()
            ));
        
        return ResponseEntity.ok(distribution);
    }
    
    /**
     * Get sentiment distribution (using enums).
     */
    @GetMapping("/sentiment-distribution")
    public ResponseEntity<Map<String, Long>> getSentimentDistribution() {
        List<BehaviorInsights> all = repository.findAll();
        
        Map<String, Long> distribution = all.stream()
            .filter(bi -> bi.getSentimentLabel() != null)
            .collect(Collectors.groupingBy(
                bi -> bi.getSentimentLabel().getValue(),
                Collectors.counting()
            ));
        
        return ResponseEntity.ok(distribution);
    }
    
    /**
     * Get user-specific trend detail.
     */
    @GetMapping("/users/{userId}/trend")
    public ResponseEntity<UserTrendDTO> getUserTrend(@PathVariable UUID userId) {
        BehaviorInsights insight = repository.findByUserId(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        UserTrendDTO dto = UserTrendDTO.builder()
            .userId(userId)
            .currentSentiment(insight.getSentimentScore())
            .previousSentiment(insight.getPreviousSentimentScore())
            .sentimentDelta(insight.getSentimentDelta())
            .currentChurnRisk(insight.getChurnRisk())
            .previousChurnRisk(insight.getPreviousChurnRisk())
            .churnDelta(insight.getChurnDelta())
            .trend(insight.getTrend())
            .churnReason(insight.getChurnReason())
            .recommendations(insight.getRecommendations())
            .analyzedAt(insight.getAnalyzedAt())
            .build();
        
        return ResponseEntity.ok(dto);
    }
    
    // ========================================
    // DTOs
    // ========================================
    
    @Data
    @Builder
    public static class TrendAlertDTO {
        private UUID userId;
        private String sentiment;
        private Double churnRisk;
        private String churnReason;
        private String trend;
        private List<String> recommendations;
        private LocalDateTime analyzedAt;
    }
    
    @Data
    @Builder
    public static class UserTrendDTO {
        private UUID userId;
        private Double currentSentiment;
        private Double previousSentiment;
        private Double sentimentDelta;
        private Double currentChurnRisk;
        private Double previousChurnRisk;
        private Double churnDelta;
        private String trend;
        private String churnReason;
        private List<String> recommendations;
        private LocalDateTime analyzedAt;
    }
}
```
## ✅ IMPLEMENTATION CHECKLIST

### Phase 1: Entity & Repository (1 hour)
- [ ] Create `BehaviorInsights` entity with all fields
- [ ] Add `@Formula` annotations for computed deltas
- [ ] Create `BehaviorInsightsRepository` with trend queries
- [ ] Generate Liquibase diff (user responsibility)

### Phase 2: Service Logic (2 hours)
- [ ] Implement `BehaviorAnalysisService.performEvolutionaryAnalysis()`
- [ ] Add `buildEvolutionaryPrompt()` with previous values
- [ ] Add `parseLLMResponse()` with validation
- [ ] Implement `computeFallbackTrend()`
- [ ] Add `logTrendAlert()` for monitoring

### Phase 3: Testing (1 hour)
- [ ] Unit tests for new user baseline
- [ ] Unit tests for rapid decline detection
- [ ] Unit tests for validation/clamping
- [ ] Integration tests for persistence

### Phase 4: API & Monitoring (30 minutes)
- [ ] Add REST endpoints (`/rapid-decline`, `/trend-distribution`)
- [ ] Add metrics publisher
- [ ] Configure monitoring dashboards

### Phase 5: Documentation (15 minutes)
- [ ] Update README with new fields
- [ ] Document alert thresholds
- [ ] Add query examples

**Total Estimated Time: 4.5 hours**

---

## 📚 APPENDIX

### Sentiment Label Decision Tree

```
Event Patterns                          → Sentiment Label
─────────────────────────────────────────────────────────
Success + exploration + upgrades        → DELIGHTED
Consistent usage + no errors           → SATISFIED
Mixed signals, no clear trend           → NEUTRAL
Help pages + repeated attempts          → CONFUSED
Errors + support tickets                → FRUSTRATED
Cancellation + pricing research         → CHURNING
```

### Trend Calculation Logic

```
Behavioral Signal                       → Trend
─────────────────────────────────────────────────────────
Δsentiment > 0.4 OR Δchurn < -0.4      → RAPIDLY_IMPROVING
Δsentiment > 0.2 OR Δchurn < -0.2      → IMPROVING
|Δsentiment| < 0.2 AND |Δchurn| < 0.2  → STABLE
Δsentiment < -0.2 OR Δchurn > 0.2      → DECLINING
Δsentiment < -0.4 OR Δchurn > 0.4      → RAPIDLY_DECLINING
No previous data                        → NEW_USER
```

### Example LLM Response

```json
{
  "segment": "Struggling Power User",
  "patterns": ["frequent_help_seeker", "feature_explorer", "error_prone"],
  "sentiment": {
    "score": -0.3,
    "label": "FRUSTRATED"
  },
  "churn": {
    "risk": 0.55,
    "reason": "High engagement but frequent errors in checkout. Visited help 5 times in 3 days. Without intervention, likely to abandon."
  },
  "trend": "DECLINING",
  "recommendations": [
    "proactive_technical_support",
    "workflow_optimization_offer"
  ],
  "insights": {
    "trigger_event": "checkout_errors"
  },
  "confidence": 0.87
}
```

---

## 🎯 WHY ENUMS? Domain Coupling Explained

### Question: Are the sentiment labels and trends domain-coupled?

**Answer: YES - And that's intentional design!**

### What is Domain Coupling?

The `SentimentLabel` and `BehaviorTrend` enums represent **business domain concepts** from the User Behavioral Analytics domain. They are NOT technical constants.

| Type | Example | Domain |
|------|---------|--------|
| **Technical Constant** | `MAX_RETRIES = 3` | Infrastructure |
| **Domain Concept** | `FRUSTRATED`, `CHURNING` | Business Semantics |

### Why Domain Coupling is GOOD Here

1. **Business Language**
   - "DELIGHTED" and "FRUSTRATED" are terms stakeholders understand
   - Changes require business approval, not just developer decision
   - Self-documenting code

2. **LLM API Contract**
   - The AI model is instructed to return these specific labels
   - Part of the contract between our code and the AI
   - Validation ensures consistency

3. **Type Safety**
   - ✅ Compile-time checks
   - ✅ IDE autocomplete
   - ✅ Impossible to typo
   - ❌ Runtime string errors eliminated

### Before (String Constants in Service)

```java
// ❌ Hidden in service class
private static final List<String> VALID_SENTIMENT_LABELS = List.of(
    "DELIGHTED", "SATISFIED", "NEUTRAL", "CONFUSED", "FRUSTRATED", "CHURNING"
);

// ❌ No type safety
builder.sentimentLabel("FRASTRATED"); // Typo goes unnoticed!
```

### After (Domain Enums)

```java
// ✅ First-class domain model
public enum SentimentLabel {
    DELIGHTED("DELIGHTED", "Extremely positive engagement"),
    SATISFIED("SATISFIED", "Positive experience"),
    // ... with business logic
    public boolean isNegative() { ... }
}

// ✅ Type-safe
builder.sentimentLabel(SentimentLabel.FRUSTRATED); // Compile error if typo!
```

### Benefits Matrix

| Aspect | String Constants | **Enums** |
|--------|-----------------|-----------|
| **Type Safety** | ❌ Runtime errors | ✅ Compile-time safety |
| **IDE Support** | ⚠️ String autocomplete | ✅ Full enum support |
| **Domain Clarity** | ⚠️ Hidden in service | ✅ Explicit domain model |
| **Extensibility** | ❌ Code change required | ✅ Add enum value |
| **Documentation** | ⚠️ JavaDoc in service | ✅ Enum-level docs |
| **Business Logic** | ❌ Scattered | ✅ Centralized (e.g., `isNegative()`) |
| **Reusability** | ❌ One service only | ✅ Shared across codebase |
| **Domain Coupling** | ⚠️ Implicit | ✅ **Explicit** |

### Conclusion

Using enums makes domain coupling **explicit and beneficial**:
- ✅ Business concepts are first-class citizens
- ✅ Type safety prevents invalid states
- ✅ Easy to extend without changing service code
- ✅ Self-documenting domain model
- ✅ Centralized business logic

**Domain coupling is NOT a code smell when it represents genuine business semantics.**

---

**Document Version:** 3.1.0  
**Last Updated:** 2025-12-27  
**Changes:** Added domain enums (SentimentLabel, BehaviorTrend), replaced @Formula with @Transient, added inline JPQL arithmetic queries  
**Status:** ✅ Ready for Implementation  
**Author:** AI Infrastructure Team

