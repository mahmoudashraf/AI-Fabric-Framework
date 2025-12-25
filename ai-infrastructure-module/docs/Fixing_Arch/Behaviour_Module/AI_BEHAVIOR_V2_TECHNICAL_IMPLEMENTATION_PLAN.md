# AI Behavior Module v2 - Technical Implementation Plan

**Version:** 2.3  
**Status:** Ready for Implementation  
**Target Framework:** AI Fabric Framework  
**Technology Stack:** Spring Boot 3.x, Java 21, PostgreSQL, JPA

---

## 📋 Table of Contents

1. [Module Structure](#1-module-structure)
2. [Database Schema](#2-database-schema)
3. [Core Interfaces (SPI)](#3-core-interfaces-spi)
4. [Entity Models](#4-entity-models)
5. [Configuration Layer](#5-configuration-layer)
6. [Service Layer](#6-service-layer)
7. [Integration Points](#7-integration-points)
8. [LLM Prompt Templates](#8-llm-prompt-templates)
9. [Implementation Sequence](#9-implementation-sequence)
10. [Testing Strategy](#10-testing-strategy)

---

## 1. Module Structure

```
ai-infrastructure-behavior/
├── src/main/java/com/ai/infrastructure/behavior/
│   ├── annotation/
│   │   └── (None - reuses @AICapable from core)
│   ├── config/
│   │   ├── BehaviorAIAutoConfiguration.java
│   │   ├── BehaviorProperties.java
│   │   └── BehaviorPresetLoader.java
│   ├── entity/
│   │   └── BehaviorInsights.java
│   ├── repository/
│   │   └── BehaviorInsightsRepository.java
│   ├── service/
│   │   ├── BehaviorAnalysisService.java
│   │   ├── BehaviorQueryService.java
│   │   └── DefaultBehaviorInsightStore.java
│   ├── spi/
│   │   ├── ExternalEventProvider.java
│   │   ├── BehaviorInsightStore.java
│   │   └── UserEventBatch.java
│   └── dto/
│       ├── ExternalEvent.java
│       ├── AnalysisRequest.java
│       └── AnalysisResponse.java
├── src/main/resources/
│   ├── behavior-ai-light.yml
│   ├── behavior-ai-full.yml
│   └── db/migration/
│       └── V001__create_behavior_insights.sql
└── pom.xml
```

---

## 2. Database Schema

### 2.1 DDL for `ai_behavior_insights`

```sql
CREATE TABLE ai_behavior_insights (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    segment VARCHAR(100),
    patterns JSONB,
    recommendations JSONB,
    insights JSONB,
    analyzed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    confidence DOUBLE PRECISION,
    ai_model_used VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uk_user_id UNIQUE (user_id)
);

CREATE INDEX idx_behavior_insights_user_id ON ai_behavior_insights(user_id);
CREATE INDEX idx_behavior_insights_segment ON ai_behavior_insights(segment);
CREATE INDEX idx_behavior_insights_analyzed_at ON ai_behavior_insights(analyzed_at DESC);
```

### 2.2 Index Strategy
- **Primary Lookup**: `user_id` (unique index for fast personalization queries)
- **Segment Filtering**: `segment` (for statistics queries like "How many power users?")
- **Temporal Queries**: `analyzed_at` (for freshness checks and cooldown logic)

---

## 3. Core Interfaces (SPI)

### 3.1 ExternalEventProvider

```java
package com.ai.infrastructure.behavior.spi;

import java.util.List;
import java.util.UUID;

/**
 * Service Provider Interface for pulling user behavioral events.
 * Users implement this to bridge their app data with the Behavior Module.
 */
public interface ExternalEventProvider {
    
    /**
     * CASE 1: Targeted Fetch
     * Retrieves events for a specific user.
     * 
     * @param userId The target user's ID
     * @return List of events (can be empty if no new events)
     */
    List<ExternalEvent> getEventsForUser(UUID userId);
    
    /**
     * CASE 2: Discovery Fetch
     * Retrieves the next batch of events for any user who needs analysis.
     * The implementer decides who is "next" based on their own logic.
     * 
     * @return Batch containing userId and their events
     */
    UserEventBatch getNextUserEvents();
}
```

### 3.2 BehaviorInsightStore

```java
package com.ai.infrastructure.behavior.spi;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;

/**
 * Service Provider Interface for custom storage of behavioral insights.
 * If implemented, the module will delegate storage to this bean.
 * Otherwise, it uses the default JPA repository.
 */
public interface BehaviorInsightStore {
    
    /**
     * Called when a behavioral insight has been generated or updated.
     * 
     * @param insight The complete insight object
     */
    void storeInsight(BehaviorInsights insight);
    
    /**
     * Retrieve the most recent insight for a user.
     * 
     * @param userId The user's ID
     * @return The insight, or null if none exists
     */
    BehaviorInsights getInsightForUser(UUID userId);
}
```

### 3.3 Supporting DTOs

```java
package com.ai.infrastructure.behavior.spi;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserEventBatch {
    private UUID userId;
    private List<ExternalEvent> events;
}
```

```java
package com.ai.infrastructure.behavior.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ExternalEvent {
    private String eventType;           // e.g., "purchase", "page_view"
    private Map<String, Object> data;   // Flexible payload
    private LocalDateTime occurredAt;
    private String source;              // e.g., "web", "mobile"
}
```

---

## 4. Entity Models

### 4.1 BehaviorInsights Entity

```java
package com.ai.infrastructure.behavior.entity;

import com.ai.infrastructure.annotation.AICapable;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

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
@AICapable(entityType = "behavior-insight")
public class BehaviorInsights {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    
    @Column(name = "segment", length = 100)
    private String segment;
    
    @Type(JsonType.class)
    @Column(name = "patterns", columnDefinition = "jsonb")
    private List<String> patterns;
    
    @Type(JsonType.class)
    @Column(name = "recommendations", columnDefinition = "jsonb")
    private List<String> recommendations;
    
    @Type(JsonType.class)
    @Column(name = "insights", columnDefinition = "jsonb")
    private Map<String, Object> insights;
    
    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;
    
    @Column(name = "confidence")
    private Double confidence;
    
    @Column(name = "ai_model_used", length = 100)
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
     * Generates searchable content for AI indexing.
     * Called by the framework when @AIProcess indexing is enabled.
     */
    public String getSearchableContent() {
        return String.format(
            "User Segment: %s. Patterns: %s. Recommendations: %s. Confidence: %.2f",
            segment != null ? segment : "Unknown",
            patterns != null ? String.join(", ", patterns) : "None",
            recommendations != null ? String.join(", ", recommendations) : "None",
            confidence != null ? confidence : 0.0
        );
    }
}
```

---

## 5. Configuration Layer

### 5.1 BehaviorProperties

```java
package com.ai.infrastructure.behavior.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.behavior")
public class BehaviorProperties {
    
    private boolean enabled = true;
    
    /**
     * Mode: LIGHT (personalization) or FULL (discovery + statistics)
     */
    private Mode mode = Mode.LIGHT;
    
    private AnalysisConfig analysis = new AnalysisConfig();
    
    @Data
    public static class AnalysisConfig {
        /**
         * Minimum hours between re-analysis for the same user
         */
        private int cooldownHours = 12;
        
        /**
         * Minimum number of new events to trigger re-analysis
         */
        private int minEventThreshold = 5;
    }
    
    public enum Mode {
        LIGHT,  // No indexing, personalization only
        FULL    // Full indexing for discovery
    }
}
```

### 5.2 Preset YAML Files

**`behavior-ai-light.yml`**
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
        enable-analysis: false
```

**`behavior-ai-full.yml`**
```yaml
ai-entities:
  behavior-insight:
    auto-embedding: true
    indexable: true
    features: ["embedding", "search", "analysis"]
    searchable-fields:
      - name: "searchableContent"
        include-in-rag: true
        enable-semantic-search: true
        weight: 1.0
    metadata-fields:
      - name: "segment"
        type: "string"
        include-in-search: true
      - name: "confidence"
        type: "double"
        include-in-search: true
    crud-operations:
      create:
        generate-embedding: true
        index-for-search: true
        enable-analysis: false
```

### 5.3 Auto-Configuration

```java
package com.ai.infrastructure.behavior.config;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.behavior.service.BehaviorAnalysisService;
import com.ai.infrastructure.behavior.service.DefaultBehaviorInsightStore;
import com.ai.infrastructure.behavior.spi.BehaviorInsightStore;
import com.ai.infrastructure.behavior.spi.ExternalEventProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration
@EnableConfigurationProperties(BehaviorProperties.class)
@ConditionalOnProperty(prefix = "ai.behavior", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BehaviorAIAutoConfiguration {
    
    private final AIEntityConfigurationLoader frameworkConfigLoader;
    private final BehaviorProperties properties;
    
    public BehaviorAIAutoConfiguration(
            AIEntityConfigurationLoader frameworkConfigLoader,
            BehaviorProperties properties) {
        this.frameworkConfigLoader = frameworkConfigLoader;
        this.properties = properties;
    }
    
    @PostConstruct
    public void registerBehaviorPreset() {
        // Only register if user hasn't defined their own config
        if (!frameworkConfigLoader.hasEntityConfig("behavior-insight")) {
            String presetFile = "behavior-ai-" + 
                properties.getMode().name().toLowerCase() + ".yml";
            
            log.info("Loading Behavior AI preset: {}", presetFile);
            frameworkConfigLoader.loadConfigurationFromFile(presetFile);
            log.info("Behavior AI initialized in {} mode", properties.getMode());
        } else {
            log.info("User-defined behavior-insight config found, skipping preset");
        }
    }
    
    @Bean
    @ConditionalOnMissingBean
    public BehaviorInsightStore defaultBehaviorInsightStore() {
        return new DefaultBehaviorInsightStore();
    }
}
```

---

## 6. Service Layer

### 6.1 BehaviorAnalysisService (The Core Logic)

```java
package com.ai.infrastructure.behavior.service;

import com.ai.infrastructure.annotation.AIProcess;
import com.ai.infrastructure.behavior.config.BehaviorProperties;
import com.ai.infrastructure.behavior.dto.ExternalEvent;
import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.repository.BehaviorInsightsRepository;
import com.ai.infrastructure.behavior.spi.BehaviorInsightStore;
import com.ai.infrastructure.behavior.spi.ExternalEventProvider;
import com.ai.infrastructure.behavior.spi.UserEventBatch;
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
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorAnalysisService {
    
    private final ExternalEventProvider eventProvider;
    private final BehaviorInsightStore insightStore;
    private final BehaviorInsightsRepository repository;
    private final AICoreService aiCoreService;
    private final BehaviorProperties properties;
    private final ObjectMapper objectMapper;
    
    /**
     * CASE 1: Analyze a specific user (Targeted)
     */
    public BehaviorInsights analyzeUser(UUID userId) {
        log.info("Starting targeted analysis for user: {}", userId);
        
        // Step 1: Fetch existing insight
        BehaviorInsights existingInsight = insightStore.getInsightForUser(userId);
        
        // Step 2: Check cooldown
        if (!shouldAnalyze(existingInsight)) {
            log.debug("Skipping analysis for user {} (cooldown active)", userId);
            return existingInsight;
        }
        
        // Step 3: Pull new events
        List<ExternalEvent> events = eventProvider.getEventsForUser(userId);
        
        if (events.isEmpty()) {
            log.debug("No new events for user {}", userId);
            return existingInsight;
        }
        
        // Step 4: Perform evolutionary analysis
        return performAnalysis(userId, events, existingInsight);
    }
    
    /**
     * CASE 2: Process next available user (Discovery)
     */
    public BehaviorInsights analyzeNext() {
        log.info("Starting discovery analysis for next user");
        
        UserEventBatch batch = eventProvider.getNextUserEvents();
        
        if (batch == null || batch.getUserId() == null) {
            log.debug("No users available for discovery analysis");
            return null;
        }
        
        BehaviorInsights existingInsight = insightStore.getInsightForUser(batch.getUserId());
        return performAnalysis(batch.getUserId(), batch.getEvents(), existingInsight);
    }
    
    /**
     * Core analysis logic with LLM integration
     */
    @Transactional
    private BehaviorInsights performAnalysis(
            UUID userId, 
            List<ExternalEvent> events, 
            BehaviorInsights existingInsight) {
        
        log.info("Performing AI analysis for user {} with {} events", userId, events.size());
        
        try {
            // Build evolutionary prompt
            String prompt = buildEvolutionaryPrompt(events, existingInsight);
            
            // Call LLM
            AIGenerationRequest request = AIGenerationRequest.builder()
                .entityId(userId.toString())
                .entityType("behavior-analysis")
                .generationType("behavior-insight")
                .prompt(prompt)
                .temperature(0.3) // Lower temperature for consistent results
                .maxTokens(800)
                .build();
            
            AIGenerationResponse response = aiCoreService.generateContent(request);
            
            // Parse LLM response
            Map<String, Object> analysisResult = parseAnalysisResponse(response.getContent());
            
            // Build or update insight
            BehaviorInsights insight = buildInsight(userId, analysisResult, existingInsight);
            
            // Save and trigger @AIProcess if in FULL mode
            return saveInsight(insight);
            
        } catch (Exception e) {
            log.error("Analysis failed for user {}", userId, e);
            return existingInsight; // Return old insight on failure
        }
    }
    
    /**
     * Saves insight and triggers framework indexing via @AIProcess
     */
    @AIProcess(entityType = "behavior-insight", processType = "create")
    public BehaviorInsights saveInsight(BehaviorInsights insight) {
        // Framework intercepts this and handles indexing if FULL mode is enabled
        BehaviorInsights saved = repository.save(insight);
        
        // Also store in custom sink if available
        insightStore.storeInsight(saved);
        
        return saved;
    }
    
    private boolean shouldAnalyze(BehaviorInsights existing) {
        if (existing == null) return true;
        
        LocalDateTime cooldownExpiry = existing.getAnalyzedAt()
            .plusHours(properties.getAnalysis().getCooldownHours());
        
        return LocalDateTime.now().isAfter(cooldownExpiry);
    }
    
    private String buildEvolutionaryPrompt(
            List<ExternalEvent> events, 
            BehaviorInsights existingInsight) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze user behavioral evolution based on:\n\n");
        
        if (existingInsight != null) {
            prompt.append("PREVIOUS STATE:\n");
            prompt.append("- Segment: ").append(existingInsight.getSegment()).append("\n");
            prompt.append("- Patterns: ").append(existingInsight.getPatterns()).append("\n");
            prompt.append("- Last analyzed: ").append(existingInsight.getAnalyzedAt()).append("\n\n");
        } else {
            prompt.append("NEW USER (no previous insights)\n\n");
        }
        
        prompt.append("NEW EVENTS (").append(events.size()).append(" total):\n");
        events.forEach(event -> {
            prompt.append("- ").append(event.getEventType())
                .append(" at ").append(event.getOccurredAt())
                .append(" from ").append(event.getSource())
                .append("\n");
        });
        
        prompt.append("\nProvide analysis in JSON format:\n");
        prompt.append("{\n");
        prompt.append("  \"segment\": \"power_user|active|steady|dormant|at_risk\",\n");
        prompt.append("  \"patterns\": [\"pattern1\", \"pattern2\"],\n");
        prompt.append("  \"recommendations\": [\"action1\", \"action2\"],\n");
        prompt.append("  \"insights\": {\"key\": \"value\"},\n");
        prompt.append("  \"confidence\": 0.0-1.0\n");
        prompt.append("}\n");
        
        return prompt.toString();
    }
    
    private Map<String, Object> parseAnalysisResponse(String response) throws Exception {
        // Extract JSON from response
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        
        if (start == -1 || end == -1) {
            throw new IllegalStateException("No JSON found in LLM response");
        }
        
        String json = response.substring(start, end + 1);
        return objectMapper.readValue(json, Map.class);
    }
    
    private BehaviorInsights buildInsight(
            UUID userId, 
            Map<String, Object> analysisResult,
            BehaviorInsights existing) {
        
        BehaviorInsights.BehaviorInsightsBuilder builder = existing != null 
            ? existing.toBuilder() 
            : BehaviorInsights.builder().userId(userId);
        
        return builder
            .segment((String) analysisResult.get("segment"))
            .patterns((List<String>) analysisResult.get("patterns"))
            .recommendations((List<String>) analysisResult.get("recommendations"))
            .insights((Map<String, Object>) analysisResult.get("insights"))
            .confidence(((Number) analysisResult.get("confidence")).doubleValue())
            .analyzedAt(LocalDateTime.now())
            .aiModelUsed("gpt-4") // Or get from aiCoreService config
            .build();
    }
}
```

### 6.2 DefaultBehaviorInsightStore

```java
package com.ai.infrastructure.behavior.service;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.repository.BehaviorInsightsRepository;
import com.ai.infrastructure.behavior.spi.BehaviorInsightStore;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public class DefaultBehaviorInsightStore implements BehaviorInsightStore {
    
    private final BehaviorInsightsRepository repository;
    
    @Override
    public void storeInsight(BehaviorInsights insight) {
        // Default: Already saved by the service, this is a no-op
    }
    
    @Override
    public BehaviorInsights getInsightForUser(UUID userId) {
        return repository.findByUserId(userId).orElse(null);
    }
}
```

---

## 7. Integration Points

### 7.1 RelationshipQuery Integration

To enable queries like "Find all power users at risk of churn", the module integrates with the `RelationshipSchemaProvider`.

```java
// In your application configuration
@Bean
public RelationshipMapping behaviorInsightMapping(EntityRelationshipMapper mapper) {
    return mapper.registerRelationship(
        "user",                    // From entity
        "behavior-insight",        // To entity
        "behaviorInsight",         // Field name
        RelationshipDirection.FORWARD,
        false                      // Not optional
    );
}
```

---

## 8. LLM Prompt Templates

### 8.1 Evolutionary Analysis Prompt

```
Analyze user behavioral evolution based on:

PREVIOUS STATE:
- Segment: steady
- Patterns: [regular_logins, moderate_engagement]
- Last analyzed: 2024-12-20T10:30:00

NEW EVENTS (5 total):
- purchase at 2024-12-24T14:22:00 from web
- high_value_view at 2024-12-24T15:10:00 from mobile
- purchase at 2024-12-25T09:45:00 from web

Provide analysis in JSON format:
{
  "segment": "power_user|active|steady|dormant|at_risk",
  "patterns": ["pattern1", "pattern2"],
  "recommendations": ["action1", "action2"],
  "insights": {"engagement_trend": "increasing"},
  "confidence": 0.0-1.0
}
```

---

## 9. Implementation Sequence

### Phase 1: Foundation (Week 1)
- [ ] Create database migration script
- [ ] Implement `BehaviorInsights` entity
- [ ] Implement `BehaviorInsightsRepository`
- [ ] Write unit tests for entity

### Phase 2: SPI Definition (Week 1)
- [ ] Create `ExternalEventProvider` interface
- [ ] Create `BehaviorInsightStore` interface
- [ ] Create supporting DTOs
- [ ] Document SPI usage

### Phase 3: Configuration (Week 2)
- [ ] Implement `BehaviorProperties`
- [ ] Create preset YAML files
- [ ] Implement `BehaviorAIAutoConfiguration`
- [ ] Test mode switching (LIGHT/FULL)

### Phase 4: Core Logic (Week 2-3)
- [ ] Implement `BehaviorAnalysisService`
- [ ] Implement evolutionary prompting
- [ ] Implement cooldown logic
- [ ] Add `@AIProcess` integration
- [ ] Write integration tests

### Phase 5: Discovery Integration (Week 3)
- [ ] Register relationship mapping
- [ ] Test cross-user queries
- [ ] Verify `AISearchableEntity` creation in FULL mode

### Phase 6: Testing & Documentation (Week 4)
- [ ] End-to-end tests
- [ ] Performance tests
- [ ] Developer documentation
- [ ] Example implementations

---

## 10. Testing Strategy

### 10.1 Unit Tests

```java
@Test
void shouldSkipAnalysisWhenCooldownActive() {
    // Given
    BehaviorInsights existing = BehaviorInsights.builder()
        .userId(userId)
        .analyzedAt(LocalDateTime.now().minusHours(6))
        .build();
    
    // When
    BehaviorInsights result = service.analyzeUser(userId);
    
    // Then
    assertThat(result).isEqualTo(existing);
    verify(aiCoreService, never()).generateContent(any());
}
```

### 10.2 Integration Tests

```java
@SpringBootTest
@TestPropertySource(properties = "ai.behavior.mode=FULL")
class BehaviorFullModeIntegrationTest {
    
    @Test
    void shouldCreateSearchableEntityInFullMode() {
        // Given: Events for a user
        List<ExternalEvent> events = createTestEvents();
        
        // When: Analysis is triggered
        BehaviorInsights insight = service.analyzeUser(userId);
        
        // Then: AISearchableEntity should be created
        AISearchableEntity searchable = searchableRepo
            .findByEntityTypeAndEntityId("behavior-insight", userId.toString())
            .orElseThrow();
        
        assertThat(searchable.getSearchableContent())
            .contains(insight.getSegment());
    }
}
```

---

## 11. Example User Implementation

### 11.1 Custom Event Provider

```java
@Component
public class MyAppEventProvider implements ExternalEventProvider {
    
    @Autowired
    private UserActivityRepository activityRepo;
    
    @Override
    public List<ExternalEvent> getEventsForUser(UUID userId) {
        return activityRepo.findByUserIdAndProcessedFalse(userId)
            .stream()
            .map(activity -> ExternalEvent.builder()
                .eventType(activity.getType())
                .data(activity.getData())
                .occurredAt(activity.getTimestamp())
                .source(activity.getSource())
                .build())
            .toList();
    }
    
    @Override
    public UserEventBatch getNextUserEvents() {
        // Find user with most unprocessed events
        UUID nextUser = activityRepo.findUserWithMostUnprocessedEvents();
        if (nextUser == null) return null;
        
        return UserEventBatch.builder()
            .userId(nextUser)
            .events(getEventsForUser(nextUser))
            .build();
    }
}
```

### 11.2 Custom Insight Store (Optional)

```java
@Component
public class CRMBehaviorStore implements BehaviorInsightStore {
    
    @Autowired
    private CRMIntegrationService crmService;
    
    @Override
    public void storeInsight(BehaviorInsights insight) {
        // Send to CRM when segment changes
        crmService.updateCustomerSegment(
            insight.getUserId(), 
            insight.getSegment()
        );
    }
    
    @Override
    public BehaviorInsights getInsightForUser(UUID userId) {
        // Still read from local DB
        return repository.findByUserId(userId).orElse(null);
    }
}
```

---

**End of Technical Implementation Plan**

