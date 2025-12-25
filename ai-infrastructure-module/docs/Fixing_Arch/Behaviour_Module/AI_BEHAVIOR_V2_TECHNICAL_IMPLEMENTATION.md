# AI Behavior Module v2 - Detailed Technical Implementation Plan

**Version:** 2.3-TECHNICAL  
**Status:** Implementation Ready  
**Strategic Alignment:** AI Fabric Framework Native Addon

---

## 📋 TABLE OF CONTENTS

1. [Executive Summary](#1-executive-summary)
2. [Architecture Deep Dive](#2-architecture-deep-dive)
3. [Database Schema](#3-database-schema)
4. [Service Provider Interfaces (SPIs)](#4-service-provider-interfaces-spis)
5. [Core Service Implementation](#5-core-service-implementation)
6. [Configuration System](#6-configuration-system)
7. [Integration with AI Framework](#7-integration-with-ai-framework)
8. [Error Handling & Resilience](#8-error-handling--resilience)
9. [Performance Considerations](#9-performance-considerations)
10. [Testing Strategy](#10-testing-strategy)
11. [Implementation Sequence](#11-implementation-sequence)

---

## 1. Executive Summary

The AI Behavior Module v2 transforms application-side user interactions into persistent AI insights. It operates in two modes (LIGHT/FULL), uses pull-based event retrieval, and leverages the AI Fabric Framework's `@AIProcess` annotation for automatic indexing in discovery scenarios.

**Key Differentiators:**
- Zero raw event persistence (Pull-only architecture)
- Stateful evolution (Updates existing insights, not replacements)
- Framework-native (Uses `@AIProcess`, `AISearchableEntity`, `RelationshipQuery`)

---

## 2. Architecture Deep Dive

### 2.1 Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ APPLICATION LAYER                                           │
│ ┌─────────────────┐    ┌──────────────────┐               │
│ │ Personalization │    │ Discovery/Stats  │               │
│ │ API             │    │ API              │               │
│ └────────┬────────┘    └────────┬─────────┘               │
└──────────┼──────────────────────┼──────────────────────────┘
           │                      │
           ▼                      ▼
┌─────────────────────────────────────────────────────────────┐
│ BEHAVIOR MODULE CORE                                        │
│ ┌──────────────────────────────────────────────────────┐   │
│ │ BehaviorAnalysisService                              │   │
│ │ - analyzeUser(userId)                                │   │
│ │ - processNextUser()                                  │   │
│ │ - evolutionaryAnalysis(oldInsight, newEvents)        │   │
│ └──────────────────────────────────────────────────────┘   │
│                                                             │
│ ┌──────────────────┐         ┌───────────────────────┐    │
│ │ BehaviorInsights │◄────────┤ BehaviorInsightStore  │    │
│ │ Repository       │         │ (SPI)                 │    │
│ └──────────────────┘         └───────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
           ▲                      ▲
           │                      │
           │         ┌────────────┴─────────────┐
           │         │ ExternalEventProvider    │
           │         │ (User-Implemented SPI)   │
           │         └──────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────────┐
│ AI FABRIC FRAMEWORK                                         │
│ ┌──────────────┐  ┌────────────────┐  ┌─────────────────┐ │
│ │ @AIProcess   │  │ AICoreService  │  │ Relationship    │ │
│ │ Aspect       │  │ (LLM)          │  │ QueryPlanner    │ │
│ └──────────────┘  └────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Data Flow Sequence

#### Case 1: Targeted User Analysis
```
┌──────┐         ┌─────────────┐         ┌──────────────┐         ┌────────┐
│Client│         │Analysis Svc │         │Event Provider│         │LLM Core│
└───┬──┘         └──────┬──────┘         └──────┬───────┘         └───┬────┘
    │                   │                       │                     │
    │ analyzeUser(uid)  │                       │                     │
    ├──────────────────►│                       │                     │
    │                   │                       │                     │
    │                   │ 1. Find existing      │                     │
    │                   ├──insight(uid)         │                     │
    │                   │◄──────────────────────┤                     │
    │                   │                       │                     │
    │                   │ 2. getEventsForUser(uid)                    │
    │                   ├──────────────────────►│                     │
    │                   │◄──events[]────────────┤                     │
    │                   │                       │                     │
    │                   │ 3. buildPrompt(old, new)                    │
    │                   │                       │                     │
    │                   │ 4. generateContent()  │                     │
    │                   ├───────────────────────┼────────────────────►│
    │                   │◄──insight json────────┼─────────────────────┤
    │                   │                       │                     │
    │                   │ 5. save(insight)      │                     │
    │                   │   [@AIProcess triggers]                     │
    │                   │                       │                     │
    │◄──insight─────────┤                       │                     │
    │                   │                       │                     │
```

#### Case 2: Discovery/Batch Processing
```
┌─────────┐         ┌─────────────┐         ┌──────────────┐
│Scheduler│         │Analysis Svc │         │Event Provider│
└────┬────┘         └──────┬──────┘         └──────┬───────┘
     │                     │                       │
     │ @Scheduled trigger  │                       │
     ├────────────────────►│                       │
     │                     │                       │
     │                     │ getNextUserEvents()   │
     │                     ├──────────────────────►│
     │                     │◄──UserEventBatch──────┤
     │                     │                       │
     │                     │ [Process like Case 1] │
     │                     │                       │
     │                     │ Loop until no more    │
     │                     │                       │
```

---

## 3. Database Schema

### 3.1 Database Schema Strategy (Open Source Library Approach)

**Philosophy:** As an open-source library, we **do not enforce** a specific database or migration tool. Users manage their own schema.

#### What the Library Provides

1. **JPA Entity with Standard Annotations** (The Contract)
   - Users can generate schema from JPA entities
   - Compatible with any JPA-compliant database

2. **Example Migration Scripts** (Optional Reference)
   - Located in `docs/database/` (not in `src/`)
   - Provided for PostgreSQL, MySQL, and H2
   - Users adapt to their environment

3. **Flexible Configuration**
   - Development: Supports Hibernate auto-DDL
   - Production: Users provide their own schema management

#### Recommended Configuration

**Development/Testing:**
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update  # Auto-generates schema from JPA entities
    show-sql: true
```

**Production:**
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # Users manage schema via Flyway/Liquibase/etc
```

#### Example Schema (PostgreSQL)

Located in `docs/database/postgresql/behavior-insights-schema.sql` (for reference only):

```sql
-- Example schema for PostgreSQL
-- Users should adapt this to their database and migration tool

CREATE TABLE ai_behavior_insights (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    segment VARCHAR(100),
    patterns JSONB,
    recommendations JSONB,
    insights JSONB,
    analyzed_at TIMESTAMP NOT NULL,
    confidence DOUBLE PRECISION,
    ai_model_used VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    
    CONSTRAINT uk_behavior_insights_user UNIQUE (user_id)
);

CREATE INDEX idx_behavior_insights_user_id ON ai_behavior_insights(user_id);
CREATE INDEX idx_behavior_insights_analyzed_at ON ai_behavior_insights(analyzed_at DESC);
CREATE INDEX idx_behavior_insights_segment ON ai_behavior_insights(segment);
```

**Database-Specific Notes:**
- **PostgreSQL:** Use `JSONB` and `UUID` as shown above
- **MySQL:** Replace `JSONB` → `JSON`, use `CHAR(36)` for UUIDs
- **H2:** Replace `UUID` → `VARCHAR(36)`, `JSONB` → `VARCHAR(5000)`
- **Oracle:** Replace `JSONB` → `CLOB`, use `RAW(16)` for UUIDs

#### No Migration Dependencies

The library **does not include** Flyway or Liquibase dependencies. Users add these to their application if needed:

```xml
<!-- Users add to their own pom.xml -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <version>${flyway.version}</version>
</dependency>
```

#### Database Portability

The `BehaviorInsights` entity uses **JPA 2.2+ features**:
- **JSON Storage:** Uses `@Convert` with custom converters (works across all databases)
- **UUID Support:** Handled by Hibernate (generates strings for unsupported databases)
- **Timestamp Handling:** Standard JPA `LocalDateTime`

Users on databases without native JSON support should provide custom `AttributeConverter` implementations.

### 3.2 Entity Model

```java
package com.ai.infrastructure.behavior.entity;

import com.ai.infrastructure.annotation.AICapable;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ai_behavior_insights")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@AICapable(
    entityType = "behavior-insight",
    autoEmbedding = true,  // Overridden by YAML in LIGHT mode
    indexable = true       // Overridden by YAML in LIGHT mode
)
public class BehaviorInsights {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    
    @Column(name = "segment", length = 100)
    private String segment;
    
    @Column(name = "patterns", columnDefinition = "jsonb")
    @Convert(converter = JsonbListConverter.class)
    private List<String> patterns;
    
    @Column(name = "recommendations", columnDefinition = "jsonb")
    @Convert(converter = JsonbListConverter.class)
    private List<String> recommendations;
    
    @Column(name = "insights", columnDefinition = "jsonb")
    @Convert(converter = JsonbMapConverter.class)
    private Map<String, Object> insights;
    
    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;
    
    @Column(name = "confidence")
    private Double confidence;
    
    @Column(name = "ai_model_used", length = 50)
    private String aiModelUsed;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Framework uses this to build searchable content for AISearchableEntity
     */
    public String getSearchableContent() {
        return String.format(
            "Segment: %s. Patterns: %s. Recommendations: %s. Confidence: %.2f",
            segment != null ? segment : "Unknown",
            patterns != null ? String.join(", ", patterns) : "None",
            recommendations != null ? String.join(", ", recommendations) : "None",
            confidence != null ? confidence : 0.0
        );
    }
}
```

---

## 4. Service Provider Interfaces (SPIs)

### 4.1 ExternalEventProvider

```java
package com.ai.infrastructure.behavior.spi;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * User-implemented interface to provide behavioral events from external sources.
 * The module never stores raw events; it pulls them on-demand via this SPI.
 */
public interface ExternalEventProvider {
    
    /**
     * CASE 1: Targeted Fetch with Time Window
     * Fetch events for a specific user within a time range.
     * 
     * @param userId The user to analyze
     * @param since Fetch events from this timestamp forward (null = use default window, e.g., last 30 days)
     * @param until Fetch events up to this timestamp (null = NOW)
     * @return List of events within the time window
     */
    List<ExternalEvent> getEventsForUser(
        UUID userId,
        LocalDateTime since,
        LocalDateTime until
    );
    
    /**
     * CASE 2: Discovery Fetch
     * Returns events for the "next" user who needs analysis.
     * The implementation decides:
     * - Which user to analyze next (e.g., oldest analyzed_at, highest event count)
     * - What time window to use for that user's events
     * 
     * @return Batch containing userId and their events, or null if no users pending
     */
    UserEventBatch getNextUserEvents();
}
```

### 4.2 ExternalEvent Model

```java
package com.ai.infrastructure.behavior.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ExternalEvent {
    private String eventType;              // e.g., "purchase", "page_view", "feature_used"
    private Map<String, Object> eventData; // Flexible payload
    private LocalDateTime timestamp;
    private String source;                 // e.g., "web", "mobile"
}
```

### 4.3 UserEventBatch Model

```java
package com.ai.infrastructure.behavior.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class UserEventBatch {
    private UUID userId;
    private List<ExternalEvent> events;
    private int totalEventCount;
    
    /**
     * Optional user context/metadata for richer LLM analysis.
     * Users can include any data they think is relevant (subscription tier, location, etc.).
     * This is NOT stored by the module; it's only used during LLM prompt generation.
     * 
     * Example: {"subscriptionTier": "premium", "city": "New York", "accountAge": 365}
     */
    private Map<String, Object> userContext;
}
```

### 4.4 BehaviorInsightStore (Custom Storage - Full CRUD)

```java
package com.ai.infrastructure.behavior.spi;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;

import java.util.Optional;
import java.util.UUID;

/**
 * Optional user-implemented interface to fully customize behavior insight storage.
 * If provided, this becomes the SINGLE source of truth for ALL operations (read, write, delete).
 * If not provided, the module uses the default JPA repository.
 * 
 * Use Cases:
 * - Store insights in a separate analytics database
 * - Sync insights to an external CRM (Salesforce, HubSpot)
 * - Send insights to a data lake (S3, BigQuery)
 * - Forward to a Kafka topic for real-time processing
 */
public interface BehaviorInsightStore {
    
    /**
     * Save or update a behavior insight.
     * Called after every analysis completes.
     * 
     * @param insight The analyzed behavioral insight
     */
    void save(BehaviorInsights insight);
    
    /**
     * Fetch the current insight for a user.
     * Called before analysis to enable evolutionary updates.
     * CRITICAL: Must be implemented for stateful evolution to work.
     * 
     * @param userId The user to fetch
     * @return The existing insight, or empty if this is a new user
     */
    Optional<BehaviorInsights> findByUserId(UUID userId);
    
    /**
     * Delete a user's behavioral insight.
     * Called when a user is deleted (GDPR "Right to Erasure").
     * 
     * @param userId The user to delete
     */
    void deleteByUserId(UUID userId);
}
```

---

## 5. Core Service Implementation

### 5.1 BehaviorAnalysisService

```java
package com.ai.infrastructure.behavior.service;

import com.ai.infrastructure.annotation.AIProcess;
import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.model.ExternalEvent;
import com.ai.infrastructure.behavior.model.UserEventBatch;
import com.ai.infrastructure.behavior.repository.BehaviorInsightsRepository;
import com.ai.infrastructure.behavior.spi.BehaviorInsightStore;
import com.ai.infrastructure.behavior.spi.ExternalEventProvider;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorAnalysisService {
    
    private final ExternalEventProvider eventProvider;
    private final BehaviorStorageAdapter storageAdapter;  // Unified storage abstraction
    private final AICoreService aiCoreService;
    private final ObjectMapper objectMapper;
    
    /**
     * CASE 1: Analyze a specific user (Targeted)
     */
    @Transactional
    public BehaviorInsights analyzeUser(UUID userId) {
        log.info("Starting targeted analysis for user: {}", userId);
        
        // Step 1: Fetch existing insight (for evolutionary analysis)
        Optional<BehaviorInsights> existingInsight = storageAdapter.findByUserId(userId);
        
        // Step 2: Pull new events from user's data source
        List<ExternalEvent> newEvents = eventProvider.getEventsForUser(userId);
        
        if (newEvents == null || newEvents.isEmpty()) {
            log.warn("No events found for user: {}", userId);
            return existingInsight.orElse(null);
        }
        
        // Step 3: Perform evolutionary analysis (no user context in targeted mode)
        BehaviorInsights updatedInsight = performEvolutionaryAnalysis(
            userId,
            existingInsight.orElse(null),
            newEvents,
            null  // User context not needed for targeted analysis
        );
        
        // Step 4: Save and trigger indexing
        return saveAndIndex(updatedInsight);
    }
    
    /**
     * CASE 2: Process the next user in queue (Discovery)
     */
    @Transactional
    public BehaviorInsights processNextUser() {
        log.debug("Fetching next user for batch processing");
        
        UserEventBatch batch = eventProvider.getNextUserEvents();
        
        if (batch == null || batch.getUserId() == null) {
            log.debug("No users pending analysis");
            return null;
        }
        
        log.info("Processing batch for user: {} with {} events", 
            batch.getUserId(), batch.getTotalEventCount());
        
        Optional<BehaviorInsights> existingInsight = 
            insightsRepository.findByUserId(batch.getUserId());
        
        BehaviorInsights updatedInsight = performEvolutionaryAnalysis(
            batch.getUserId(),
            existingInsight.orElse(null),
            batch.getEvents(),
            batch.getUserContext()  // Pass user context to LLM
        );
        
        return saveAndIndex(updatedInsight);
    }
    
    /**
     * Core logic: Evolutionary analysis using LLM
     */
    private BehaviorInsights performEvolutionaryAnalysis(
        UUID userId,
        BehaviorInsights oldInsight,
        List<ExternalEvent> newEvents,
        Map<String, Object> userContext
    ) {
        try {
            // Build evolutionary prompt with optional user context
            String prompt = buildEvolutionaryPrompt(oldInsight, newEvents, userContext);
            
            // Call LLM
            AIGenerationResponse response = aiCoreService.generateContent(
                AIGenerationRequest.builder()
                    .entityId(userId.toString())
                    .entityType("behavior-insight")
                    .generationType("behavioral-analysis")
                    .prompt(prompt)
                    .systemPrompt(getSystemPrompt())
                    .temperature(0.3) // Low temperature for consistency
                    .maxTokens(800)
                    .build()
            );
            
            // Parse LLM response
            return parseLLMResponse(userId, response.getContent(), oldInsight);
            
        } catch (Exception e) {
            log.error("Failed to perform evolutionary analysis for user: {}", userId, e);
            
            // Fallback: return existing insight or create minimal one
            if (oldInsight != null) {
                return oldInsight;
            }
            
            return BehaviorInsights.builder()
                .userId(userId)
                .segment("unknown")
                .analyzedAt(LocalDateTime.now())
                .confidence(0.0)
                .aiModelUsed("fallback")
                .build();
        }
    }
    
    /**
     * Build prompt that includes previous state for evolution detection
     */
    private String buildEvolutionaryPrompt(
        BehaviorInsights oldInsight,
        List<ExternalEvent> newEvents,
        Map<String, Object> userContext
    ) {
        StringBuilder prompt = new StringBuilder();
        
        // NEW: Include user context if provided
        if (userContext != null && !userContext.isEmpty()) {
            prompt.append("User Context:\n");
            userContext.forEach((key, value) -> 
                prompt.append("- ").append(key).append(": ").append(value).append("\n")
            );
            prompt.append("\n");
        }
        
        if (oldInsight != null) {
            prompt.append("Previous Analysis:\n");
            prompt.append("- Segment: ").append(oldInsight.getSegment()).append("\n");
            prompt.append("- Patterns: ").append(oldInsight.getPatterns()).append("\n");
            prompt.append("- Analyzed At: ").append(oldInsight.getAnalyzedAt()).append("\n\n");
        } else {
            prompt.append("This is a NEW user with no previous analysis.\n\n");
        }
        
        prompt.append("New Events (").append(newEvents.size()).append("):\n");
        newEvents.forEach(event -> {
            prompt.append("- ").append(event.getEventType())
                  .append(" at ").append(event.getTimestamp())
                  .append(" | Data: ").append(event.getEventData())
                  .append("\n");
        });
        
        prompt.append("\nAnalyze how this user's behavior has evolved. ");
        prompt.append("Consider the user context when generating segments and recommendations. ");
        prompt.append("Provide: segment, patterns, recommendations, and confidence.");
        
        return prompt.toString();
    }
    
    private String getSystemPrompt() {
        return """
            You are a behavioral analyst. Your task is to analyze user actions and provide:
            1. Segment: A category (e.g., "Power User", "At Risk", "Dormant")
            2. Patterns: A list of behavioral patterns observed
            3. Recommendations: Actionable suggestions
            4. Confidence: A score from 0.0 to 1.0
            
            Respond ONLY with valid JSON:
            {
              "segment": "string",
              "patterns": ["pattern1", "pattern2"],
              "recommendations": ["rec1", "rec2"],
              "insights": {"key": "value"},
              "confidence": 0.0-1.0
            }
            """;
    }
    
    private BehaviorInsights parseLLMResponse(
        UUID userId,
        String llmResponse,
        BehaviorInsights oldInsight
    ) throws Exception {
        // Extract JSON from LLM response
        String json = extractJson(llmResponse);
        
        // Parse into structured object
        @SuppressWarnings("unchecked")
        var parsed = objectMapper.readValue(json, java.util.Map.class);
        
        BehaviorInsights.BehaviorInsightsBuilder builder = BehaviorInsights.builder()
            .userId(userId)
            .segment((String) parsed.get("segment"))
            .patterns((List<String>) parsed.get("patterns"))
            .recommendations((List<String>) parsed.get("recommendations"))
            .insights((java.util.Map<String, Object>) parsed.get("insights"))
            .confidence(((Number) parsed.getOrDefault("confidence", 0.5)).doubleValue())
            .analyzedAt(LocalDateTime.now())
            .aiModelUsed("gpt-4o"); // Could be dynamic
        
        // Preserve ID if updating
        if (oldInsight != null) {
            builder.id(oldInsight.getId())
                   .createdAt(oldInsight.getCreatedAt());
        }
        
        return builder.build();
    }
    
    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start == -1 || end <= start) {
            throw new IllegalStateException("No valid JSON in LLM response");
        }
        return response.substring(start, end + 1);
    }
    
    /**
     * Save insight and trigger framework indexing (if FULL mode)
     */
    @AIProcess(
        entityType = "behavior-insight",
        processType = "create"
    )
    private BehaviorInsights saveAndIndex(BehaviorInsights insight) {
        // Save using the unified storage adapter
        // (automatically routes to custom store if provided, otherwise uses JPA)
        BehaviorInsights saved = storageAdapter.save(insight);
        
        // @AIProcess annotation triggers framework indexing here (if enabled in YAML)
        return saved;
    }
}
```

### 5.2 BehaviorStorageAdapter (Routing Layer)

```java
package com.ai.infrastructure.behavior.service;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.repository.BehaviorInsightsRepository;
import com.ai.infrastructure.behavior.spi.BehaviorInsightStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Unified storage abstraction that routes to either:
 * - User's custom BehaviorInsightStore implementation (if provided), OR
 * - Default JPA repository (fallback)
 * 
 * This ensures the module works seamlessly whether the user provides custom storage or not.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BehaviorStorageAdapter {
    
    private final Optional<BehaviorInsightStore> customStore;
    private final BehaviorInsightsRepository defaultRepository;
    
    /**
     * Fetch insight for a user (needed for evolutionary analysis)
     */
    public Optional<BehaviorInsights> findByUserId(UUID userId) {
        if (customStore.isPresent()) {
            log.debug("Fetching from custom store: userId={}", userId);
            return customStore.get().findByUserId(userId);
        }
        return defaultRepository.findByUserId(userId);
    }
    
    /**
     * Save or update insight
     */
    public BehaviorInsights save(BehaviorInsights insight) {
        if (customStore.isPresent()) {
            log.debug("Saving to custom store: userId={}", insight.getUserId());
            customStore.get().save(insight);
            return insight; // Custom stores manage their own IDs
        }
        return defaultRepository.save(insight);
    }
    
    /**
     * Delete insight (GDPR compliance)
     */
    public void deleteByUserId(UUID userId) {
        if (customStore.isPresent()) {
            log.info("Deleting from custom store: userId={}", userId);
            customStore.get().deleteByUserId(userId);
        } else {
            defaultRepository.deleteByUserId(userId);
        }
    }
}
```

### 5.3 Repository (Default Storage)

```java
package com.ai.infrastructure.behavior.repository;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BehaviorInsightsRepository extends JpaRepository<BehaviorInsights, UUID> {
    
    Optional<BehaviorInsights> findByUserId(UUID userId);
    
    void deleteByUserId(UUID userId);
}
```

---

## 6. Configuration System

### 6.1 Addon Auto-Configuration

```java
package com.ai.infrastructure.behavior.config;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "ai.behavior", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@DependsOn("AIEntityConfigurationLoader")  // CRITICAL: Ensures user config loads first
public class BehaviorAIAutoConfiguration {
    
    private final AIEntityConfigurationLoader frameworkConfigLoader;
    
    @Value("${ai.behavior.mode:LIGHT}")
    private String mode;
    
    @PostConstruct
    public void registerBehaviorConfig() {
        // Preset YAML is packaged inside the behavior module JAR
        String presetFile = "classpath:behavior-presets/behavior-ai-" + mode.toLowerCase() + ".yml";
        
        log.info("Registering Behavior Module preset configuration (mode: {})", mode);
        
        // Load with allowOverride=false to respect user's custom config
        frameworkConfigLoader.loadConfigurationFromFile(presetFile, false);
        
        log.info("Behavior AI Addon ready (mode: {})", mode);
    }
}
```

**Key Design:**
1. **`@DependsOn("AIEntityConfigurationLoader")`**: Guarantees the framework loader has already processed the user's `ai-entity-config.yml` before the addon runs.
2. **`allowOverride=false`**: Prevents the addon from overwriting user-defined entity configurations.
3. **`classpath:` prefix**: Spring's `ResourceLoader` automatically resolves this from the JAR.

### 6.2 Module File Structure

The preset YAML files are packaged **inside** the behavior module JAR:

```
ai-infrastructure-behavior/
  src/main/resources/
    behavior-presets/
      behavior-ai-light.yml
      behavior-ai-full.yml
```

**`behavior-presets/behavior-ai-light.yml`**
```yaml
ai-entities:
  behavior-insight:
    auto-embedding: false
    indexable: false
    features: ["analysis"]
    crud-operations:
      create:
        generate-embedding: false
        index-for-search: false
```

**`behavior-presets/behavior-ai-full.yml`**
```yaml
ai-entities:
  behavior-insight:
    auto-embedding: true
    indexable: true
    features: ["embedding", "search", "analysis"]
    searchable-fields:
      - name: segment
        weight: 2.0
      - name: patterns
        weight: 1.5
    metadata-fields:
      - name: segment
        type: string
        include-in-search: true
      - name: confidence
        type: double
        include-in-search: true
    crud-operations:
      create:
        generate-embedding: true
        index-for-search: true
```

### 6.2.1 Framework Enhancement Required

The `AIEntityConfigurationLoader` needs a simple enhancement to support addon-provided presets without overriding user configs:

```java
// Update AIEntityConfigurationLoader.java

/**
 * Load configuration from file with override control.
 * 
 * @param configFile File path (supports "classpath:" prefix for JAR resources)
 * @param allowOverride If false, skips entities already defined by the user
 */
public void loadConfigurationFromFile(String configFile, boolean allowOverride) {
    try {
        Resource resource = resourceLoader.getResource(configFile);
        if (!resource.exists()) {
            log.warn("Configuration file not found: {}", configFile);
            return;
        }
        
        InputStream inputStream = resource.getInputStream();
        Yaml yaml = new Yaml();
        Map<String, Object> config = yaml.load(inputStream);
        
        // Load global configuration
        if (config.containsKey("ai-config")) {
            if (allowOverride) {
                globalConfig.putAll((Map<String, Object>) config.get("ai-config"));
            }
        }
        
        // Load entity configurations
        if (config.containsKey("ai-entities")) {
            Map<String, Object> entities = (Map<String, Object>) config.get("ai-entities");
            for (Map.Entry<String, Object> entry : entities.entrySet()) {
                String entityType = entry.getKey();
                Map<String, Object> entityConfig = (Map<String, Object>) entry.getValue();
                
                // Respect allowOverride flag
                if (allowOverride || !entityConfigs.containsKey(entityType)) {
                    AIEntityConfig configObj = parseEntityConfig(entityType, entityConfig);
                    entityConfigs.put(entityType, configObj);
                    log.info("Loaded config for entity: {} (override={})", entityType, allowOverride);
                } else {
                    log.debug("Skipping {} - user config takes precedence", entityType);
                }
            }
        }
        
    } catch (Exception e) {
        log.error("Failed to load configuration from: {}", configFile, e);
        throw new RuntimeException("Failed to load configuration from: " + configFile, e);
    }
}

/**
 * Backward compatibility: Load with override enabled (existing behavior)
 */
public void loadConfigurationFromFile(String configFile) {
    loadConfigurationFromFile(configFile, true);
}
```

**Why This Works:**
- Spring's `ResourceLoader.getResource()` already supports `classpath:` prefix
- No new method needed - just add a parameter to control override behavior
- Backward compatible - existing calls default to `allowOverride=true`

### 6.3 User Configuration (`application.yml`)

```yaml
ai:
  behavior:
    enabled: true
    mode: FULL  # or LIGHT
    
    # Optional: Advanced settings
    analysis:
      cooldown-hours: 12  # Don't re-analyze within this window
      min-event-threshold: 5  # Minimum events needed to trigger analysis
```

---

## 7. Integration with AI Framework

### 7.1 Relationship Query Integration

The module automatically registers with the `RelationshipQueryPlanner` so users can query behavior insights alongside other entities.

```java
package com.ai.infrastructure.behavior.config;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.relationship.service.EntityRelationshipMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(EntityRelationshipMapper.class)  // Only if RelationshipQuery module is present
@RequiredArgsConstructor
public class BehaviorRelationshipRegistration {
    
    private final EntityRelationshipMapper relationshipMapper;
    
    @PostConstruct
    public void registerRelationships() {
        log.info("Registering BehaviorInsights with RelationshipQuery module");
        
        // Register the behavior entity type
        relationshipMapper.registerEntityType(BehaviorInsights.class);
        
        log.info("BehaviorInsights registered as 'behavior-insight' entity type");
    }
}
```

### 7.2 Example Behavioral Queries (FULL Mode)

Once registered, users can query behavior insights directly:

```java
@Autowired
private RelationshipQueryService queryService;

// Example 1: Find users by behavioral segment
RAGResponse response = queryService.execute(
    "Find all users in the Power User segment",
    List.of("behavior-insight")
);

// Example 2: Statistics query
RAGResponse stats = queryService.execute(
    "How many users have churn risk above 0.8?",
    List.of("behavior-insight")
);

// Example 3: Pattern-based query
RAGResponse patterns = queryService.execute(
    "Find users showing dormant behavior patterns with high previous engagement",
    List.of("behavior-insight")
);
```

**How it works:**
1. The `RelationshipQueryPlanner` generates a query plan for `behavior-insight` entities
2. The framework uses `AISearchableEntity` metadata to filter by behavioral attributes
3. Results contain `userId` which you can use to fetch full user details if needed

**Note:** The module does NOT join with User tables. It only returns behavioral data. If you need user details, fetch them separately using the returned `userId` values.

---

## 8. Error Handling & Resilience

### 8.1 Retry Strategy

```java
@Service
public class ResilientBehaviorAnalysisService {
    
    @Retryable(
        value = {LLMTimeoutException.class, NetworkException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public BehaviorInsights analyzeUserWithRetry(UUID userId) {
        return behaviorAnalysisService.analyzeUser(userId);
    }
    
    @Recover
    public BehaviorInsights recoverFromFailure(Exception e, UUID userId) {
        log.error("All retry attempts failed for user: {}", userId, e);
        
        // Return cached or degraded result
        return insightsRepository.findByUserId(userId)
            .orElse(createMinimalInsight(userId));
    }
}
```

### 8.2 Circuit Breaker

```java
@Service
public class ProtectedBehaviorService {
    
    @CircuitBreaker(name = "behaviorAnalysis", fallbackMethod = "fallbackAnalysis")
    public BehaviorInsights analyzeUser(UUID userId) {
        return behaviorAnalysisService.analyzeUser(userId);
    }
    
    private BehaviorInsights fallbackAnalysis(UUID userId, Exception e) {
        log.warn("Circuit breaker triggered for user: {}", userId);
        return insightsRepository.findByUserId(userId).orElse(null);
    }
}
```

---

## 9. Performance Considerations

### 9.1 Caching Strategy

```java
@Service
public class CachedBehaviorService {
    
    @Cacheable(value = "behaviorInsights", key = "#userId")
    public BehaviorInsights getInsight(UUID userId) {
        return insightsRepository.findByUserId(userId).orElse(null);
    }
    
    @CacheEvict(value = "behaviorInsights", key = "#userId")
    public void invalidateCache(UUID userId) {
        log.debug("Cache invalidated for user: {}", userId);
    }
}
```

### 9.2 Batch Processing

```java
@Service
public class BatchBehaviorProcessor {
    
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void processBatch() {
        int batchSize = 50;
        int processed = 0;
        
        while (processed < batchSize) {
            BehaviorInsights result = behaviorAnalysisService.processNextUser();
            if (result == null) break;
            processed++;
        }
        
        log.info("Batch processing complete. Processed {} users", processed);
    }
}
```

---

## 10. Testing Strategy

### 10.1 Unit Tests

```java
@ExtendWith(MockitoExtension.class)
class BehaviorAnalysisServiceTest {
    
    @Mock
    private ExternalEventProvider eventProvider;
    
    @Mock
    private BehaviorInsightsRepository repository;
    
    @Mock
    private AICoreService aiCoreService;
    
    @InjectMocks
    private BehaviorAnalysisService service;
    
    @Test
    void testEvolutionaryAnalysis_NewUser() {
        UUID userId = UUID.randomUUID();
        
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());
        when(eventProvider.getEventsForUser(userId)).thenReturn(mockEvents());
        when(aiCoreService.generateContent(any())).thenReturn(mockLLMResponse());
        
        BehaviorInsights result = service.analyzeUser(userId);
        
        assertNotNull(result);
        assertEquals("Power User", result.getSegment());
        verify(repository).save(any());
    }
}
```

### 10.2 Integration Tests

```java
@SpringBootTest
@Testcontainers
class BehaviorModuleIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    @Autowired
    private BehaviorAnalysisService service;
    
    @Test
    void testFullFlow_LightMode() {
        // Given a test user
        UUID userId = UUID.randomUUID();
        
        // When analysis is triggered
        BehaviorInsights result = service.analyzeUser(userId);
        
        // Then insight is saved but NOT indexed
        assertNotNull(result);
        // Verify AISearchableEntity was NOT created (LIGHT mode)
    }
}
```

---

## 11. Implementation Sequence

### Phase 1: Foundation (Week 1)
- [ ] Implement `BehaviorInsights` entity with JPA annotations
- [ ] Create `BehaviorInsightsRepository` interface
- [ ] Write example schema scripts for PostgreSQL, MySQL, H2 (in `docs/database/`)
- [ ] Test with Hibernate auto-DDL in development profile
- [ ] Document schema requirements in README

### Phase 2: SPIs (Week 1-2)
- [ ] Define `ExternalEventProvider` interface
- [ ] Define `BehaviorInsightStore` interface
- [ ] Create model classes (`ExternalEvent`, `UserEventBatch`)
- [ ] Implement default JPA-based `BehaviorInsightStore`

### Phase 3: Core Logic (Week 2)
- [ ] Implement `BehaviorAnalysisService`
- [ ] Build evolutionary prompt logic
- [ ] Integrate with `AICoreService`
- [ ] Add LLM response parsing

### Phase 4: Configuration (Week 2-3)
- [ ] Create `BehaviorAIAutoConfiguration`
- [ ] Write preset YAML files (LIGHT/FULL)
- [ ] Implement "User-First" loading logic

### Phase 5: Framework Integration (Week 3)
- [ ] Add `@AIProcess` annotation to save method
- [ ] Register with `RelationshipQueryPlanner`
- [ ] Test FULL mode indexing

### Phase 6: Production Ready (Week 4)
- [ ] Add retry/circuit breaker
- [ ] Implement caching
- [ ] Write comprehensive tests
- [ ] Performance tuning

---

## Appendix A: Example Implementation of ExternalEventProvider

```java
@Component
public class MyAppEventProvider implements ExternalEventProvider {
    
    @Autowired
    private MyEventRepository eventRepository;
    
    @Autowired
    private UserRepository userRepository;  // Your existing User repository
    
    @Value("${ai.behavior.analysis.default-window-days:30}")
    private int defaultWindowDays;
    
    @Value("${ai.behavior.analysis.max-events-per-analysis:1000}")
    private int maxEvents;
    
    @Override
    public List<ExternalEvent> getEventsForUser(
        UUID userId,
        LocalDateTime since,
        LocalDateTime until
    ) {
        // Apply defaults if not provided
        LocalDateTime effectiveSince = since != null 
            ? since 
            : LocalDateTime.now().minusDays(defaultWindowDays);
            
        LocalDateTime effectiveUntil = until != null 
            ? until 
            : LocalDateTime.now();
        
        // Fetch from your event store with time window
        return eventRepository
            .findByUserIdAndTimestampBetween(userId, effectiveSince, effectiveUntil)
            .stream()
            .limit(maxEvents) // Safety cap
            .map(this::toExternalEvent)
            .collect(Collectors.toList());
    }
    
    @Override
    public UserEventBatch getNextUserEvents() {
        // Strategy: Find user with oldest analysis or highest unprocessed event count
        UUID nextUser = eventRepository.findNextUserForAnalysis();
        if (nextUser == null) return null;
        
        // For discovery mode, use a reasonable default window
        LocalDateTime since = LocalDateTime.now().minusDays(7);  // Last week
        List<ExternalEvent> events = getEventsForUser(nextUser, since, null);
        
        // NEW: Optionally include user context for richer LLM analysis
        Map<String, Object> userContext = buildUserContext(nextUser);
        
        return UserEventBatch.builder()
            .userId(nextUser)
            .events(events)
            .totalEventCount(events.size())
            .userContext(userContext)  // ← User decides what context matters
            .build();
    }
    
    /**
     * Build user context from your existing User entity.
     * YOU decide what's relevant for behavioral analysis.
     */
    private Map<String, Object> buildUserContext(UUID userId) {
        return userRepository.findById(userId)
            .map(user -> Map.of(
                "subscriptionTier", user.getSubscriptionTier(),
                "accountAgeInDays", ChronoUnit.DAYS.between(user.getCreatedAt(), LocalDateTime.now()),
                "location", user.getCity() + ", " + user.getCountry(),
                "totalSpent", user.getTotalOrderValue(),
                "preferredLanguage", user.getLanguage()
                // Add any data you think helps the LLM understand behavior
            ))
            .orElse(Map.of());
    }
    
    private ExternalEvent toExternalEvent(MyEventEntity entity) {
        return ExternalEvent.builder()
            .eventType(entity.getType())
            .eventData(entity.getData())
            .timestamp(entity.getCreatedAt())
            .source(entity.getSource())
            .build();
    }
}
```

## Appendix B: Example Implementation of BehaviorInsightStore

```java
@Component
public class CustomCRMInsightStore implements BehaviorInsightStore {
    
    @Autowired
    private CRMApiClient crmClient;
    
    @Override
    public void save(BehaviorInsights insight) {
        // Example: Sync to Salesforce
        crmClient.updateContact(
            insight.getUserId().toString(),
            Map.of(
                "Behavioral_Segment__c", insight.getSegment(),
                "Churn_Risk__c", calculateChurnRisk(insight),
                "Last_Analyzed__c", insight.getAnalyzedAt()
            )
        );
    }
    
    @Override
    public Optional<BehaviorInsights> findByUserId(UUID userId) {
        // Fetch from CRM if you store it there, or return empty to always do fresh analysis
        return crmClient.getContactBehavior(userId.toString())
            .map(this::toBehaviorInsights);
    }
    
    @Override
    public void deleteByUserId(UUID userId) {
        // GDPR compliance: Remove behavioral data from CRM
        crmClient.deleteBehavioralData(userId.toString());
    }
    
    private Double calculateChurnRisk(BehaviorInsights insight) {
        // Extract risk from insights map
        return insight.getInsights() != null 
            ? (Double) insight.getInsights().get("churnRisk") 
            : 0.0;
    }
    
    private BehaviorInsights toBehaviorInsights(CRMContact contact) {
        // Map CRM data back to BehaviorInsights entity
        return BehaviorInsights.builder()
            .userId(UUID.fromString(contact.getId()))
            .segment(contact.getField("Behavioral_Segment__c"))
            .analyzedAt(contact.getField("Last_Analyzed__c"))
            .build();
    }
}
```

---

**End of Technical Specification**

