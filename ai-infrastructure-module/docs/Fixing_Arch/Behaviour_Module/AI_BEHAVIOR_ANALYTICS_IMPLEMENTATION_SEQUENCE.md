# AI Behavior Analytics: Implementation Sequence

**Version:** 1.0  
**Status:** Ready for Implementation  
**Last Updated:** November 2025  
**Target Timeline:** 4-6 weeks (2 developers)

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Phase 1: Foundation](#phase-1-foundation-weeks-1)
4. [Phase 2: Event Pipeline](#phase-2-event-pipeline-weeks-1-2)
5. [Phase 3: AI Analysis](#phase-3-ai-analysis-weeks-2-3)
6. [Phase 4: Search & Orchestration](#phase-4-search--orchestration-weeks-3-4)
7. [Phase 5: Integration Testing](#phase-5-integration-testing-weeks-4-5)
8. [Phase 6: Production Hardening](#phase-6-production-hardening-weeks-5-6)
9. [Rollout Strategy](#rollout-strategy)

---

## 🎯 OVERVIEW

This document provides a **step-by-step implementation sequence** for the AI Behavior Analytics microservice, referencing exact code patterns, configuration files, and integration points from the architecture documents.

**Key Principle:** Each phase has clear deliverables, with exact file paths, code examples, and integration tests.

---

## ✅ PREREQUISITES

Before starting, ensure:

- [ ] AI Core module is deployed (with `@AICapable` and `@AIProcess` support)
- [ ] RAGOrchestrator is available as a service
- [ ] PIIDetectionService is accessible from AI Core
- [ ] PostgreSQL 13+ database available
- [ ] Redis cache available (optional but recommended)
- [ ] Maven 3.8+ and Java 21 installed
- [ ] Docker & Testcontainers configured

### Reference
- See: `AI_BEHAVIOR_ANALYTICS_MICROSERVICE_ARCHITECTURE.md` → "Prerequisites" section

---

## 🏗️ PHASE 1: FOUNDATION (Week 1)

### 1.1 Module Structure & Dependencies

**Goal:** Set up the Maven module structure and add all required dependencies.

#### Task 1.1.1: Create Module Directory Structure

```bash
ai-infrastructure-behavior/
├── src/main/java/com/ai/behavior/
│   ├── config/                      # Configuration classes
│   ├── controller/                  # REST endpoints
│   ├── model/                       # JPA entities and DTOs
│   ├── repository/                  # Spring Data repositories
│   ├── service/                     # Business logic services
│   ├── worker/                      # Async workers & schedulers
│   ├── policy/                      # Policy hook interfaces & implementations
│   └── util/                        # Utility classes
├── src/main/resources/
│   ├── application.yml              # Default configuration
│   ├── application-dev.yml          # Development overrides
│   ├── db/changelog/                # Liquibase migrations
│   └── behavior/schemas/            # Behavior signal schemas
├── src/test/java/
│   └── com/ai/behavior/
│       ├── unit/                    # Unit tests
│       ├── integration/             # Integration tests
│       └── BehaviourTests/          # Existing tests (from Phase 1)
└── pom.xml                          # Module POM

```

#### Task 1.1.2: Update `pom.xml`

**File:** `ai-infrastructure-module/ai-infrastructure-behavior/pom.xml`

Add these dependencies:

```xml
<!-- AI Core (for @AICapable, @AIProcess, RAGOrchestrator) -->
<dependency>
    <groupId>com.ai</groupId>
    <artifactId>ai-core</artifactId>
    <version>${ai-core.version}</version>
</dependency>

<!-- Spring Data JPA -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- PostgreSQL Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Liquibase for migrations -->
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
</dependency>

<!-- Spring Cache (Redis optional backend) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>

<!-- Redis (optional, for caching) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
    <optional>true</optional>
</dependency>

<!-- Spring Scheduling -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-scheduling</artifactId>
</dependency>

<!-- Testcontainers for integration tests -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>

<!-- JUnit 5 -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

**Reference:** See `AI_BEHAVIOR_ANALYTICS_MODULE_V2_IMPLEMENTATION_CHECKLIST.md` → "Dependencies" section

#### Task 1.1.3: Create Base Configuration Class

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/config/BehaviorModuleConfiguration.java`

```java
package com.ai.behavior.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableScheduling;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableScheduling
@EnableCaching
@EnableAsync
public class BehaviorModuleConfiguration {
    // Configuration beans will be added in later phases
}
```

#### Task 1.1.4: Create Configuration Properties Class

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/config/BehaviorModuleProperties.java`

```java
package com.ai.behavior.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.behavior")
public class BehaviorModuleProperties {
    
    private Events events = new Events();
    private Processing processing = new Processing();
    private Retention retention = new Retention();
    private Search search = new Search();
    
    @Data
    public static class Events {
        private int batchSize = 100;
        private int processingTimeoutSeconds = 30;
        private String storageType = "postgresql";  // postgresql, mongodb
    }
    
    @Data
    public static class Processing {
        private Analyzer analyzer = new Analyzer();
        private Worker worker = new Worker();
        
        @Data
        public static class Analyzer {
            private double engagementThreshold = 0.75;
            private double recencyThreshold = 0.6;
            private boolean enableAiEnrichment = true;
        }
        
        @Data
        public static class Worker {
            private int poolSize = 5;
            private int delaySeconds = 300;  // 5 minutes
            private int maxRetries = 3;
        }
    }
    
    @Data
    public static class Retention {
        private int tempEventsTtlDays = 30;
        private int insightRetentionDays = 90;
        private String cleanupSchedule = "0 3 * * *";  // 3 AM daily
    }
    
    @Data
    public static class Search {
        private Orchestrated orchestrated = new Orchestrated();
        
        @Data
        public static class Orchestrated {
            private boolean enabled = true;
            private boolean piiDetectionEnabled = true;
            private int maxResults = 100;
            private int timeoutSeconds = 30;
            private RateLimit rateLimit = new RateLimit();
            
            @Data
            public static class RateLimit {
                private String perUser = "100/hour";
                private String perEndpoint = "1000/hour";
            }
        }
    }
}
```

**Reference:** See `AI_BEHAVIOR_ANALYTICS_MODULE_V2_PHILOSOPHY.md` → "Configuration" section

---

## 🗄️ PHASE 2: EVENT PIPELINE (Weeks 1-2)

### 2.1 Database Schema

#### Task 2.1.1: Create Database Migrations

**File:** `ai-infrastructure-behavior/src/main/resources/db/changelog/V001__create_behavior_tables.sql`

```sql
-- Temporary events table (with TTL)
CREATE TABLE ai_behavior_events_temp (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_data JSONB NOT NULL,
    source VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed BOOLEAN DEFAULT FALSE,
    processing_status VARCHAR(50),
    retry_count INT DEFAULT 0,
    expires_at TIMESTAMP,
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_temp_events_user_created 
ON ai_behavior_events_temp(user_id, created_at DESC);

CREATE INDEX idx_temp_events_processed_expires 
ON ai_behavior_events_temp(processed, expires_at);

CREATE INDEX idx_temp_events_expires 
ON ai_behavior_events_temp(expires_at) 
WHERE expires_at IS NOT NULL;

-- Failed events tracking
CREATE TABLE ai_behavior_events_failed (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_data JSONB NOT NULL,
    error_reason TEXT,
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    manual_review_required BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_failed_events_user_created 
ON ai_behavior_events_failed(user_id, created_at DESC);

CREATE INDEX idx_failed_events_review 
ON ai_behavior_events_failed(manual_review_required);
```

**Reference:** See `AI_BEHAVIOR_ANALYTICS_MODULE_V2_IMPLEMENTATION_CHECKLIST.md` → "Phase 1: Data Layer"

#### Task 2.1.2: Configure Liquibase

**File:** `ai-infrastructure-behavior/src/main/resources/application.yml`

```yaml
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.xml
    liquibase-schema: public

ai:
  behavior:
    events:
      batchSize: 100
      processingTimeoutSeconds: 30
    processing:
      analyzer:
        engagementThreshold: 0.75
        enableAiEnrichment: true
      worker:
        poolSize: 5
        delaySeconds: 300
        maxRetries: 3
    retention:
      tempEventsTtlDays: 30
      insightRetentionDays: 90
      cleanupSchedule: "0 3 * * *"
    search:
      orchestrated:
        enabled: true
        piiDetectionEnabled: true
        maxResults: 100
        timeoutSeconds: 30
```

### 2.2 Entity Models

#### Task 2.2.1: Create BehaviorEventEntity

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/model/BehaviorEventEntity.java`

```java
package com.ai.behavior.model;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.type.JsonType;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "ai_behavior_events_temp", indexes = {
    @Index(name = "idx_events_user_created", columnList = "user_id, created_at DESC"),
    @Index(name = "idx_events_processed_expires", columnList = "processed, expires_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BehaviorEventEntity {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false, length = 255)
    private String eventType;  // e.g., "engagement.view", "conversion.purchase"
    
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode eventData;
    
    @Column(length = 100)
    private String source;  // e.g., "mobile_app", "web", "batch_import"
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private Boolean processed = false;
    
    @Column(length = 50)
    private String processingStatus;  // PENDING, IN_PROGRESS, COMPLETED, FAILED
    
    @Column(nullable = false)
    private Integer retryCount = 0;
    
    @Column
    private LocalDateTime expiresAt;  // TTL: For database cleanup
    
    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (processed == null) {
            processed = false;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }
}
```

#### Task 2.2.2: Update BehaviorInsights Entity with @AICapable

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/model/BehaviorInsights.java`

Existing file - **ADD THESE ANNOTATIONS:**

```java
package com.ai.behavior.model;

import com.ai.core.annotation.AICapable;
import com.ai.core.annotation.AIProcess;
import com.ai.core.enums.AIProcessStrategy;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

@Entity
@Table(name = "ai_behavior_insights", indexes = {
    @Index(name = "idx_insights_user_analyzed", columnList = "user_id, analyzed_at DESC"),
    @Index(name = "idx_insights_created", columnList = "created_at DESC")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@AICapable  // ← TELL AI CORE THIS ENTITY IS SEARCHABLE
public class BehaviorInsights {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID userId;
    
    @ElementCollection
    @CollectionTable(name = "behavior_insights_patterns", joinColumns = @JoinColumn(name = "insight_id"))
    @Column(name = "pattern")
    private List<String> patterns;  // e.g., ["power_user", "mobile_preference"]
    
    @ElementCollection
    @CollectionTable(name = "behavior_insights_recommendations", joinColumns = @JoinColumn(name = "insight_id"))
    @Column(name = "recommendation")
    private List<String> recommendations;  // e.g., ["loyalty_program", "early_access"]
    
    @Column(columnDefinition = "TEXT")
    private String jsonInsights;  // Full insights JSON
    
    @Column(length = 100)
    private String segment;  // "vip", "loyal", "at_risk", etc.
    
    @Column
    private Double confidence;  // 0.0 - 1.0
    
    @Column(length = 100)
    private String aiModelUsed;  // "gpt-4", "claude", etc.
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime analyzedAt;
    
    @Column
    private LocalDateTime retentionUntil;
    
    // ← NEW: Called automatically by AI Core after entity is saved
    @AIProcess(
        strategy = AIProcessStrategy.ASYNC,
        type = "behavior-insight",
        description = "Index behavior insights for semantic search"
    )
    public void notifyInsightsReady() {
        // AI Core will:
        // 1. Create embedding from this entity
        // 2. Create AISearchableEntity
        // 3. Index in vector DB + full-text
        // 4. All configured async!
    }
    
    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (analyzedAt == null) {
            analyzedAt = LocalDateTime.now();
        }
    }
}
```

**Reference:** See `AI_BEHAVIOR_ANALYTICS_MICROSERVICE_ARCHITECTURE.md` → "Core Concept" section

### 2.3 Repositories

#### Task 2.3.1: Create Event Repository

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/repository/BehaviorEventRepository.java`

```java
package com.ai.behavior.repository;

import com.ai.behavior.model.BehaviorEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface BehaviorEventRepository extends JpaRepository<BehaviorEventEntity, UUID> {
    
    List<BehaviorEventEntity> findByUserIdAndProcessedFalse(UUID userId);
    
    List<BehaviorEventEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    @Query("SELECT e FROM BehaviorEventEntity e WHERE e.processed = false ORDER BY e.createdAt ASC LIMIT :limit")
    List<BehaviorEventEntity> findUnprocessedEvents(int limit);
    
    @Query("SELECT e FROM BehaviorEventEntity e WHERE e.expiresAt IS NOT NULL AND e.expiresAt < CURRENT_TIMESTAMP")
    List<BehaviorEventEntity> findExpiredEvents();
    
    long countByUserIdAndProcessedFalse(UUID userId);
}
```

#### Task 2.3.2: Create Insights Repository

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/repository/BehaviorInsightsRepository.java`

```java
package com.ai.behavior.repository;

import com.ai.behavior.model.BehaviorInsights;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BehaviorInsightsRepository extends JpaRepository<BehaviorInsights, UUID> {
    
    Optional<BehaviorInsights> findByUserId(UUID userId);
    
    List<BehaviorInsights> findByUserIdOrderByAnalyzedAtDesc(UUID userId);
    
    List<BehaviorInsights> findBySegment(String segment);
    
    List<BehaviorInsights> findByRetentionUntilBefore(LocalDateTime date);
    
    long countBySegment(String segment);
}
```

### 2.4 Event Ingestion Service

#### Task 2.4.1: Create BehaviorEventIngestionService

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/service/BehaviorEventIngestionService.java`

```java
package com.ai.behavior.service;

import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.repository.BehaviorEventRepository;
import com.ai.behavior.config.BehaviorModuleProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BehaviorEventIngestionService {
    
    private final BehaviorEventRepository eventRepository;
    private final BehaviorModuleProperties properties;
    private final ObjectMapper objectMapper;
    
    /**
     * Ingest single event (non-blocking)
     * Called from: POST /api/behavior/events
     */
    @Transactional
    public BehaviorEventEntity ingestSingleEvent(
        UUID userId,
        String eventType,
        String eventDataJson,
        String source
    ) {
        try {
            JsonNode eventData = objectMapper.readTree(eventDataJson);
            
            LocalDateTime expiresAt = LocalDateTime.now()
                .plusDays(properties.getRetention().getTempEventsTtlDays());
            
            BehaviorEventEntity event = BehaviorEventEntity.builder()
                .userId(userId)
                .eventType(eventType)
                .eventData(eventData)
                .source(source)
                .processed(false)
                .processingStatus("PENDING")
                .expiresAt(expiresAt)
                .retryCount(0)
                .build();
            
            BehaviorEventEntity saved = eventRepository.save(event);
            log.info("Ingested event: userId={}, eventType={}, id={}", 
                userId, eventType, saved.getId());
            
            return saved;
        } catch (Exception e) {
            log.error("Failed to ingest event: {}", e.getMessage(), e);
            throw new RuntimeException("Event ingestion failed", e);
        }
    }
    
    /**
     * Ingest batch of events (non-blocking)
     * Called from: POST /api/behavior/events/batch
     */
    @Transactional
    public List<BehaviorEventEntity> ingestBatchEvents(List<BehaviorEventEntity> events) {
        try {
            LocalDateTime expiresAt = LocalDateTime.now()
                .plusDays(properties.getRetention().getTempEventsTtlDays());
            
            List<BehaviorEventEntity> enrichedEvents = new ArrayList<>();
            for (BehaviorEventEntity event : events) {
                if (event.getExpiresAt() == null) {
                    event.setExpiresAt(expiresAt);
                }
                if (event.getProcessed() == null) {
                    event.setProcessed(false);
                }
                if (event.getProcessingStatus() == null) {
                    event.setProcessingStatus("PENDING");
                }
                enrichedEvents.add(event);
            }
            
            List<BehaviorEventEntity> saved = eventRepository.saveAll(enrichedEvents);
            log.info("Ingested batch: {} events", saved.size());
            
            return saved;
        } catch (Exception e) {
            log.error("Failed to ingest batch: {}", e.getMessage(), e);
            throw new RuntimeException("Batch ingestion failed", e);
        }
    }
    
    /**
     * Get unprocessed events for analysis
     */
    public List<BehaviorEventEntity> getUnprocessedEvents(int limit) {
        return eventRepository.findUnprocessedEvents(limit);
    }
    
    /**
     * Mark event as processed
     */
    @Transactional
    public void markProcessed(UUID eventId) {
        BehaviorEventEntity event = eventRepository.findById(eventId)
            .orElseThrow(() -> new RuntimeException("Event not found"));
        event.setProcessed(true);
        event.setProcessingStatus("COMPLETED");
        eventRepository.save(event);
    }
}
```

**Reference:** See `AI_BEHAVIOR_ANALYTICS_MICROSERVICE_ARCHITECTURE.md` → "Layer 1: Event Ingestion"

---

## 🤖 PHASE 3: AI ANALYSIS (Weeks 2-3)

### 3.1 Policy Hook Interfaces

#### Task 3.1.1: Create BehaviorAnalysisPolicy Hook Interface

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/policy/BehaviorAnalysisPolicy.java`

```java
package com.ai.behavior.policy;

import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.model.BehaviorInsights;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hook interface for domain-specific behavior analysis logic.
 * 
 * Implementations allow customers to define their own:
 * - Pattern detection rules
 * - Segmentation criteria
 * - Recommendation generation
 * - Confidence scoring
 * 
 * Example: E-Commerce vs SaaS have DIFFERENT business logic
 * - E-Commerce: "high_value = total_purchase > 5000"
 * - SaaS: "power_user = login_days > 20 && feature_usage > 0.8"
 */
public interface BehaviorAnalysisPolicy {
    
    /**
     * Detect patterns from behavior signals
     */
    List<String> detectPatterns(
        UUID userId,
        List<BehaviorEventEntity> events,
        Map<String, Double> scores
    );
    
    /**
     * Determine user segment
     */
    String determineSegment(
        UUID userId,
        List<String> patterns,
        Map<String, Double> scores
    );
    
    /**
     * Generate recommendations
     */
    List<String> generateRecommendations(
        UUID userId,
        String segment,
        List<String> patterns,
        Map<String, Double> scores
    );
    
    /**
     * Calculate confidence score (0.0 - 1.0)
     */
    Double calculateConfidence(
        UUID userId,
        List<BehaviorEventEntity> events,
        String segment
    );
}
```

#### Task 3.1.2: Create Default Implementation

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/policy/DefaultBehaviorAnalysisPolicy.java`

```java
package com.ai.behavior.policy;

import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.config.BehaviorModuleProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.UUID;

/**
 * Default domain-agnostic policy.
 * Customers should override with their own @Component implementation
 * of BehaviorAnalysisPolicy.
 */
@Component
@ConditionalOnMissingBean(BehaviorAnalysisPolicy.class)
@RequiredArgsConstructor
@Slf4j
public class DefaultBehaviorAnalysisPolicy implements BehaviorAnalysisPolicy {
    
    private final BehaviorModuleProperties properties;
    
    @Override
    public List<String> detectPatterns(
        UUID userId,
        List<BehaviorEventEntity> events,
        Map<String, Double> scores
    ) {
        List<String> patterns = new ArrayList<>();
        
        if (events.isEmpty()) {
            patterns.add("insufficient_data");
            return patterns;
        }
        
        // Use configurable thresholds (not hardcoded!)
        double engagementThreshold = properties.getProcessing()
            .getAnalyzer().getEngagementThreshold();
        
        if (scores.getOrDefault("engagement", 0.0) >= engagementThreshold) {
            patterns.add("high_engagement");
        }
        
        if (scores.getOrDefault("recency", 0.0) >= 0.6) {
            patterns.add("active_user");
        }
        
        // Count events by type
        Map<String, Long> eventTypeCounts = new HashMap<>();
        for (BehaviorEventEntity event : events) {
            eventTypeCounts.put(event.getEventType(),
                eventTypeCounts.getOrDefault(event.getEventType(), 0L) + 1);
        }
        
        if (eventTypeCounts.values().stream().max(Long::compare)
            .orElse(0L) > 5) {
            patterns.add("consistent_behavior");
        }
        
        log.info("Detected patterns for user {}: {}", userId, patterns);
        return patterns;
    }
    
    @Override
    public String determineSegment(
        UUID userId,
        List<String> patterns,
        Map<String, Double> scores
    ) {
        if (patterns.contains("high_engagement") && 
            patterns.contains("active_user")) {
            return "engaged";
        } else if (patterns.contains("active_user")) {
            return "active";
        } else if (patterns.contains("insufficient_data")) {
            return "unknown";
        } else {
            return "passive";
        }
    }
    
    @Override
    public List<String> generateRecommendations(
        UUID userId,
        String segment,
        List<String> patterns,
        Map<String, Double> scores
    ) {
        List<String> recommendations = new ArrayList<>();
        
        if ("engaged".equals(segment)) {
            recommendations.add("loyalty_program");
            recommendations.add("vip_benefits");
        } else if ("active".equals(segment)) {
            recommendations.add("regular_communication");
        } else if ("unknown".equals(segment)) {
            recommendations.add("collect_additional_signals");
        } else {
            recommendations.add("re_engagement_campaign");
        }
        
        return recommendations;
    }
    
    @Override
    public Double calculateConfidence(
        UUID userId,
        List<BehaviorEventEntity> events,
        String segment
    ) {
        if (events.isEmpty()) {
            return 0.0;
        }
        
        // Confidence based on event count
        // 1-2 events: 0.3, 3-5: 0.6, 5+: 0.9
        int eventCount = events.size();
        if (eventCount <= 2) {
            return 0.3;
        } else if (eventCount <= 5) {
            return 0.6;
        } else {
            return 0.9;
        }
    }
}
```

**Reference:** See `COUPLING_QUICK_ANSWER.txt` → "Solution 2: Hook-Based Architecture"

### 3.2 Analysis Service

#### Task 3.2.1: Create BehaviorAnalyzerService

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/service/BehaviorAnalyzerService.java`

```java
package com.ai.behavior.service;

import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.policy.BehaviorAnalysisPolicy;
import com.ai.behavior.repository.BehaviorInsightsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BehaviorAnalyzerService {
    
    private final BehaviorAnalysisPolicy policy;  // ← Injected hook!
    private final BehaviorInsightsRepository insightsRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Analyze user behavior and create insights
     * Uses injected policy hook (domain-specific logic)
     */
    @Transactional
    public BehaviorInsights analyzeUserBehavior(
        UUID userId,
        List<BehaviorEventEntity> events
    ) {
        try {
            log.info("Analyzing behavior for user: {}", userId);
            
            if (events.isEmpty()) {
                return createEmptyInsights(userId);
            }
            
            // Step 1: Compute scores (generic)
            Map<String, Double> scores = computeScores(events);
            log.debug("Computed scores for user {}: {}", userId, scores);
            
            // Step 2: Detect patterns (USES POLICY HOOK!)
            List<String> patterns = policy.detectPatterns(userId, events, scores);
            log.info("Detected patterns: {}", patterns);
            
            // Step 3: Determine segment (USES POLICY HOOK!)
            String segment = policy.determineSegment(userId, patterns, scores);
            log.info("Determined segment: {}", segment);
            
            // Step 4: Generate recommendations (USES POLICY HOOK!)
            List<String> recommendations = policy.generateRecommendations(
                userId, segment, patterns, scores
            );
            log.info("Generated recommendations: {}", recommendations);
            
            // Step 5: Calculate confidence (USES POLICY HOOK!)
            Double confidence = policy.calculateConfidence(userId, events, segment);
            log.info("Calculated confidence: {}", confidence);
            
            // Step 6: Build insights
            BehaviorInsights insights = BehaviorInsights.builder()
                .userId(userId)
                .patterns(patterns)
                .recommendations(recommendations)
                .segment(segment)
                .confidence(confidence)
                .aiModelUsed("default")
                .analyzedAt(LocalDateTime.now())
                .retentionUntil(LocalDateTime.now().plusDays(90))
                .jsonInsights(objectMapper.writeValueAsString(Map.of(
                    "patterns", patterns,
                    "recommendations", recommendations,
                    "segment", segment,
                    "scores", scores,
                    "confidence", confidence
                )))
                .build();
            
            // Step 7: Save
            BehaviorInsights saved = insightsRepository.save(insights);
            
            // AI Core automatically picks up @AIProcess annotation here!
            // No manual indexing needed!
            
            log.info("Insights created for user {}: id={}", userId, saved.getId());
            return saved;
            
        } catch (Exception e) {
            log.error("Failed to analyze behavior for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Analysis failed", e);
        }
    }
    
    /**
     * Compute basic engagement/recency scores
     */
    private Map<String, Double> computeScores(List<BehaviorEventEntity> events) {
        Map<String, Double> scores = new HashMap<>();
        
        int eventCount = events.size();
        double engagement = Math.min(eventCount / 10.0, 1.0);  // Normalize to 0-1
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oldest = events.stream()
            .map(BehaviorEventEntity::getCreatedAt)
            .min(LocalDateTime::compareTo)
            .orElse(now);
        
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(oldest, now);
        double recency = Math.max(1.0 - (daysDiff / 30.0), 0.0);  // Normalize
        
        scores.put("engagement", engagement);
        scores.put("recency", recency);
        scores.put("event_count", (double) eventCount);
        
        return scores;
    }
    
    private BehaviorInsights createEmptyInsights(UUID userId) {
        return BehaviorInsights.builder()
            .userId(userId)
            .patterns(List.of("insufficient_data"))
            .recommendations(List.of("collect_additional_signals"))
            .segment("unknown")
            .confidence(0.0)
            .aiModelUsed("default")
            .analyzedAt(LocalDateTime.now())
            .build();
    }
}
```

**Reference:** See `AI_BEHAVIOR_ANALYTICS_MICROSERVICE_ARCHITECTURE.md` → "Layer 2: Async AI Analysis"

### 3.3 Worker/Scheduler

#### Task 3.3.1: Create BehaviorAnalysisWorker

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/worker/BehaviorAnalysisWorker.java`

```java
package com.ai.behavior.worker;

import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.repository.BehaviorEventRepository;
import com.ai.behavior.service.BehaviorEventIngestionService;
import com.ai.behavior.service.BehaviorAnalyzerService;
import com.ai.behavior.config.BehaviorModuleProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class BehaviorAnalysisWorker {
    
    private final BehaviorEventIngestionService ingestionService;
    private final BehaviorAnalyzerService analyzerService;
    private final BehaviorEventRepository eventRepository;
    private final BehaviorModuleProperties properties;
    
    /**
     * Scheduled task: Process unprocessed events every N seconds
     * Configurable via: ai.behavior.processing.worker.delaySeconds
     */
    @Scheduled(
        fixedDelayString = "${ai.behavior.processing.worker.delaySeconds:300}",
        timeUnit = java.util.concurrent.TimeUnit.SECONDS,
        initialDelayString = "${ai.behavior.processing.worker.initialDelaySeconds:60}"
    )
    public void processUnprocessedEvents() {
        try {
            log.debug("Worker: Starting batch processing");
            
            int batchSize = properties.getEvents().getBatchSize();
            List<BehaviorEventEntity> unprocessedEvents = 
                ingestionService.getUnprocessedEvents(batchSize);
            
            if (unprocessedEvents.isEmpty()) {
                log.debug("Worker: No unprocessed events");
                return;
            }
            
            log.info("Worker: Processing {} events", unprocessedEvents.size());
            
            // Group events by user
            Map<UUID, List<BehaviorEventEntity>> byUser = unprocessedEvents
                .stream()
                .collect(Collectors.groupingBy(BehaviorEventEntity::getUserId));
            
            // Process each user's events
            for (Map.Entry<UUID, List<BehaviorEventEntity>> entry : byUser.entrySet()) {
                UUID userId = entry.getKey();
                List<BehaviorEventEntity> userEvents = entry.getValue();
                
                try {
                    // Analyze user's behavior
                    analyzerService.analyzeUserBehavior(userId, userEvents);
                    
                    // Mark as processed
                    for (BehaviorEventEntity event : userEvents) {
                        ingestionService.markProcessed(event.getId());
                    }
                    
                    log.info("Worker: Processed {} events for user {}", 
                        userEvents.size(), userId);
                    
                } catch (Exception e) {
                    log.error("Worker: Failed to process events for user {}: {}",
                        userId, e.getMessage(), e);
                    
                    // Mark for retry (with max retries)
                    for (BehaviorEventEntity event : userEvents) {
                        if (event.getRetryCount() < properties.getProcessing()
                            .getWorker().getMaxRetries()) {
                            event.setRetryCount(event.getRetryCount() + 1);
                            event.setProcessingStatus("RETRY");
                            eventRepository.save(event);
                        }
                    }
                }
            }
            
            log.info("Worker: Batch processing completed");
            
        } catch (Exception e) {
            log.error("Worker: Critical error during batch processing: {}", 
                e.getMessage(), e);
        }
    }
    
    /**
     * Scheduled task: Clean up expired events
     * Runs daily at 3 AM: 0 3 * * *
     */
    @Scheduled(cron = "${ai.behavior.retention.cleanupSchedule:0 3 * * *}")
    public void cleanupExpiredEvents() {
        try {
            log.info("Cleanup: Starting expired events cleanup");
            
            List<BehaviorEventEntity> expiredEvents = 
                eventRepository.findExpiredEvents();
            
            if (expiredEvents.isEmpty()) {
                log.debug("Cleanup: No expired events");
                return;
            }
            
            eventRepository.deleteAll(expiredEvents);
            log.info("Cleanup: Deleted {} expired events", expiredEvents.size());
            
        } catch (Exception e) {
            log.error("Cleanup: Failed to clean expired events: {}",
                e.getMessage(), e);
        }
    }
}
```

**Reference:** See `AI_BEHAVIOR_ANALYTICS_MODULE_V2_PHILOSOPHY.md` → "Architecture" section

---

## 🔍 PHASE 4: SEARCH & ORCHESTRATION (Weeks 3-4)

### 4.1 Search Service

#### Task 4.1.1: Create BehaviorSearchService

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/service/BehaviorSearchService.java`

```java
package com.ai.behavior.service;

import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.repository.BehaviorInsightsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/**
 * Search service for querying BehaviorInsights
 * Queries against AISearchableEntity created by AI Core
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BehaviorSearchService {
    
    private final BehaviorInsightsRepository repository;
    // In future: inject vector database service from AI Core
    
    /**
     * Search by segment
     */
    public List<BehaviorInsights> searchBySegment(String segment, int limit) {
        log.info("Searching insights by segment: {}", segment);
        return repository.findBySegment(segment).stream()
            .limit(limit)
            .toList();
    }
    
    /**
     * Search by pattern (simple contains)
     */
    public List<BehaviorInsights> searchByPattern(String pattern, int limit) {
        log.info("Searching insights by pattern: {}", pattern);
        return repository.findAll().stream()
            .filter(insight -> insight.getPatterns().contains(pattern))
            .limit(limit)
            .toList();
    }
    
    /**
     * Complex search with filters
     * In real implementation: Use vectorstore.search() from AI Core
     */
    public List<BehaviorInsights> search(SearchParameters params, int limit) {
        log.info("Executing complex search with params: {}", params);
        
        List<BehaviorInsights> results = repository.findAll();
        
        // Apply filters
        if (params.getSegment() != null) {
            results = results.stream()
                .filter(i -> i.getSegment().equals(params.getSegment()))
                .toList();
        }
        
        if (params.getMinConfidence() != null) {
            results = results.stream()
                .filter(i -> i.getConfidence() >= params.getMinConfidence())
                .toList();
        }
        
        return results.stream()
            .limit(limit)
            .toList();
    }
}
```

### 4.2 Query Orchestration (AI Core Integration)

#### Task 4.2.1: Create OrchestratedQueryRequest/Response DTOs

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/dto/OrchestratedQueryRequest.java`

```java
package com.ai.behavior.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class OrchestratedQueryRequest {
    private String query;  // Natural language query
    private int limit;  // Max results (default 20)
    private boolean includeExplanation;  // Default true
    private String userId;  // Optional: for audit
}
```

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/dto/OrchestratedSearchResponse.java`

```java
package com.ai.behavior.dto;

import com.ai.behavior.model.BehaviorInsights;
import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrchestratedSearchResponse {
    private String query;
    private LocalDateTime executedAt;
    private boolean piiDetected;
    private String error;
    private SearchResults results;
    
    @Data
    @Builder
    public static class SearchResults {
        private List<BehaviorInsights> matchedUsers;
        private long totalMatches;
        private String searchStrategy;
        private String aiExplanation;
    }
}
```

#### Task 4.2.2: Create BehaviorQueryOrchestrator

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/service/BehaviorQueryOrchestrator.java`

```java
package com.ai.behavior.service;

import com.ai.behavior.dto.OrchestratedQueryRequest;
import com.ai.behavior.dto.OrchestratedSearchResponse;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.core.service.RAGOrchestrator;  // From AI Core
import com.ai.core.service.PIIDetectionService;  // From AI Core
import com.ai.core.model.RAGOrchestrationContext;  // From AI Core
import com.ai.core.model.RAGResponse;  // From AI Core
import com.ai.core.model.PIIDetectionResult;  // From AI Core
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Orchestrates search queries through AI Core.
 * Handles: PII detection, query transformation, search execution, explanation generation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BehaviorQueryOrchestrator {
    
    private final RAGOrchestrator ragOrchestrator;  // ← From AI Core
    private final PIIDetectionService piiDetection;  // ← From AI Core
    private final BehaviorSearchService searchService;
    
    /**
     * Execute orchestrated query
     */
    public OrchestratedSearchResponse executeQuery(OrchestratedQueryRequest request) {
        log.info("Executing orchestrated query: {}", request.getQuery());
        
        // STEP 1: PII Detection
        log.debug("Step 1: Detecting PII...");
        PIIDetectionResult piiResult = piiDetection.detect(request.getQuery());
        
        if (piiResult.hasSensitiveData()) {
            log.warn("Query contains PII");
            return OrchestratedSearchResponse.builder()
                .query(request.getQuery())
                .executedAt(LocalDateTime.now())
                .piiDetected(true)
                .error("Query contains PII. Please rephrase without sensitive data.")
                .build();
        }
        
        // STEP 2: Orchestrate through AI Core
        log.debug("Step 2: Orchestrating query through AI Core...");
        RAGOrchestrationContext context = RAGOrchestrationContext.builder()
            .userQuery(request.getQuery())
            .source("behavior-analytics")
            .dataType("behavior-insights")
            .intent("search")
            .build();
        
        RAGResponse ragResponse = ragOrchestrator.orchestrate(context);
        log.debug("RAG Response: {}", ragResponse);
        
        // STEP 3: Transform to search params
        log.debug("Step 3: Transforming query to search params...");
        SearchParameters searchParams = transformToSearchParams(
            ragResponse.getTransformedQuery()
        );
        
        // STEP 4: Execute search
        log.debug("Step 4: Executing search...");
        List<BehaviorInsights> searchResults = searchService.search(
            searchParams,
            request.getLimit()
        );
        log.info("Search returned {} results", searchResults.size());
        
        // STEP 5: Generate explanation
        log.debug("Step 5: Generating explanation...");
        String explanation = request.isIncludeExplanation() ?
            generateExplanation(request.getQuery(), searchResults) :
            null;
        
        // STEP 6: Return response
        return OrchestratedSearchResponse.builder()
            .query(request.getQuery())
            .executedAt(LocalDateTime.now())
            .piiDetected(false)
            .results(OrchestratedSearchResponse.SearchResults.builder()
                .matchedUsers(searchResults)
                .totalMatches(searchResults.size())
                .searchStrategy(ragResponse.getSearchStrategy())
                .aiExplanation(explanation)
                .build())
            .build();
    }
    
    /**
     * Transform AI-generated query to structured search params
     */
    private SearchParameters transformToSearchParams(String transformedQuery) {
        log.debug("Transforming query: {}", transformedQuery);
        
        // Parse AI query: "segment=high_value AND confidence>0.8"
        // → SearchParameters with filters
        
        SearchParameters params = new SearchParameters();
        
        if (transformedQuery.contains("segment=")) {
            String segment = extractParam(transformedQuery, "segment=");
            params.setSegment(segment);
        }
        
        if (transformedQuery.contains("confidence")) {
            Double confidence = extractDoubleParam(transformedQuery, "confidence");
            params.setMinConfidence(confidence);
        }
        
        return params;
    }
    
    /**
     * Generate AI explanation
     */
    private String generateExplanation(String query, List<BehaviorInsights> results) {
        // In real implementation: Call LLM via AI Core
        // return ragOrchestrator.generateExplanation(query, results);
        
        return String.format(
            "Found %d users matching your criteria. " +
            "Top segments: %s",
            results.size(),
            results.stream()
                .map(BehaviorInsights::getSegment)
                .distinct()
                .toList()
        );
    }
    
    private String extractParam(String query, String paramName) {
        int startIdx = query.indexOf(paramName) + paramName.length();
        int endIdx = query.indexOf(" ", startIdx);
        if (endIdx == -1) endIdx = query.length();
        return query.substring(startIdx, endIdx);
    }
    
    private Double extractDoubleParam(String query, String paramName) {
        try {
            String value = extractParam(query, paramName + ">");
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
```

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/service/SearchParameters.java`

```java
package com.ai.behavior.service;

import lombok.Data;

@Data
public class SearchParameters {
    private String segment;
    private Double minConfidence;
    private String query;
    // Add more filter fields as needed
}
```

### 4.3 REST Controller

#### Task 4.3.1: Create BehaviorSearchController

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/controller/BehaviorSearchController.java`

```java
package com.ai.behavior.controller;

import com.ai.behavior.dto.OrchestratedQueryRequest;
import com.ai.behavior.dto.OrchestratedSearchResponse;
import com.ai.behavior.service.BehaviorQueryOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Slf4j
public class BehaviorSearchController {
    
    private final BehaviorQueryOrchestrator queryOrchestrator;
    
    /**
     * POST /api/search/orchestrated
     * 
     * Execute AI-orchestrated search query
     * 
     * Request:
     * {
     *   "query": "Show me high-value users at risk of churn",
     *   "limit": 20,
     *   "includeExplanation": true,
     *   "userId": "550e8400-e29b-41d4-a716-446655440000"
     * }
     */
    @PostMapping("/orchestrated")
    public ResponseEntity<OrchestratedSearchResponse> executeOrchestratedQuery(
        @RequestBody OrchestratedQueryRequest request
    ) {
        log.info("Received orchestrated search request: {}", request.getQuery());
        
        try {
            OrchestratedSearchResponse response = queryOrchestrator.executeQuery(request);
            
            if (response.isPiiDetected()) {
                log.warn("PII detected in query");
                return ResponseEntity.badRequest().body(response);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error executing orchestrated query: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(OrchestratedSearchResponse.builder()
                    .query(request.getQuery())
                    .error("Query execution failed: " + e.getMessage())
                    .build());
        }
    }
}
```

#### Task 4.3.2: Create BehaviorEventController

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/controller/BehaviorEventController.java`

```java
package com.ai.behavior.controller;

import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.service.BehaviorEventIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@RestController
@RequestMapping("/api/behavior/events")
@RequiredArgsConstructor
@Slf4j
public class BehaviorEventController {
    
    private final BehaviorEventIngestionService ingestionService;
    
    /**
     * POST /api/behavior/events
     * 
     * Ingest single event (non-blocking, returns 202 Accepted)
     * 
     * Request:
     * {
     *   "userId": "550e8400-e29b-41d4-a716-446655440000",
     *   "eventType": "engagement.view",
     *   "eventData": {"page": "product", "duration": 120},
     *   "source": "web"
     * }
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> ingestEvent(
        @RequestBody Map<String, Object> payload
    ) {
        try {
            UUID userId = UUID.fromString((String) payload.get("userId"));
            String eventType = (String) payload.get("eventType");
            String source = (String) payload.getOrDefault("source", "api");
            String eventData = payload.get("eventData").toString();
            
            log.info("Ingesting event: userId={}, eventType={}", userId, eventType);
            
            BehaviorEventEntity event = ingestionService.ingestSingleEvent(
                userId, eventType, eventData, source
            );
            
            return ResponseEntity.accepted()
                .body(Map.of(
                    "eventId", event.getId(),
                    "status", "accepted",
                    "message", "Event queued for processing"
                ));
        } catch (Exception e) {
            log.error("Error ingesting event: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * POST /api/behavior/events/batch
     * 
     * Ingest batch of events (non-blocking, returns 202 Accepted)
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> ingestBatchEvents(
        @RequestBody List<Map<String, Object>> batch
    ) {
        try {
            log.info("Ingesting batch: {} events", batch.size());
            
            List<BehaviorEventEntity> events = batch.stream()
                .map(this::mapToEntity)
                .toList();
            
            List<BehaviorEventEntity> saved = ingestionService.ingestBatchEvents(events);
            
            return ResponseEntity.accepted()
                .body(Map.of(
                    "count", saved.size(),
                    "status", "accepted",
                    "message", "Batch queued for processing"
                ));
        } catch (Exception e) {
            log.error("Error ingesting batch: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        }
    }
    
    private BehaviorEventEntity mapToEntity(Map<String, Object> map) {
        return BehaviorEventEntity.builder()
            .userId(UUID.fromString((String) map.get("userId")))
            .eventType((String) map.get("eventType"))
            .source((String) map.getOrDefault("source", "batch"))
            .build();
    }
}
```

**Reference:** See `AI_BEHAVIOR_ANALYTICS_MICROSERVICE_ARCHITECTURE.md` → "API Endpoints" & "Layer 4: Search & Orchestration"

---

## ✅ PHASE 5: INTEGRATION TESTING (Weeks 4-5)

### 5.1 Test Configuration

#### Task 5.1.1: Create Integration Test Base Class

**File:** `ai-infrastructure-behavior/src/test/java/com/ai/behavior/integration/BehaviorAnalyticsIntegrationTest.java`

```java
package com.ai.behavior.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
public abstract class BehaviorAnalyticsIntegrationTest {
    
    @Container
    public static PostgreSQLContainer<?> postgres = 
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("behavior_test")
            .withUsername("test")
            .withPassword("test");
    
    @Autowired
    protected MockMvc mockMvc;
    
    @BeforeEach
    public void setup() {
        // Setup common test data
    }
}
```

#### Task 5.1.2: Create Event Ingestion Test

**File:** `ai-infrastructure-behavior/src/test/java/com/ai/behavior/integration/EventIngestionIntegrationTest.java`

```java
package com.ai.behavior.integration;

import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.repository.BehaviorEventRepository;
import com.ai.behavior.service.BehaviorEventIngestionService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

public class EventIngestionIntegrationTest extends BehaviorAnalyticsIntegrationTest {
    
    @Autowired
    private BehaviorEventIngestionService ingestionService;
    
    @Autowired
    private BehaviorEventRepository repository;
    
    @Test
    public void testSingleEventIngestion() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String eventType = "engagement.view";
        String eventData = "{\"page\": \"home\"}";
        String source = "web";
        
        // Act
        BehaviorEventEntity event = ingestionService.ingestSingleEvent(
            userId, eventType, eventData, source
        );
        
        // Assert
        assertThat(event).isNotNull();
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getEventType()).isEqualTo(eventType);
        assertThat(event.getProcessed()).isFalse();
        
        // Verify persisted
        BehaviorEventEntity persisted = repository.findById(event.getId())
            .orElseThrow();
        assertThat(persisted).isNotNull();
    }
    
    @Test
    public void testBatchEventIngestion() {
        // Arrange
        UUID userId = UUID.randomUUID();
        var events = java.util.List.of(
            BehaviorEventEntity.builder()
                .userId(userId)
                .eventType("engagement.view")
                .source("web")
                .build(),
            BehaviorEventEntity.builder()
                .userId(userId)
                .eventType("conversion.purchase")
                .source("web")
                .build()
        );
        
        // Act
        var saved = ingestionService.ingestBatchEvents(events);
        
        // Assert
        assertThat(saved).hasSize(2);
    }
}
```

#### Task 5.1.3: Create Analysis Test

**File:** `ai-infrastructure-behavior/src/test/java/com/ai/behavior/integration/BehaviorAnalysisIntegrationTest.java`

```java
package com.ai.behavior.integration;

import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.service.BehaviorAnalyzerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

public class BehaviorAnalysisIntegrationTest extends BehaviorAnalyticsIntegrationTest {
    
    @Autowired
    private BehaviorAnalyzerService analyzerService;
    
    @Test
    public void testAnalyzeUserBehavior() {
        // Arrange
        UUID userId = UUID.randomUUID();
        var events = List.of(
            BehaviorEventEntity.builder()
                .userId(userId)
                .eventType("engagement.view")
                .createdAt(LocalDateTime.now())
                .build(),
            BehaviorEventEntity.builder()
                .userId(userId)
                .eventType("engagement.click")
                .createdAt(LocalDateTime.now())
                .build()
        );
        
        // Act
        BehaviorInsights insights = analyzerService.analyzeUserBehavior(userId, events);
        
        // Assert
        assertThat(insights).isNotNull();
        assertThat(insights.getUserId()).isEqualTo(userId);
        assertThat(insights.getPatterns()).isNotEmpty();
        assertThat(insights.getRecommendations()).isNotEmpty();
    }
}
```

**Reference:** See `PatternAnalyzerInsightsIntegrationTest.java` for test patterns

---

## 🚀 PHASE 6: PRODUCTION HARDENING (Weeks 5-6)

### 6.1 Security & Monitoring

#### Task 6.1.1: Add Rate Limiting

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/config/RateLimitingConfiguration.java`

```java
package com.ai.behavior.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Configuration
public class RateLimitingConfiguration {
    
    private final Map<UUID, Bucket> buckets = new HashMap<>();
    
    public boolean allowRequest(UUID userId) {
        Bucket bucket = buckets.computeIfAbsent(userId, this::createBucket);
        return bucket.tryConsume(1);
    }
    
    private Bucket createBucket(UUID userId) {
        Bandwidth limit = Bandwidth.classic(100, Refill.intervally(100, Duration.ofHours(1)));
        return Bucket4j.builder()
            .addLimit(limit)
            .build();
    }
}
```

#### Task 6.1.2: Add Audit Logging

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/behavior/audit/BehaviorAuditService.java`

```java
package com.ai.behavior.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BehaviorAuditService {
    
    public void logEventIngestion(UUID userId, String eventType) {
        log.info("AUDIT: Event ingestion - userId={}, eventType={}, timestamp={}",
            userId, eventType, LocalDateTime.now());
    }
    
    public void logQueryExecution(String query, UUID userId, int resultCount) {
        log.info("AUDIT: Query execution - query={}, userId={}, results={}, timestamp={}",
            query, userId, resultCount, LocalDateTime.now());
    }
    
    public void logPIIDetected(String query) {
        log.warn("AUDIT: PII detected - query={}, timestamp={}",
            query, LocalDateTime.now());
    }
}
```

### 6.2 Metrics & Monitoring

#### Task 6.2.1: Add Actuator Endpoints

**File:** `ai-infrastructure-behavior/src/main/resources/application.yml`

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

---

## 📋 ROLLOUT STRATEGY

### Deployment Order

```
Week 6, Day 1-2: Staging Deployment
├─ Deploy to staging environment
├─ Run full integration test suite
├─ Load testing (1000 req/sec)
└─ Security review

Week 6, Day 3-4: Canary Release
├─ Deploy to 10% of production
├─ Monitor: latency, errors, PII detections
├─ Gradual rollout: 25% → 50% → 100%
└─ Rollback plan ready

Week 6, Day 5+: Full Production
├─ 100% of traffic
├─ 24/7 monitoring
├─ On-call escalation ready
└─ Success criteria: <50ms p95 latency, <1% errors
```

### Monitoring & Alerts

```
Critical Metrics:
├─ Event ingestion latency (target: <50ms)
├─ Analysis worker processing time (target: <5s per user)
├─ Search query latency (target: <100ms p95)
├─ PII detection rate (target: ~0%)
├─ Error rate (target: <0.5%)
└─ Worker backlog (target: <100 pending events)

Alerts:
├─ Worker backlog > 1000
├─ Search latency > 5s
├─ PII detection anomaly
├─ Database connection pool exhausted
└─ Redis cache miss rate > 20%
```

---

## 🎯 SUCCESS CRITERIA

Each phase has clear success metrics:

| Phase | Deliverable | Success Criteria |
|-------|-------------|------------------|
| 1 | Foundation | All dependencies resolved, tests compile |
| 2 | Event Pipeline | Single & batch ingestion working, 95%+ uptime |
| 3 | AI Analysis | Policy hooks integrated, analysis latency <5s |
| 4 | Search & Query | Orchestrated endpoint working, PII detection active |
| 5 | Testing | 80%+ code coverage, all integration tests passing |
| 6 | Production | <50ms search latency, <1% error rate, monitoring active |

---

## 📚 REFERENCE DOCUMENTS

- **AI_BEHAVIOR_ANALYTICS_MICROSERVICE_ARCHITECTURE.md** - Main architecture
- **AI_BEHAVIOR_ANALYTICS_MODULE_V2_PHILOSOPHY.md** - Original requirements
- **AI_BEHAVIOR_ANALYTICS_MODULE_V2_IMPLEMENTATION_CHECKLIST.md** - Task structure
- **COUPLING_QUICK_ANSWER.txt** - Hook-based decoupling patterns

---

**Ready to start implementing! 🚀**

Each task includes exact file paths, code examples, and integration points. Developers can follow this sequentially without ambiguity.

