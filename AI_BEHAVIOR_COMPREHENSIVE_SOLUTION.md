# AI Behavior - Comprehensive Solution

**Version:** 1.0.0  
**Date:** 2025-11-14  
**Status:** Design Specification

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Architecture Overview](#architecture-overview)
3. [Core Principles](#core-principles)
4. [Module Structure](#module-structure)
5. [Core Interfaces](#core-interfaces)
6. [Data Models](#data-models)
7. [Implementation Components](#implementation-components)
8. [Integration Patterns](#integration-patterns)
9. [Embedding Strategy](#embedding-strategy)
10. [Configuration](#configuration)
11. [Migration Strategy](#migration-strategy)
12. [Usage Examples](#usage-examples)
13. [Performance Considerations](#performance-considerations)
14. [Monitoring & Observability](#monitoring--observability)
15. [Implementation Roadmap](#implementation-roadmap)

---

## Executive Summary

### Problem Statement

Current behavior tracking implementation has critical issues:
- **Duplication:** Two identical entities (`Behavior` and `UserBehavior`) with separate repositories
- **Over-engineering:** Every behavior gets AI embeddings (expensive and unnecessary)
- **Poor separation:** Behavior logic mixed in `ai-infrastructure-core` (domain-specific in generic module)
- **Performance bottlenecks:** No indexes, N+1 queries, synchronous AI processing
- **Inflexibility:** Tightly coupled to database storage, can't integrate external systems

### Solution Overview

New `ai-behavior` module that:
- ✅ **Separates concerns:** Behavior out of ai-core
- ✅ **Flexible storage:** Pluggable storage backends (DB, Kafka, Redis, S3)
- ✅ **Multi-source ingestion:** Accept events from web, mobile, external systems
- ✅ **Selective AI:** Embeddings only for text content (feedback, reviews, search queries)
- ✅ **Async processing:** Non-blocking analysis and pattern detection
- ✅ **Extensible:** Adapter pattern for legacy system integration

### Key Benefits

| Current | Proposed |
|---------|----------|
| 2 duplicate entities | 1 unified model |
| AI on every event | AI only where valuable |
| Synchronous blocking | Async non-blocking |
| Database only | Pluggable storage |
| Closed system | Open for integration |
| Performance bottleneck | Optimized and scalable |

---

## Architecture Overview

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       APPLICATION LAYER                          │
│  (E-commerce backend, Mobile app, External systems)             │
│                                                                  │
│  - Track events: POST /api/ai-behavior/ingest                  │
│  - Query insights: GET /api/ai-behavior/users/{id}/insights    │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ REST API / SDK
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                    AI-BEHAVIOR MODULE                            │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ INGESTION LAYER                                            │ │
│  │  - BehaviorIngestionService                               │ │
│  │  - Validation & Enrichment                                │ │
│  │  - Event Publishing                                       │ │
│  └────────────────────────────────────────────────────────────┘ │
│                            │                                     │
│                            ↓                                     │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ STORAGE LAYER (PLUGGABLE)                                 │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │ │
│  │  │   Database   │  │    Kafka     │  │    Redis     │   │ │
│  │  │    Sink      │  │    Sink      │  │    Sink      │   │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘   │ │
│  │          └─────────────┬──────────────┘                   │ │
│  │                   BehaviorEventSink (Interface)            │ │
│  └────────────────────────────────────────────────────────────┘ │
│                            │                                     │
│                            ↓                                     │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ PROCESSING LAYER (ASYNC)                                  │ │
│  │  - Real-time Aggregation Worker                           │ │
│  │  - Pattern Detection Worker                               │ │
│  │  - Anomaly Detection Worker                               │ │
│  │  - Embedding Generation Worker (selective)                │ │
│  │  - User Segmentation Worker                               │ │
│  └────────────────────────────────────────────────────────────┘ │
│                            │                                     │
│                            ↓                                     │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ ANALYSIS RESULTS                                          │ │
│  │  - BehaviorInsights (pre-computed)                        │ │
│  │  - BehaviorMetrics (aggregated)                           │ │
│  │  - BehaviorEmbeddings (selective, text only)              │ │
│  │  - BehaviorAlerts (anomalies)                             │ │
│  └────────────────────────────────────────────────────────────┘ │
│                            │                                     │
│                            ↓                                     │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ QUERY LAYER                                               │ │
│  │  - BehaviorDataProvider (Interface)                       │ │
│  │  - BehaviorInsightsService                                │ │
│  │  - BehaviorQueryService                                   │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ Uses for AI capabilities
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                      AI-CORE MODULE                              │
│  (Generic AI infrastructure - domain agnostic)                  │
│                                                                  │
│  - Embeddings Generation (OpenAI, local models)                 │
│  - Vector Search                                                │
│  - Pattern Detection (generic time-series)                      │
│  - Semantic Analysis                                            │
│  - RAG (Retrieval Augmented Generation)                         │
│                                                                  │
│  Internal: AI Operations Tracking (NOT exposed to behavior)     │
└─────────────────────────────────────────────────────────────────┘
```

### Module Dependencies

```
application
    ↓
ai-behavior (NEW)
    ↓
ai-core (existing - generic primitives only)
    ↓
Spring Boot, JPA, etc.
```

**Key principle:** ai-core knows NOTHING about behaviors. ai-behavior USES ai-core.

---

## Core Principles

### 1. Separation of Concerns
- **ai-core:** Generic AI primitives (embeddings, search, RAG)
- **ai-behavior:** Domain-specific behavior tracking
- **application:** Business logic and UI

### 2. Flexibility First
- Pluggable storage backends
- Multiple ingestion sources
- Extensible via interfaces
- Default implementations for quick start

### 3. Performance by Design
- Async processing (non-blocking)
- Selective AI (only where valuable)
- Proper indexing
- Efficient batch operations

### 4. AI Where It Matters
- NO embeddings for simple events (clicks, views)
- YES embeddings for text content (feedback, reviews)
- Pre-computed insights (fast reads)
- Background analysis (scheduled)

### 5. Integration-Friendly
- REST API for remote systems
- SDK for local integration
- Adapter pattern for legacy systems
- Batch import for external analytics

---

## Module Structure

### Directory Structure

```
ai-behavior-module/
├── pom.xml
├── README.md
│
├── src/main/java/com/ai/behavior/
│   │
│   ├── model/
│   │   ├── BehaviorEvent.java                    # Core event model
│   │   ├── BehaviorQuery.java                    # Query builder
│   │   ├── BehaviorInsights.java                 # Analysis results
│   │   ├── BehaviorMetrics.java                  # Aggregated metrics
│   │   ├── BehaviorEmbedding.java                # Text embeddings (selective)
│   │   ├── BehaviorAlert.java                    # Anomaly alerts
│   │   └── EventType.java                        # Simple enum (10-15 types)
│   │
│   ├── ingestion/
│   │   ├── BehaviorIngestionService.java         # Main ingestion service
│   │   ├── BehaviorIngestionController.java      # REST API
│   │   ├── BehaviorEventSink.java                # Storage interface
│   │   ├── impl/
│   │   │   ├── DatabaseEventSink.java            # Default DB storage
│   │   │   ├── KafkaEventSink.java               # Kafka integration
│   │   │   ├── RedisEventSink.java               # Redis cache
│   │   │   └── HybridEventSink.java              # Hot/cold storage
│   │   └── validator/
│   │       └── BehaviorEventValidator.java
│   │
│   ├── storage/
│   │   ├── BehaviorDataProvider.java             # Query interface
│   │   ├── BehaviorEventRepository.java          # JPA repository
│   │   ├── BehaviorInsightsRepository.java
│   │   ├── BehaviorMetricsRepository.java
│   │   ├── BehaviorEmbeddingRepository.java
│   │   └── impl/
│   │       ├── DatabaseBehaviorProvider.java     # Default implementation
│   │       ├── ExternalAnalyticsBehaviorProvider.java
│   │       └── AggregatedBehaviorProvider.java   # Multi-source
│   │
│   ├── processing/
│   │   ├── worker/
│   │   │   ├── RealTimeAggregationWorker.java    # Async metrics
│   │   │   ├── PatternDetectionWorker.java       # Scheduled patterns
│   │   │   ├── AnomalyDetectionWorker.java       # Fraud/suspicious
│   │   │   ├── EmbeddingGenerationWorker.java    # Selective embedding
│   │   │   └── UserSegmentationWorker.java       # Daily segmentation
│   │   └── analyzer/
│   │       ├── PatternAnalyzer.java              # Uses ai-core
│   │       ├── AnomalyAnalyzer.java
│   │       └── SegmentationAnalyzer.java
│   │
│   ├── analysis/
│   │   ├── BehaviorAnalysisService.java          # Main analysis service
│   │   ├── BehaviorInsightsService.java          # Query insights
│   │   └── BehaviorQueryService.java             # Query events
│   │
│   ├── api/
│   │   ├── BehaviorIngestionController.java      # POST events
│   │   ├── BehaviorInsightsController.java       # GET insights
│   │   ├── BehaviorQueryController.java          # GET events
│   │   └── BehaviorMonitoringController.java     # Admin monitoring
│   │
│   ├── adapter/
│   │   ├── LegacySystemAdapter.java              # For old systems
│   │   └── ExternalAnalyticsAdapter.java         # Mixpanel, GA, etc.
│   │
│   ├── config/
│   │   ├── AIBehaviorAutoConfiguration.java      # Spring Boot auto-config
│   │   ├── BehaviorStorageConfiguration.java
│   │   ├── BehaviorProcessingConfiguration.java
│   │   └── BehaviorProperties.java               # Configuration properties
│   │
│   └── exception/
│       ├── BehaviorValidationException.java
│       ├── BehaviorStorageException.java
│       └── BehaviorAnalysisException.java
│
├── src/main/resources/
│   ├── db/migration/
│   │   ├── V1__Create_Behavior_Events.sql
│   │   ├── V2__Create_Behavior_Insights.sql
│   │   ├── V3__Create_Behavior_Metrics.sql
│   │   ├── V4__Create_Behavior_Embeddings.sql
│   │   └── V5__Create_Indexes.sql
│   └── application.yml
│
└── src/test/java/com/ai/behavior/
    ├── integration/
    └── unit/
```

---

## Core Interfaces

### 1. BehaviorEventSink (Write Interface)

**Purpose:** Define WHERE events are stored (pluggable storage)

```java
package com.ai.behavior.ingestion;

import com.ai.behavior.model.BehaviorEvent;
import java.util.List;

/**
 * Interface for storing behavior events.
 * Implementations can store in: Database, Kafka, Redis, S3, etc.
 * 
 * Users can provide custom implementations via:
 * @Component
 * public class MyCustomSink implements BehaviorEventSink { ... }
 */
public interface BehaviorEventSink {
    
    /**
     * Store a single behavior event
     * 
     * @param event the behavior event to store
     * @throws BehaviorStorageException if storage fails
     */
    void accept(BehaviorEvent event);
    
    /**
     * Store multiple behavior events in batch
     * More efficient for bulk operations
     * 
     * @param events list of events to store
     * @throws BehaviorStorageException if storage fails
     */
    void acceptBatch(List<BehaviorEvent> events);
    
    /**
     * Optional: Flush any buffered events
     * Some implementations may buffer for performance
     */
    default void flush() {
        // Default: no-op
    }
    
    /**
     * Get the sink type identifier
     * Used for monitoring and configuration
     */
    String getSinkType();
}
```

### 2. BehaviorDataProvider (Read Interface)

**Purpose:** Define HOW to query behavior data (flexible sources)

```java
package com.ai.behavior.storage;

import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorQuery;
import java.util.List;
import java.util.UUID;

/**
 * Interface for querying behavior events.
 * Implementations can read from: Database, Cache, External APIs, etc.
 * 
 * Users can provide custom implementations to integrate with:
 * - Internal databases
 * - External analytics (Mixpanel, Amplitude)
 * - Legacy systems
 * - Multiple sources (aggregated)
 */
public interface BehaviorDataProvider {
    
    /**
     * Query behavior events using flexible query builder
     * 
     * @param query the behavior query
     * @return list of matching behavior events
     */
    List<BehaviorEvent> query(BehaviorQuery query);
    
    /**
     * Get recent events for a user (convenience method)
     * 
     * @param userId the user ID
     * @param limit max number of events
     * @return list of recent events
     */
    default List<BehaviorEvent> getRecentEvents(UUID userId, int limit) {
        return query(BehaviorQuery.forUser(userId).limit(limit));
    }
    
    /**
     * Get events for an entity (product, page, etc.)
     * 
     * @param entityType the entity type
     * @param entityId the entity ID
     * @return list of events related to entity
     */
    default List<BehaviorEvent> getEntityEvents(String entityType, String entityId) {
        return query(BehaviorQuery.forEntity(entityType, entityId));
    }
    
    /**
     * Get the provider type identifier
     * Used for monitoring and debugging
     */
    String getProviderType();
}
```

### 3. BehaviorAnalyzer (Analysis Interface)

**Purpose:** Define HOW behavior is analyzed (extensible analyzers)

```java
package com.ai.behavior.processing.analyzer;

import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorInsights;
import java.util.List;
import java.util.UUID;

/**
 * Interface for behavior analysis.
 * Different analyzers can focus on:
 * - Pattern detection
 * - Anomaly detection
 * - User segmentation
 * - Churn prediction
 * - Conversion optimization
 */
public interface BehaviorAnalyzer {
    
    /**
     * Analyze behavior events and generate insights
     * 
     * @param userId the user ID
     * @param events the behavior events to analyze
     * @return behavior insights
     */
    BehaviorInsights analyze(UUID userId, List<BehaviorEvent> events);
    
    /**
     * Get the analyzer type
     */
    String getAnalyzerType();
    
    /**
     * Check if this analyzer supports the given event types
     */
    boolean supports(List<String> eventTypes);
}
```

---

## Data Models

### 1. BehaviorEvent (Core Event Model)

**Simplified, lightweight, domain-agnostic**

```java
package com.ai.behavior.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * BehaviorEvent - Core lightweight event model
 * 
 * Simplified from previous 20+ fields to essential data only.
 * Analysis results stored separately (not in event).
 * 
 * Design principles:
 * - Fast writes (minimal fields)
 * - Flexible metadata (JSON for domain-specific data)
 * - No AI fields here (separate table)
 * - Partitioned by date (automatic cleanup)
 */
@Entity
@Table(
    name = "behavior_events",
    indexes = {
        @Index(name = "idx_behavior_user_time", columnList = "user_id, timestamp DESC"),
        @Index(name = "idx_behavior_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_behavior_type", columnList = "event_type"),
        @Index(name = "idx_behavior_session", columnList = "session_id"),
        @Index(name = "idx_behavior_timestamp", columnList = "timestamp DESC")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * User who performed the action
     * NULL for anonymous users (use session_id)
     */
    @Column(name = "user_id")
    private UUID userId;
    
    /**
     * Type of behavior event
     * Simple enum: view, click, purchase, search, feedback, etc.
     * Keep under 15 types for simplicity
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;
    
    /**
     * Type of entity the event relates to
     * Examples: product, page, order, article, video
     */
    @Column(name = "entity_type", length = 50)
    private String entityType;
    
    /**
     * ID of the entity
     * Examples: product ID, page slug, order ID
     */
    @Column(name = "entity_id", length = 255)
    private String entityId;
    
    /**
     * Session ID for tracking user journeys
     * Used for anonymous users and session analysis
     */
    @Column(name = "session_id", length = 255)
    private String sessionId;
    
    /**
     * When the event occurred
     * Used for time-series analysis and partitioning
     */
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    /**
     * Flexible metadata (JSON)
     * Store domain-specific data here:
     * - source: "web_app", "mobile_app", "pos_system"
     * - device: "iPhone 15", "Chrome on Windows"
     * - location: "US-CA-SF"
     * - price: 199.99
     * - quantity: 2
     * - Any custom fields
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    /**
     * When this event was ingested (system timestamp)
     * Different from 'timestamp' which is user action time
     */
    @CreationTimestamp
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private LocalDateTime ingestedAt;
    
    // Helper methods
    
    public String getMetadataValue(String key) {
        return metadata != null ? (String) metadata.get(key) : null;
    }
    
    public void setMetadataValue(String key, Object value) {
        if (metadata == null) {
            metadata = new java.util.HashMap<>();
        }
        metadata.put(key, value);
    }
    
    public boolean isAnonymous() {
        return userId == null;
    }
    
    public String getUserIdentifier() {
        return userId != null ? userId.toString() : sessionId;
    }
}
```

### 2. BehaviorInsights (Analysis Results)

**Pre-computed insights for fast reads**

```java
package com.ai.behavior.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BehaviorInsights - Pre-computed analysis results
 * 
 * Stores results of async analysis for fast retrieval.
 * Updated periodically (every 5 min, hourly, daily depending on type)
 * 
 * Separation principle: Raw events are cheap to write,
 * insights are expensive to compute but cheap to read.
 */
@Entity
@Table(
    name = "behavior_insights",
    indexes = {
        @Index(name = "idx_insights_user", columnList = "user_id"),
        @Index(name = "idx_insights_valid", columnList = "valid_until")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorInsights {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    /**
     * Detected patterns
     * Examples: ["frequent_buyer", "evening_shopper", "mobile_first", "price_sensitive"]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "patterns", columnDefinition = "jsonb")
    private List<String> patterns;
    
    /**
     * Behavioral scores
     * Examples:
     * - engagement_score: 0.85
     * - conversion_probability: 0.65
     * - churn_risk: 0.15
     * - lifetime_value_estimate: 1250.00
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scores", columnDefinition = "jsonb")
    private Map<String, Double> scores;
    
    /**
     * User segment
     * Examples: "VIP", "at_risk", "new_user", "dormant"
     */
    @Column(name = "segment", length = 100)
    private String segment;
    
    /**
     * Preferences detected from behavior
     * Examples:
     * - preferred_categories: ["watches", "jewelry"]
     * - price_range: "luxury"
     * - shopping_time: "evening"
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", columnDefinition = "jsonb")
    private Map<String, Object> preferences;
    
    /**
     * Recommended actions
     * Examples: ["send_promotion", "offer_vip_upgrade", "re_engagement_email"]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendations", columnDefinition = "jsonb")
    private List<String> recommendations;
    
    /**
     * When this insight was computed
     */
    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;
    
    /**
     * When this insight expires (cache TTL)
     * After expiration, re-analyze
     */
    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;
    
    /**
     * Analysis version (for A/B testing different algorithms)
     */
    @Column(name = "analysis_version", length = 50)
    private String analysisVersion;
    
    // Helper methods
    
    public boolean isValid() {
        return validUntil.isAfter(LocalDateTime.now());
    }
    
    public boolean hasPattern(String pattern) {
        return patterns != null && patterns.contains(pattern);
    }
    
    public Double getScore(String scoreType) {
        return scores != null ? scores.get(scoreType) : null;
    }
}
```

### 3. BehaviorEmbedding (Selective Text Embeddings)

**Only for text content that needs semantic search**

```java
package com.ai.behavior.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BehaviorEmbedding - Selective text embeddings
 * 
 * ONLY created for events with text content:
 * - User feedback
 * - Product reviews
 * - Search queries
 * - Support messages
 * 
 * NOT created for:
 * - Clicks
 * - Views
 * - Purchases
 * - Simple events
 * 
 * Principle: Embeddings are expensive (API cost + storage).
 * Only generate when semantic search provides value.
 */
@Entity
@Table(
    name = "behavior_embeddings",
    indexes = {
        @Index(name = "idx_embedding_event", columnList = "behavior_event_id"),
        @Index(name = "idx_embedding_type", columnList = "embedding_type")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorEmbedding {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Reference to the behavior event
     */
    @Column(name = "behavior_event_id", nullable = false)
    private UUID behaviorEventId;
    
    /**
     * Type of embedding
     * Examples: "feedback", "review", "search_query", "support_message"
     */
    @Column(name = "embedding_type", nullable = false, length = 50)
    private String embeddingType;
    
    /**
     * Original text that was embedded
     */
    @Column(name = "original_text", columnDefinition = "TEXT")
    private String originalText;
    
    /**
     * Vector embedding (1536 dimensions for text-embedding-3-small)
     * Stored as binary for efficiency
     */
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;
    
    /**
     * Model used for embedding
     */
    @Column(name = "model", length = 100)
    private String model;
    
    /**
     * When embedding was generated
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Optional: Category detected from embedding
     */
    @Column(name = "detected_category", length = 100)
    private String detectedCategory;
    
    /**
     * Optional: Sentiment score (-1 to 1)
     */
    @Column(name = "sentiment_score")
    private Double sentimentScore;
}
```

### 4. BehaviorMetrics (Aggregated Counters)

**Fast aggregated metrics**

```java
package com.ai.behavior.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * BehaviorMetrics - Pre-aggregated metrics
 * 
 * Updated in real-time by RealTimeAggregationWorker.
 * Provides fast counts without scanning raw events.
 * 
 * Partitioned by date for efficient queries.
 */
@Entity
@Table(
    name = "behavior_metrics",
    indexes = {
        @Index(name = "idx_metrics_user_date", columnList = "user_id, metric_date DESC"),
        @Index(name = "idx_metrics_date", columnList = "metric_date DESC")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorMetrics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;
    
    // Event counts
    @Column(name = "view_count")
    private Integer viewCount = 0;
    
    @Column(name = "click_count")
    private Integer clickCount = 0;
    
    @Column(name = "search_count")
    private Integer searchCount = 0;
    
    @Column(name = "add_to_cart_count")
    private Integer addToCartCount = 0;
    
    @Column(name = "purchase_count")
    private Integer purchaseCount = 0;
    
    @Column(name = "feedback_count")
    private Integer feedbackCount = 0;
    
    // Session metrics
    @Column(name = "session_count")
    private Integer sessionCount = 0;
    
    @Column(name = "avg_session_duration_seconds")
    private Integer avgSessionDurationSeconds = 0;
    
    // Conversion metrics
    @Column(name = "conversion_rate")
    private Double conversionRate = 0.0;
    
    @Column(name = "total_revenue")
    private Double totalRevenue = 0.0;
    
    // Helper methods
    
    public void incrementView() {
        this.viewCount++;
    }
    
    public void incrementPurchase(double amount) {
        this.purchaseCount++;
        this.totalRevenue += amount;
        updateConversionRate();
    }
    
    private void updateConversionRate() {
        if (viewCount > 0) {
            this.conversionRate = (double) purchaseCount / viewCount;
        }
    }
}
```

### 5. EventType (Simplified Enum)

**Keep it simple: 10-15 core types**

```java
package com.ai.behavior.model;

/**
 * EventType - Simplified behavior event types
 * 
 * Reduced from 115 types to ~10 essential types.
 * 
 * Principle: Simple events tracked here.
 * Complex business events (orders, payments) stay in their own domains.
 * System events (monitoring, logs) go to separate logging system.
 */
public enum EventType {
    
    // Viewing & Navigation (most common)
    VIEW,              // User viewed something (product, page, content)
    NAVIGATION,        // User navigated to a page
    
    // Interaction
    CLICK,             // User clicked something
    SEARCH,            // User performed a search
    FILTER,            // User applied filters/sorting
    
    // Conversion funnel
    ADD_TO_CART,       // User added item to cart
    REMOVE_FROM_CART,  // User removed item from cart
    PURCHASE,          // User completed purchase
    WISHLIST,          // User added to wishlist
    
    // User-generated content (text - needs embedding)
    FEEDBACK,          // User submitted feedback
    REVIEW,            // User wrote a review
    RATING,            // User rated something
    
    // Engagement
    SHARE,             // User shared content
    SAVE,              // User saved/bookmarked
    
    // Generic fallback
    CUSTOM             // Custom event type (use metadata.customType)
}
```

---

## Implementation Components

### 1. BehaviorIngestionService

**Main service for receiving events**

```java
package com.ai.behavior.ingestion;

import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.ingestion.validator.BehaviorEventValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * BehaviorIngestionService
 * 
 * Main entry point for behavior event ingestion.
 * 
 * Flow:
 * 1. Validate event
 * 2. Enrich event (add system metadata)
 * 3. Store via sink (pluggable)
 * 4. Publish for async processing
 * 5. Return immediately (non-blocking)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorIngestionService {
    
    private final BehaviorEventValidator validator;
    private final BehaviorEventSink sink;
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * Ingest a single behavior event
     */
    @Transactional
    public void ingest(BehaviorEvent event) {
        log.debug("Ingesting behavior event: type={}, userId={}", 
            event.getEventType(), event.getUserId());
        
        try {
            // 1. Validate
            validator.validate(event);
            
            // 2. Enrich
            enrichEvent(event);
            
            // 3. Store (via pluggable sink)
            sink.accept(event);
            
            // 4. Publish for async processing
            eventPublisher.publishEvent(new BehaviorEventIngested(event));
            
            log.debug("Successfully ingested event: {}", event.getId());
            
        } catch (Exception e) {
            log.error("Failed to ingest event: {}", event, e);
            throw new BehaviorIngestionException("Failed to ingest event", e);
        }
    }
    
    /**
     * Ingest multiple events in batch (more efficient)
     */
    @Transactional
    public void ingestBatch(List<BehaviorEvent> events) {
        log.info("Ingesting batch of {} events", events.size());
        
        try {
            // Validate all
            events.forEach(validator::validate);
            
            // Enrich all
            events.forEach(this::enrichEvent);
            
            // Store batch (more efficient)
            sink.acceptBatch(events);
            
            // Publish batch event
            eventPublisher.publishEvent(new BehaviorEventBatchIngested(events));
            
            log.info("Successfully ingested batch of {} events", events.size());
            
        } catch (Exception e) {
            log.error("Failed to ingest batch", e);
            throw new BehaviorIngestionException("Failed to ingest batch", e);
        }
    }
    
    /**
     * Enrich event with system metadata
     */
    private void enrichEvent(BehaviorEvent event) {
        // Set ID if not present
        if (event.getId() == null) {
            event.setId(UUID.randomUUID());
        }
        
        // Set ingestion timestamp
        if (event.getIngestedAt() == null) {
            event.setIngestedAt(LocalDateTime.now());
        }
        
        // Set event timestamp if not present
        if (event.getTimestamp() == null) {
            event.setTimestamp(LocalDateTime.now());
        }
        
        // Add system metadata
        if (event.getMetadata() == null) {
            event.setMetadata(new java.util.HashMap<>());
        }
        event.getMetadata().put("_ingested_at", event.getIngestedAt().toString());
        event.getMetadata().put("_version", "1.0");
    }
}

/**
 * Event published after successful ingestion
 * Picked up by async workers
 */
class BehaviorEventIngested {
    private final BehaviorEvent event;
    
    public BehaviorEventIngested(BehaviorEvent event) {
        this.event = event;
    }
    
    public BehaviorEvent getEvent() {
        return event;
    }
}

class BehaviorEventBatchIngested {
    private final List<BehaviorEvent> events;
    
    public BehaviorEventBatchIngested(List<BehaviorEvent> events) {
        this.events = events;
    }
    
    public List<BehaviorEvent> getEvents() {
        return events;
    }
}
```

### 2. DatabaseEventSink (Default Implementation)

```java
package com.ai.behavior.ingestion.impl;

import com.ai.behavior.ingestion.BehaviorEventSink;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.storage.BehaviorEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DatabaseEventSink - Default storage implementation
 * 
 * Stores events in PostgreSQL database.
 * Active by default unless user configures a different sink.
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "ai.behavior.sink.type",
    havingValue = "database",
    matchIfMissing = true  // Default
)
@RequiredArgsConstructor
public class DatabaseEventSink implements BehaviorEventSink {
    
    private final BehaviorEventRepository repository;
    
    @Override
    public void accept(BehaviorEvent event) {
        log.trace("Storing event in database: {}", event.getId());
        repository.save(event);
    }
    
    @Override
    public void acceptBatch(List<BehaviorEvent> events) {
        log.debug("Storing batch of {} events in database", events.size());
        repository.saveAll(events);
    }
    
    @Override
    public String getSinkType() {
        return "database";
    }
}
```

### 3. Async Processing Workers

#### RealTimeAggregationWorker

```java
package com.ai.behavior.processing.worker;

import com.ai.behavior.ingestion.BehaviorEventIngested;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorMetrics;
import com.ai.behavior.storage.BehaviorMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * RealTimeAggregationWorker
 * 
 * Listens to ingested events and updates metrics in real-time.
 * Runs async, doesn't block ingestion.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealTimeAggregationWorker {
    
    private final BehaviorMetricsRepository metricsRepository;
    
    @Async
    @EventListener
    public void onEventIngested(BehaviorEventIngested event) {
        BehaviorEvent behaviorEvent = event.getEvent();
        
        // Skip anonymous events for user metrics
        if (behaviorEvent.getUserId() == null) {
            return;
        }
        
        try {
            updateMetrics(behaviorEvent);
        } catch (Exception e) {
            log.error("Failed to update metrics for event: {}", behaviorEvent.getId(), e);
            // Don't throw - metrics are non-critical
        }
    }
    
    private void updateMetrics(BehaviorEvent event) {
        LocalDate date = event.getTimestamp().toLocalDate();
        
        // Get or create metrics for user + date
        BehaviorMetrics metrics = metricsRepository
            .findByUserIdAndMetricDate(event.getUserId(), date)
            .orElseGet(() -> BehaviorMetrics.builder()
                .userId(event.getUserId())
                .metricDate(date)
                .build());
        
        // Update based on event type
        switch (event.getEventType()) {
            case "view":
                metrics.incrementView();
                break;
            case "click":
                metrics.setClickCount(metrics.getClickCount() + 1);
                break;
            case "search":
                metrics.setSearchCount(metrics.getSearchCount() + 1);
                break;
            case "add_to_cart":
                metrics.setAddToCartCount(metrics.getAddToCartCount() + 1);
                break;
            case "purchase":
                Double amount = (Double) event.getMetadata().get("amount");
                metrics.incrementPurchase(amount != null ? amount : 0.0);
                break;
        }
        
        metricsRepository.save(metrics);
    }
}
```

#### PatternDetectionWorker

```java
package com.ai.behavior.processing.worker;

import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.processing.analyzer.PatternAnalyzer;
import com.ai.behavior.storage.BehaviorDataProvider;
import com.ai.behavior.storage.BehaviorInsightsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PatternDetectionWorker
 * 
 * Scheduled worker that detects patterns in user behavior.
 * Runs every 5 minutes for active users.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatternDetectionWorker {
    
    private final BehaviorDataProvider dataProvider;
    private final BehaviorInsightsRepository insightsRepository;
    private final PatternAnalyzer patternAnalyzer;
    
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void detectPatterns() {
        log.info("Starting pattern detection worker");
        
        try {
            // Get users with recent activity (last 5 minutes)
            List<UUID> activeUsers = getRecentlyActiveUsers();
            
            log.info("Analyzing patterns for {} active users", activeUsers.size());
            
            // Analyze each user
            activeUsers.forEach(this::analyzeUserPatterns);
            
            log.info("Pattern detection completed");
            
        } catch (Exception e) {
            log.error("Pattern detection worker failed", e);
        }
    }
    
    private void analyzeUserPatterns(UUID userId) {
        try {
            // Get last 24 hours of events
            List<BehaviorEvent> events = dataProvider.getRecentEvents(userId, 1000);
            
            if (events.isEmpty()) {
                return;
            }
            
            // Analyze patterns using ai-core
            BehaviorInsights insights = patternAnalyzer.analyze(userId, events);
            
            // Set expiration (5 minutes from now)
            insights.setValidUntil(LocalDateTime.now().plusMinutes(5));
            
            // Save insights
            insightsRepository.save(insights);
            
        } catch (Exception e) {
            log.error("Failed to analyze patterns for user: {}", userId, e);
        }
    }
    
    private List<UUID> getRecentlyActiveUsers() {
        // Query for users with events in last 5 minutes
        // Implementation depends on your storage
        return List.of(); // Placeholder
    }
}
```

#### EmbeddingGenerationWorker

```java
package com.ai.behavior.processing.worker;

import com.ai.behavior.ingestion.BehaviorEventIngested;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorEmbedding;
import com.ai.behavior.storage.BehaviorEmbeddingRepository;
import com.ai.infrastructure.core.AICoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * EmbeddingGenerationWorker
 * 
 * SELECTIVELY generates embeddings only for text content.
 * 
 * Generates embeddings for:
 * - feedback
 * - review
 * - search queries (complex ones)
 * 
 * Does NOT generate for:
 * - view, click, purchase (no semantic value)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingGenerationWorker {
    
    private final AICoreService aiCoreService;
    private final BehaviorEmbeddingRepository embeddingRepository;
    
    @Async
    @EventListener
    public void onEventIngested(BehaviorEventIngested event) {
        BehaviorEvent behaviorEvent = event.getEvent();
        
        // Check if this event needs embedding
        if (!shouldGenerateEmbedding(behaviorEvent)) {
            return;
        }
        
        try {
            generateEmbedding(behaviorEvent);
        } catch (Exception e) {
            log.error("Failed to generate embedding for event: {}", behaviorEvent.getId(), e);
        }
    }
    
    private boolean shouldGenerateEmbedding(BehaviorEvent event) {
        // Only generate for events with text content
        switch (event.getEventType()) {
            case "feedback":
            case "review":
            case "search":
                return extractText(event) != null;
            default:
                return false;
        }
    }
    
    private void generateEmbedding(BehaviorEvent event) {
        String text = extractText(event);
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        log.debug("Generating embedding for event: {} (type: {})", 
            event.getId(), event.getEventType());
        
        // Generate embedding using ai-core
        float[] embedding = aiCoreService.generateEmbedding(text);
        
        // Store embedding
        BehaviorEmbedding behaviorEmbedding = BehaviorEmbedding.builder()
            .behaviorEventId(event.getId())
            .embeddingType(event.getEventType())
            .originalText(text)
            .embedding(embedding)
            .model("text-embedding-3-small")
            .build();
        
        embeddingRepository.save(behaviorEmbedding);
        
        log.debug("Successfully generated embedding for event: {}", event.getId());
    }
    
    private String extractText(BehaviorEvent event) {
        // Extract text from metadata based on event type
        if (event.getMetadata() == null) {
            return null;
        }
        
        switch (event.getEventType()) {
            case "feedback":
                return (String) event.getMetadata().get("feedback_text");
            case "review":
                return (String) event.getMetadata().get("review_text");
            case "search":
                return (String) event.getMetadata().get("query");
            default:
                return null;
        }
    }
}
```

---

## Integration Patterns

### 1. Web Application Integration

```javascript
// Frontend SDK: behavior-tracker.js

class BehaviorTracker {
  constructor(apiUrl, userId, sessionId) {
    this.apiUrl = apiUrl;
    this.userId = userId;
    this.sessionId = sessionId;
    this.queue = [];
    this.flushInterval = 5000; // Flush every 5 seconds
    this.startAutoFlush();
  }

  // Track a behavior event
  track(eventType, entityType, entityId, metadata = {}) {
    const event = {
      userId: this.userId,
      eventType: eventType,
      entityType: entityType,
      entityId: entityId,
      sessionId: this.sessionId,
      timestamp: new Date().toISOString(),
      metadata: {
        ...metadata,
        source: 'web_app',
        page: window.location.pathname,
        referrer: document.referrer,
        userAgent: navigator.userAgent
      }
    };

    this.queue.push(event);

    // Flush immediately for critical events
    if (this.isCriticalEvent(eventType)) {
      this.flush();
    }
  }

  // Convenience methods
  trackView(entityType, entityId) {
    this.track('view', entityType, entityId);
  }

  trackClick(elementId, metadata = {}) {
    this.track('click', 'ui_element', elementId, metadata);
  }

  trackPurchase(orderId, amount, items) {
    this.track('purchase', 'order', orderId, {
      amount: amount,
      items: items,
      currency: 'USD'
    });
  }

  trackSearch(query) {
    this.track('search', null, null, {
      query: query
    });
  }

  trackFeedback(feedbackText, category) {
    this.track('feedback', 'app', null, {
      feedback_text: feedbackText,
      category: category
    });
  }

  // Flush queue to server
  async flush() {
    if (this.queue.length === 0) {
      return;
    }

    const eventsToSend = [...this.queue];
    this.queue = [];

    try {
      await fetch(`${this.apiUrl}/api/ai-behavior/ingest/batch`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.getAuthToken()}`
        },
        body: JSON.stringify({ events: eventsToSend })
      });
    } catch (error) {
      console.error('Failed to send behavior events:', error);
      // Re-queue on failure
      this.queue.push(...eventsToSend);
    }
  }

  // Auto flush
  startAutoFlush() {
    setInterval(() => this.flush(), this.flushInterval);
  }

  isCriticalEvent(eventType) {
    return ['purchase', 'feedback'].includes(eventType);
  }

  getAuthToken() {
    // Get from your auth system
    return localStorage.getItem('auth_token');
  }
}

// Usage in React app
import { useEffect } from 'react';

const tracker = new BehaviorTracker(
  process.env.REACT_APP_API_URL,
  currentUser.id,
  generateSessionId()
);

// Track product view
function ProductPage({ product }) {
  useEffect(() => {
    tracker.trackView('product', product.id);
  }, [product.id]);

  const handleAddToCart = () => {
    tracker.track('add_to_cart', 'product', product.id, {
      price: product.price,
      quantity: 1
    });
    // ... rest of add to cart logic
  };

  return (
    <div>
      <h1>{product.name}</h1>
      <button onClick={handleAddToCart}>Add to Cart</button>
    </div>
  );
}
```

### 2. Mobile App Integration

```java
// Android SDK: BehaviorTracker.java

package com.ai.behavior.sdk;

import java.util.*;
import java.util.concurrent.*;

public class BehaviorTracker {
    private final String apiUrl;
    private final UUID userId;
    private final String sessionId;
    private final Queue<BehaviorEvent> queue;
    private final ScheduledExecutorService executor;
    
    public BehaviorTracker(String apiUrl, UUID userId) {
        this.apiUrl = apiUrl;
        this.userId = userId;
        this.sessionId = UUID.randomUUID().toString();
        this.queue = new ConcurrentLinkedQueue<>();
        this.executor = Executors.newSingleThreadScheduledExecutor();
        startAutoFlush();
    }
    
    public void track(String eventType, String entityType, String entityId, Map<String, Object> metadata) {
        BehaviorEvent event = BehaviorEvent.builder()
            .userId(userId)
            .eventType(eventType)
            .entityType(entityType)
            .entityId(entityId)
            .sessionId(sessionId)
            .timestamp(LocalDateTime.now())
            .metadata(enrichMetadata(metadata))
            .build();
        
        queue.add(event);
        
        if (isCriticalEvent(eventType)) {
            flush();
        }
    }
    
    // Convenience methods
    public void trackView(String entityType, String entityId) {
        track("view", entityType, entityId, Map.of());
    }
    
    public void trackPurchase(String orderId, double amount) {
        track("purchase", "order", orderId, Map.of(
            "amount", amount,
            "currency", "USD"
        ));
    }
    
    private Map<String, Object> enrichMetadata(Map<String, Object> metadata) {
        Map<String, Object> enriched = new HashMap<>(metadata);
        enriched.put("source", "mobile_app");
        enriched.put("device", Build.MODEL);
        enriched.put("os_version", Build.VERSION.RELEASE);
        enriched.put("app_version", BuildConfig.VERSION_NAME);
        return enriched;
    }
    
    private void flush() {
        if (queue.isEmpty()) {
            return;
        }
        
        List<BehaviorEvent> events = new ArrayList<>();
        while (!queue.isEmpty()) {
            events.add(queue.poll());
        }
        
        // Send to API (async)
        CompletableFuture.runAsync(() -> sendToAPI(events));
    }
    
    private void sendToAPI(List<BehaviorEvent> events) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "/api/ai-behavior/ingest/batch"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(events)))
                .build();
            
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            Log.e("BehaviorTracker", "Failed to send events", e);
            // Re-queue
            queue.addAll(events);
        }
    }
    
    private void startAutoFlush() {
        executor.scheduleAtFixedRate(this::flush, 5, 5, TimeUnit.SECONDS);
    }
}
```

### 3. External System Integration (Adapter)

```java
package com.ai.behavior.adapter;

import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorQuery;
import com.ai.behavior.storage.BehaviorDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter for integrating with external analytics (e.g., Mixpanel, Amplitude)
 * 
 * Allows querying behavior data from external system instead of local DB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalAnalyticsAdapter implements BehaviorDataProvider {
    
    private final MixpanelClient mixpanel;  // External analytics client
    
    @Override
    public List<BehaviorEvent> query(BehaviorQuery query) {
        log.debug("Querying behaviors from Mixpanel for user: {}", query.getUserId());
        
        try {
            // Query external analytics
            List<MixpanelEvent> externalEvents = mixpanel.queryEvents(
                query.getUserId().toString(),
                query.getStartTime(),
                query.getEndTime()
            );
            
            // Convert to BehaviorEvent
            return externalEvents.stream()
                .map(this::convertToBehaviorEvent)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to query Mixpanel", e);
            return List.of();
        }
    }
    
    private BehaviorEvent convertToBehaviorEvent(MixpanelEvent mixpanelEvent) {
        return BehaviorEvent.builder()
            .userId(UUID.fromString(mixpanelEvent.getUserId()))
            .eventType(mapEventType(mixpanelEvent.getEventName()))
            .entityType(mixpanelEvent.getProperties().get("entity_type"))
            .entityId(mixpanelEvent.getProperties().get("entity_id"))
            .timestamp(mixpanelEvent.getTimestamp())
            .metadata(mixpanelEvent.getProperties())
            .build();
    }
    
    private String mapEventType(String mixpanelEventName) {
        // Map external event names to your event types
        switch (mixpanelEventName) {
            case "Product Viewed": return "view";
            case "Item Added": return "add_to_cart";
            case "Order Completed": return "purchase";
            default: return "custom";
        }
    }
    
    @Override
    public String getProviderType() {
        return "mixpanel";
    }
}
```

---

## Embedding Strategy

### When to Generate Embeddings

```
┌─────────────────────────────────────────────────────────────┐
│                    EMBEDDING DECISION TREE                   │
└─────────────────────────────────────────────────────────────┘

Event Type?
    │
    ├─ Simple Event (view, click, purchase)
    │  └─> NO EMBEDDING
    │      Reason: No semantic value
    │      Cost: $0
    │
    ├─ User Feedback
    │  └─> YES EMBEDDING
    │      Reason: Need to find similar feedback
    │      Cost: ~$0.0001 per event
    │      Value: Categorization, sentiment, similar issue detection
    │
    ├─ Product Review
    │  └─> YES EMBEDDING
    │      Reason: Semantic search, sentiment analysis
    │      Cost: ~$0.0001 per event
    │      Value: Find similar reviews, detect patterns
    │
    ├─ Search Query (short: "watch")
    │  └─> NO EMBEDDING
    │      Reason: Simple keyword search sufficient
    │      Cost: $0
    │
    └─ Search Query (complex: "luxury watch for women under $500")
       └─> YES EMBEDDING
           Reason: Intent understanding needed
           Cost: ~$0.0001 per query
           Value: Better search results, personalization
```

### Cost Analysis

```
Scenario: E-commerce site, 10,000 active users/day

WITHOUT selective embedding (current approach):
- Events per day: 500,000 (50 events/user average)
- All events embedded: 500,000 embeddings/day
- Cost per embedding: $0.0001
- Daily cost: $50
- Monthly cost: $1,500
- Annual cost: $18,000

WITH selective embedding (proposed):
- Events per day: 500,000
- Text events (5%): 25,000
  - Feedback: 5,000
  - Reviews: 10,000
  - Complex searches: 10,000
- Embeddings per day: 25,000
- Cost per embedding: $0.0001
- Daily cost: $2.50
- Monthly cost: $75
- Annual cost: $900

SAVINGS: $17,100/year (95% reduction)
```

---

## Configuration

### Application Properties

```yaml
# application.yml

ai:
  behavior:
    # Module enabled
    enabled: true
    
    # Ingestion configuration
    ingestion:
      validation:
        enabled: true
        strict-mode: false  # Reject invalid events or log and continue
      batch-size: 1000
    
    # Storage configuration
    sink:
      type: database  # Options: database, kafka, redis, hybrid, custom
      # Custom sink class (if type=custom)
      # custom-class: com.mycompany.CustomEventSink
      
      # Database sink configuration
      database:
        batch-insert: true
        batch-size: 100
      
      # Kafka sink configuration (if type=kafka)
      kafka:
        topic: behavior-events
        compression: snappy
        
      # Redis sink configuration (if type=redis)
      redis:
        ttl-days: 7
        
      # Hybrid sink configuration (if type=hybrid)
      hybrid:
        hot-storage: redis     # Recent events
        hot-retention-days: 7
        cold-storage: database # Historical events
    
    # Processing configuration
    processing:
      # Real-time aggregation
      aggregation:
        enabled: true
        async: true
      
      # Pattern detection
      pattern-detection:
        enabled: true
        schedule: "*/5 * * * *"  # Every 5 minutes (cron)
        analysis-window-hours: 24
        min-events-for-analysis: 10
      
      # Anomaly detection
      anomaly-detection:
        enabled: true
        schedule: "* * * * *"  # Every minute
        sensitivity: 0.8  # 0.0 (low) to 1.0 (high)
      
      # Embedding generation
      embedding:
        enabled: true
        async: true
        event-types:  # Only generate for these
          - feedback
          - review
          - search
        min-text-length: 10  # Don't embed very short text
      
      # User segmentation
      segmentation:
        enabled: true
        schedule: "0 2 * * *"  # Daily at 2 AM
    
    # Insights configuration
    insights:
      cache-ttl-minutes: 5
      min-events-for-insights: 10
    
    # Retention configuration
    retention:
      events-days: 90         # Raw events kept for 90 days
      insights-days: 180      # Insights kept longer
      metrics-days: 365       # Metrics kept for 1 year
      embeddings-days: 90     # Same as events
    
    # Performance configuration
    performance:
      async-executor:
        core-pool-size: 4
        max-pool-size: 16
        queue-capacity: 1000
```

### Programmatic Configuration

```java
package com.ai.behavior.config;

import com.ai.behavior.ingestion.BehaviorEventSink;
import com.ai.behavior.storage.BehaviorDataProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomBehaviorConfiguration {
    
    /**
     * Custom sink implementation
     * Will be used instead of default if defined
     */
    @Bean
    public BehaviorEventSink customEventSink() {
        return new MyCustomSink();
    }
    
    /**
     * Custom data provider
     * Can define multiple providers for different sources
     */
    @Bean
    public BehaviorDataProvider legacySystemProvider() {
        return new LegacySystemBehaviorProvider();
    }
    
    /**
     * Custom analyzer
     */
    @Bean
    public BehaviorAnalyzer churnPredictionAnalyzer() {
        return new ChurnPredictionAnalyzer();
    }
}
```

---

## Migration Strategy

### Phase 1: Setup New Module (Week 1)

**Goals:**
- Create ai-behavior module structure
- Define interfaces
- Implement basic models

**Tasks:**
```bash
# 1. Create module
mkdir -p ai-behavior-module/src/main/java/com/ai/behavior

# 2. Create pom.xml with dependencies
# 3. Create database migration scripts
# 4. Implement core interfaces
# 5. Add unit tests
```

**Deliverables:**
- ✅ Module compiles
- ✅ Database tables created
- ✅ Core interfaces defined
- ✅ Basic tests passing

---

### Phase 2: Implement Storage Layer (Week 2)

**Goals:**
- Implement default storage (database sink)
- Implement query layer
- Add indexes

**Tasks:**
```sql
-- Database migrations
CREATE TABLE behavior_events (
    id UUID PRIMARY KEY,
    user_id UUID,
    event_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id VARCHAR(255),
    session_id VARCHAR(255),
    timestamp TIMESTAMP NOT NULL,
    metadata JSONB,
    ingested_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Critical indexes
CREATE INDEX idx_behavior_user_time 
    ON behavior_events(user_id, timestamp DESC);
    
CREATE INDEX idx_behavior_entity 
    ON behavior_events(entity_type, entity_id);
    
CREATE INDEX idx_behavior_timestamp 
    ON behavior_events(timestamp DESC);

-- Partitioning by date for automatic cleanup
CREATE TABLE behavior_events_2025_11 
    PARTITION OF behavior_events
    FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');
```

**Implementation:**
- DatabaseEventSink
- DatabaseBehaviorProvider
- BehaviorEventRepository

**Deliverables:**
- ✅ Can write events
- ✅ Can query events
- ✅ Indexes created
- ✅ Integration tests passing

---

### Phase 3: Implement Ingestion API (Week 3)

**Goals:**
- REST API for event ingestion
- Validation
- SDK for easy integration

**Endpoints:**
```
POST   /api/ai-behavior/ingest/event        # Single event
POST   /api/ai-behavior/ingest/batch        # Batch events
GET    /api/ai-behavior/events/{id}         # Get event
GET    /api/ai-behavior/users/{id}/events   # User events
GET    /api/ai-behavior/users/{id}/insights # User insights (stub)
```

**Deliverables:**
- ✅ Ingestion API working
- ✅ Validation working
- ✅ SDK for JavaScript
- ✅ SDK for Java/Android
- ✅ API tests passing

---

### Phase 4: Implement Async Processing (Week 4)

**Goals:**
- Real-time aggregation worker
- Pattern detection worker (basic)
- Embedding generation worker (selective)

**Implementation:**
- RealTimeAggregationWorker (metrics)
- PatternDetectionWorker (scheduled)
- EmbeddingGenerationWorker (selective)

**Deliverables:**
- ✅ Metrics updated in real-time
- ✅ Patterns detected (basic)
- ✅ Embeddings generated (text only)
- ✅ Async tests passing

---

### Phase 5: Migrate Existing Data (Week 5)

**Goals:**
- Migrate from old Behavior/UserBehavior tables
- Validate data integrity
- Run dual-write temporarily

**Migration script:**
```java
@Component
public class BehaviorMigrationService {
    
    @Autowired
    private BehaviorRepository oldBehaviorRepo;
    
    @Autowired
    private UserBehaviorRepository oldUserBehaviorRepo;
    
    @Autowired
    private BehaviorIngestionService newIngestionService;
    
    @Transactional
    public void migrateOldBehaviors() {
        log.info("Starting behavior migration");
        
        // Migrate from old Behavior table
        List<Behavior> oldBehaviors = oldBehaviorRepo.findAll();
        log.info("Migrating {} behaviors from old table", oldBehaviors.size());
        
        List<BehaviorEvent> newEvents = oldBehaviors.stream()
            .map(this::convertToNewEvent)
            .toList();
        
        newIngestionService.ingestBatch(newEvents);
        
        // Migrate from UserBehavior table
        List<UserBehavior> oldUserBehaviors = oldUserBehaviorRepo.findAll();
        log.info("Migrating {} user behaviors from old table", oldUserBehaviors.size());
        
        List<BehaviorEvent> newUserEvents = oldUserBehaviors.stream()
            .map(this::convertUserBehaviorToEvent)
            .toList();
        
        newIngestionService.ingestBatch(newUserEvents);
        
        log.info("Migration completed");
    }
    
    private BehaviorEvent convertToNewEvent(Behavior old) {
        return BehaviorEvent.builder()
            .userId(old.getUserId())
            .eventType(simplifyEventType(old.getBehaviorType().name()))
            .entityType(old.getEntityType())
            .entityId(old.getEntityId())
            .sessionId(old.getSessionId())
            .timestamp(old.getCreatedAt())
            .metadata(buildMetadata(old))
            .build();
    }
    
    private String simplifyEventType(String oldType) {
        // Map old 115 types to new 15 types
        switch (oldType) {
            case "PRODUCT_VIEW":
            case "PAGE_VIEW":
            case "CATEGORY_VIEW":
                return "view";
            case "ADD_TO_CART":
                return "add_to_cart";
            case "PURCHASE":
            case "CHECKOUT_COMPLETE":
                return "purchase";
            case "SEARCH_QUERY":
                return "search";
            case "FEEDBACK":
            case "CUSTOMER_SUPPORT":
                return "feedback";
            case "REVIEW":
                return "review";
            default:
                return "custom";
        }
    }
}
```

**Deliverables:**
- ✅ All old data migrated
- ✅ Data validation passed
- ✅ No data loss

---

### Phase 6: Update Application Code (Week 6)

**Goals:**
- Update all code using old Behavior/UserBehavior
- Switch to new ai-behavior module
- Remove old adapters

**Changes:**
```java
// OLD CODE (remove):
@Autowired
private BehaviorService behaviorService;

@Autowired
private UserBehaviorRepository userBehaviorRepository;

List<UserBehavior> behaviors = userBehaviorRepository
    .findByUserIdOrderByCreatedAtDesc(userId);

// NEW CODE:
@Autowired
private BehaviorIngestionService ingestionService;

@Autowired
private BehaviorInsightsService insightsService;

// Track event
ingestionService.ingest(BehaviorEvent.builder()
    .userId(userId)
    .eventType("view")
    .entityType("product")
    .entityId(productId)
    .build());

// Query insights (fast - pre-computed)
BehaviorInsights insights = insightsService.getUserInsights(userId);
```

**Deliverables:**
- ✅ All application code updated
- ✅ Old dependencies removed
- ✅ Application tests passing

---

### Phase 7: Deprecate Old System (Week 7)

**Goals:**
- Mark old Behavior/UserBehavior as deprecated
- Stop writing to old tables
- Keep for read-only temporarily

**Steps:**
1. Add @Deprecated annotations
2. Stop writes to old tables
3. Monitor for any usage
4. Plan deletion date

**Deliverables:**
- ✅ Old system deprecated
- ✅ No new writes
- ✅ Monitoring in place

---

### Phase 8: Delete Old System (Week 8+)

**Goals:**
- Delete old Behavior/UserBehavior entities
- Drop old tables
- Clean up code

**Final cleanup:**
```sql
-- After confirming no usage for 2+ weeks
DROP TABLE behaviors CASCADE;
DROP TABLE user_behaviors CASCADE;
```

```bash
# Delete old code
rm -rf ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/entity/Behavior.java
rm -rf backend/src/main/java/com/easyluxury/entity/UserBehavior.java
rm -rf backend/src/main/java/com/easyluxury/ai/adapter/UserBehaviorAdapter.java
```

**Deliverables:**
- ✅ Old system completely removed
- ✅ Code cleaned up
- ✅ Documentation updated

---

## Usage Examples

### Example 1: E-commerce Product Tracking

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    private final BehaviorIngestionService behaviorIngestion;
    
    public Product getProduct(UUID productId, UUID userId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
        
        // Track view (async, non-blocking)
        behaviorIngestion.ingest(BehaviorEvent.builder()
            .userId(userId)
            .eventType("view")
            .entityType("product")
            .entityId(productId.toString())
            .metadata(Map.of(
                "product_name", product.getName(),
                "price", product.getPrice(),
                "category", product.getCategory()
            ))
            .build());
        
        return product;
    }
    
    public void addToCart(UUID productId, UUID userId, int quantity) {
        // Business logic...
        
        // Track add to cart
        behaviorIngestion.ingest(BehaviorEvent.builder()
            .userId(userId)
            .eventType("add_to_cart")
            .entityType("product")
            .entityId(productId.toString())
            .metadata(Map.of(
                "quantity", quantity,
                "price", product.getPrice()
            ))
            .build());
    }
}
```

### Example 2: User Feedback with Embedding

```java
@Service
@RequiredArgsConstructor
public class FeedbackService {
    
    private final BehaviorIngestionService behaviorIngestion;
    
    public void submitFeedback(UUID userId, String feedbackText, String category) {
        // Track feedback event (will trigger embedding generation automatically)
        behaviorIngestion.ingest(BehaviorEvent.builder()
            .userId(userId)
            .eventType("feedback")
            .entityType("app")
            .entityId(null)
            .metadata(Map.of(
                "feedback_text", feedbackText,  // Will be embedded
                "category", category
            ))
            .build());
        
        // EmbeddingGenerationWorker will:
        // 1. Detect this is feedback (has text)
        // 2. Generate embedding asynchronously
        // 3. Store in behavior_embeddings table
        // 4. Enable semantic search for similar feedback
    }
    
    public List<BehaviorEvent> findSimilarFeedback(String feedbackText) {
        // Generate embedding for search query
        float[] queryEmbedding = aiCoreService.generateEmbedding(feedbackText);
        
        // Search for similar feedback
        return embeddingSearchService.findSimilar(
            queryEmbedding,
            "feedback",
            10  // Top 10 similar
        );
    }
}
```

### Example 3: Personalized Recommendations

```java
@Service
@RequiredArgsConstructor
public class RecommendationService {
    
    private final BehaviorInsightsService insightsService;
    private final BehaviorDataProvider behaviorProvider;
    private final ProductRepository productRepository;
    
    public List<Product> getPersonalizedRecommendations(UUID userId, int limit) {
        // Get pre-computed insights (fast!)
        BehaviorInsights insights = insightsService.getUserInsights(userId);
        
        if (insights == null || !insights.isValid()) {
            // Fallback to default recommendations
            return getDefaultRecommendations(limit);
        }
        
        // Use insights to personalize
        List<String> preferredCategories = (List<String>) 
            insights.getPreferences().get("preferred_categories");
        
        String priceRange = (String) 
            insights.getPreferences().get("price_range");
        
        // Query products based on preferences
        return productRepository.findByPreferences(
            preferredCategories,
            priceRange,
            limit
        );
    }
    
    private List<Product> getDefaultRecommendations(int limit) {
        return productRepository.findTopRated(limit);
    }
}
```

### Example 4: Anomaly Detection (Fraud)

```java
@Service
@RequiredArgsConstructor
public class FraudDetectionService {
    
    private final BehaviorDataProvider behaviorProvider;
    private final AlertService alertService;
    
    @Scheduled(fixedDelay = 60000) // Every minute
    public void detectSuspiciousActivity() {
        // Get last minute of purchase events
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        
        List<BehaviorEvent> recentPurchases = behaviorProvider.query(
            BehaviorQuery.builder()
                .eventType("purchase")
                .startTime(oneMinuteAgo)
                .build()
        );
        
        // Check for suspicious patterns
        recentPurchases.forEach(event -> {
            if (isSuspicious(event)) {
                alertService.sendAlert(new FraudAlert(
                    event.getUserId(),
                    "Suspicious purchase pattern detected",
                    event
                ));
            }
        });
    }
    
    private boolean isSuspicious(BehaviorEvent event) {
        // Multiple purchases in short time
        List<BehaviorEvent> userPurchases = behaviorProvider.query(
            BehaviorQuery.forUser(event.getUserId())
                .eventType("purchase")
                .lastMinutes(5)
        );
        
        if (userPurchases.size() > 5) {
            return true;  // 5+ purchases in 5 minutes
        }
        
        // High value purchase
        Double amount = (Double) event.getMetadata().get("amount");
        if (amount != null && amount > 10000) {
            return true;  // Purchase over $10k
        }
        
        return false;
    }
}
```

---

## Performance Considerations

### 1. Database Optimization

```sql
-- Partitioning by month (automatic old data deletion)
CREATE TABLE behavior_events (
    id UUID NOT NULL,
    user_id UUID,
    event_type VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    -- ... other columns
) PARTITION BY RANGE (timestamp);

CREATE TABLE behavior_events_2025_11 
    PARTITION OF behavior_events
    FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');

CREATE TABLE behavior_events_2025_12 
    PARTITION OF behavior_events
    FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');

-- Automatic cleanup: just drop old partitions
DROP TABLE behavior_events_2025_10;  -- Drops entire October instantly

-- Indexes on each partition automatically
CREATE INDEX idx_behavior_user_time_2025_11
    ON behavior_events_2025_11(user_id, timestamp DESC);
```

### 2. Query Optimization

```java
// BAD: Loading all events then filtering in app
List<BehaviorEvent> allEvents = repository.findByUserId(userId);
List<BehaviorEvent> purchases = allEvents.stream()
    .filter(e -> e.getEventType().equals("purchase"))
    .toList();

// GOOD: Filter in database
@Query("SELECT e FROM BehaviorEvent e WHERE e.userId = :userId AND e.eventType = :type ORDER BY e.timestamp DESC")
List<BehaviorEvent> findByUserIdAndEventType(UUID userId, String eventType);

// BETTER: Use pre-computed metrics
BehaviorMetrics metrics = metricsRepository.findByUserIdAndDate(userId, LocalDate.now());
int purchaseCount = metrics.getPurchaseCount();  // Instant!
```

### 3. Caching Strategy

```java
@Service
@RequiredArgsConstructor
public class BehaviorInsightsService {
    
    private final BehaviorInsightsRepository repository;
    private final PatternAnalyzer analyzer;
    
    @Cacheable(value = "behavior-insights", key = "#userId")
    public BehaviorInsights getUserInsights(UUID userId) {
        // Check cache first (Redis)
        // Cache TTL: 5 minutes
        
        return repository.findByUserIdAndValidUntilAfter(
            userId,
            LocalDateTime.now()
        ).orElseGet(() -> {
            // Not found or expired - recompute
            return analyzer.analyze(userId);
        });
    }
}
```

### 4. Batch Processing

```java
// BAD: Process events one by one
events.forEach(event -> {
    behaviorIngestion.ingest(event);  // N database writes
});

// GOOD: Batch insert
behaviorIngestion.ingestBatch(events);  // 1 database write

// BETTER: Batch with optimal size
Lists.partition(events, 1000).forEach(batch -> {
    behaviorIngestion.ingestBatch(batch);  // 1000 events per batch
});
```

### 5. Async Processing

```java
// BAD: Synchronous analysis (blocks user request)
@GetMapping("/recommendations")
public List<Product> getRecommendations(@RequestParam UUID userId) {
    BehaviorInsights insights = analyzer.analyze(userId);  // SLOW! 500ms+
    return recommendationEngine.generate(insights);
}

// GOOD: Use pre-computed insights
@GetMapping("/recommendations")
public List<Product> getRecommendations(@RequestParam UUID userId) {
    BehaviorInsights insights = insightsService.getUserInsights(userId);  // FAST! <10ms from cache
    return recommendationEngine.generate(insights);
}

// Background worker computes insights async every 5 minutes
@Scheduled(fixedDelay = 300000)
public void computeInsights() {
    List<UUID> activeUsers = getActiveUsers();
    activeUsers.forEach(userId -> {
        BehaviorInsights insights = analyzer.analyze(userId);
        repository.save(insights);
    });
}
```

### 6. Selective Embedding

```java
// BAD: Embed everything (expensive!)
events.forEach(event -> {
    float[] embedding = aiCore.generateEmbedding(event.toString());  // $$$$
    store(embedding);
});

// GOOD: Only embed text content
events.forEach(event -> {
    if (hasTextContent(event)) {  // Only feedback, reviews, search
        String text = extractText(event);
        if (text.length() >= 10) {  // Skip very short text
            float[] embedding = aiCore.generateEmbedding(text);
            store(embedding);
        }
    }
});
```

---

## Monitoring & Observability

### Metrics to Track

```java
@Component
@RequiredArgsConstructor
public class BehaviorMetrics {
    
    private final MeterRegistry meterRegistry;
    
    // Ingestion metrics
    public void recordEventIngested(String eventType) {
        Counter.builder("behavior.events.ingested")
            .tag("type", eventType)
            .register(meterRegistry)
            .increment();
    }
    
    // Processing metrics
    public void recordProcessingTime(String workerType, long durationMs) {
        Timer.builder("behavior.processing.duration")
            .tag("worker", workerType)
            .register(meterRegistry)
            .record(Duration.ofMillis(durationMs));
    }
    
    // Embedding metrics
    public void recordEmbeddingGenerated(String embeddingType, long durationMs, double cost) {
        Counter.builder("behavior.embeddings.generated")
            .tag("type", embeddingType)
            .register(meterRegistry)
            .increment();
        
        Counter.builder("behavior.embeddings.cost")
            .tag("type", embeddingType)
            .register(meterRegistry)
            .increment(cost);
    }
    
    // Error metrics
    public void recordError(String component, String errorType) {
        Counter.builder("behavior.errors")
            .tag("component", component)
            .tag("error_type", errorType)
            .register(meterRegistry)
            .increment();
    }
}
```

### Health Checks

```java
@Component
public class BehaviorHealthIndicator implements HealthIndicator {
    
    @Autowired
    private BehaviorEventRepository repository;
    
    @Autowired
    private BehaviorEventSink sink;
    
    @Override
    public Health health() {
        try {
            // Check database connectivity
            long eventCount = repository.count();
            
            // Check sink health
            String sinkType = sink.getSinkType();
            
            // Check recent ingestion (last 5 minutes)
            LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
            long recentEvents = repository.countByIngestedAtAfter(fiveMinutesAgo);
            
            if (recentEvents == 0) {
                return Health.down()
                    .withDetail("reason", "No events ingested in last 5 minutes")
                    .build();
            }
            
            return Health.up()
                .withDetail("total_events", eventCount)
                .withDetail("recent_events_5min", recentEvents)
                .withDetail("sink_type", sinkType)
                .build();
                
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

### Logging

```java
// Structured logging for debugging
log.info("Behavior event ingested", 
    kv("event_id", event.getId()),
    kv("user_id", event.getUserId()),
    kv("event_type", event.getEventType()),
    kv("entity_type", event.getEntityType()),
    kv("ingestion_time_ms", duration)
);

// Error logging with context
log.error("Failed to process behavior event",
    kv("event_id", event.getId()),
    kv("error", e.getMessage()),
    kv("worker", "PatternDetectionWorker"),
    e
);
```

---

## Implementation Roadmap

### Summary Timeline

```
Week 1:  ✅ Module setup & interfaces
Week 2:  ✅ Storage layer implementation
Week 3:  ✅ Ingestion API & SDKs
Week 4:  ✅ Async processing workers
Week 5:  ✅ Data migration
Week 6:  ✅ Application code update
Week 7:  ✅ Deprecate old system
Week 8+: ✅ Delete old system
```

### Success Criteria

- [ ] All events from all sources flowing to ai-behavior
- [ ] No blocking/synchronous AI processing
- [ ] Embeddings only for text content (<5% of events)
- [ ] Pre-computed insights available in <10ms
- [ ] Old Behavior/UserBehavior system completely removed
- [ ] 95% cost reduction on embeddings
- [ ] 90% reduction in storage
- [ ] Performance bottlenecks resolved

---

## Conclusion

This comprehensive solution provides:

1. **Clean separation**: Behavior tracking in separate module, out of ai-core
2. **Flexibility**: Pluggable storage, multiple ingestion sources, extensible analyzers
3. **Performance**: Async processing, selective embeddings, proper indexing
4. **Cost-effective**: 95% reduction in embedding costs
5. **Scalable**: Can handle high volume without blocking
6. **Maintainable**: Clear interfaces, well-documented, tested

**Next steps:** Begin Phase 1 implementation.

---

**End of Document**
