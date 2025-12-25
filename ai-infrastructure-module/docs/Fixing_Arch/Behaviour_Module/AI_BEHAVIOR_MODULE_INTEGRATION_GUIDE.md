# AI Behavior Module v2 - Integration Guide

**Version:** 2.3  
**Audience:** Developers integrating the Behavior Module into their application  
**Prerequisites:** Spring Boot 3.x, Java 21+

---

## 📋 Table of Contents

1. [Overview](#overview)
2. [Integration Flow](#integration-flow)
3. [Step-by-Step Integration](#step-by-step-integration)
4. [Database-Specific Examples](#database-specific-examples)
5. [Testing Your Integration](#testing-your-integration)
6. [Troubleshooting](#troubleshooting)

---

## 1. Overview

The AI Behavior Module is a **plug-and-play** addon that requires:
- A database (any JPA-supported database)
- Your own event data source
- Implementation of 1-2 simple interfaces

**What the module does NOT require:**
- Specific migration tool (Flyway, Liquibase, etc.)
- Specific database (PostgreSQL, MySQL, etc.)
- Changes to your existing schema

---

## 2. Integration Flow

```
┌─────────────────────────────────────────────────────────────┐
│ USER'S APPLICATION (e.g., MySQL + Liquibase)               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ Step 1: Add Maven Dependency                               │
│    └─ <dependency>ai-infrastructure-behavior</dependency>  │
│                                                             │
│ Step 2: Create Schema Using YOUR Tool                      │
│    └─ Liquibase, Flyway, or manual SQL                    │
│                                                             │
│ Step 3: Configure Module in application.yml                │
│    └─ ai.behavior.enabled: true                           │
│    └─ ai.behavior.mode: LIGHT or FULL                     │
│                                                             │
│ Step 4: Implement ExternalEventProvider                    │
│    └─ Tell module where to find your events               │
│                                                             │
│ Step 5: (Optional) Implement BehaviorInsightStore          │
│    └─ Customize where insights are saved                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│ BEHAVIOR MODULE AUTO-CONFIGURATION                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ ✅ Detects your database                                   │
│ ✅ Validates schema matches JPA entity                     │
│ ✅ Loads LIGHT or FULL preset configuration                │
│ ✅ Discovers your ExternalEventProvider bean               │
│ ✅ Discovers custom storage (if provided)                  │
│ ✅ Wires everything together automatically                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│ MODULE READY - YOU CAN NOW:                                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ • Call behaviorService.analyzeUser(userId)                 │
│ • Query insights for personalization                       │
│ • Run cross-user analytics (if FULL mode)                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Step-by-Step Integration

### Step 1: Add Dependency

Add the Behavior Module to your `pom.xml`:

```xml
<dependencies>
    <!-- Your existing dependencies (MySQL, Liquibase, etc.) -->
    
    <!-- NEW: AI Behavior Module -->
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-behavior</artifactId>
        <version>2.0.0</version>
    </dependency>
</dependencies>
```

For Gradle:
```groovy
dependencies {
    implementation 'com.ai.infrastructure:ai-infrastructure-behavior:2.0.0'
}
```

---

### Step 2: Create Database Schema

The module requires one table: `ai_behavior_insights`. You create it using **your preferred tool**.

#### Option A: Using Liquibase (Example)

**File:** `src/main/resources/db/changelog/changes/v1.0-behavior-module.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: behavior-module-table
      author: yourname
      changes:
        - createTable:
            tableName: ai_behavior_insights
            columns:
              - column:
                  name: id
                  type: ${uuid_type}  # Defined in liquibase.properties
                  constraints:
                    primaryKey: true
              - column:
                  name: user_id
                  type: ${uuid_type}
                  constraints:
                    nullable: false
                    unique: true
              - column:
                  name: segment
                  type: VARCHAR(100)
              - column:
                  name: patterns
                  type: ${json_type}  # JSON or JSONB depending on DB
              - column:
                  name: recommendations
                  type: ${json_type}
              - column:
                  name: insights
                  type: ${json_type}
              - column:
                  name: analyzed_at
                  type: TIMESTAMP
                  constraints:
                    nullable: false
              - column:
                  name: confidence
                  type: DOUBLE
              - column:
                  name: ai_model_used
                  type: VARCHAR(50)
              - column:
                  name: created_at
                  type: TIMESTAMP
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: TIMESTAMP
                  constraints:
                    nullable: false
        
        - createIndex:
            indexName: idx_behavior_insights_user_id
            tableName: ai_behavior_insights
            columns:
              - column:
                  name: user_id
        
        - createIndex:
            indexName: idx_behavior_insights_analyzed_at
            tableName: ai_behavior_insights
            columns:
              - column:
                  name: analyzed_at
```

**Database-specific property files:**

`liquibase-postgresql.properties`:
```properties
uuid_type=UUID
json_type=JSONB
```

`liquibase-mysql.properties`:
```properties
uuid_type=CHAR(36)
json_type=JSON
```

#### Option B: Using Flyway (Example)

**File:** `src/main/resources/db/migration/V1__behavior_module.sql`

```sql
-- For PostgreSQL
CREATE TABLE ai_behavior_insights (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    segment VARCHAR(100),
    patterns JSONB,
    recommendations JSONB,
    insights JSONB,
    analyzed_at TIMESTAMP NOT NULL,
    confidence DOUBLE PRECISION,
    ai_model_used VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_behavior_insights_user_id ON ai_behavior_insights(user_id);
CREATE INDEX idx_behavior_insights_analyzed_at ON ai_behavior_insights(analyzed_at DESC);
```

For MySQL, see [Database-Specific Examples](#4-database-specific-examples).

#### Option C: Hibernate Auto-DDL (Development Only)

For **development/testing** environments only:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update  # Auto-creates table from JPA entity
```

**⚠️ WARNING:** Never use `ddl-auto: update` in production!

---

### Step 3: Configure the Module

Add to your `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/myapp
    username: myuser
    password: mypass
  
  jpa:
    hibernate:
      ddl-auto: validate  # Let your migration tool manage schema

# Enable Behavior Module
ai:
  behavior:
    enabled: true
    mode: LIGHT  # or FULL (see modes below)
    
    # Optional: Advanced settings
    analysis:
      default-window-days: 30         # For new users with no history
      max-events-per-analysis: 1000   # Safety cap
```

#### Mode Selection

| Mode | Use Case | Indexing | Discovery |
| :--- | :--- | :--- | :--- |
| **LIGHT** | Personalization only | ❌ No | ❌ No |
| **FULL** | Personalization + Statistics | ✅ Yes | ✅ Yes |

**LIGHT Mode:** Insights stored in database only. Fast, minimal overhead.  
**FULL Mode:** Insights automatically indexed in `AISearchableEntity` for cross-user analytics.

---

### Step 4: Implement ExternalEventProvider (Required)

The module needs to know **where your events come from**. You implement this interface:

```java
package com.myapp.behavior;

import com.ai.infrastructure.behavior.spi.ExternalEventProvider;
import com.ai.infrastructure.behavior.model.ExternalEvent;
import com.ai.infrastructure.behavior.model.UserEventBatch;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class MyAppEventProvider implements ExternalEventProvider {
    
    @Autowired
    private MyEventRepository eventRepository;  // Your existing event store
    
    @Value("${ai.behavior.analysis.default-window-days:30}")
    private int defaultWindowDays;
    
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
        
        // Fetch from YOUR event tables
        return eventRepository.findByUserAndDateRange(userId, effectiveSince, effectiveUntil)
            .stream()
            .map(this::toExternalEvent)
            .collect(Collectors.toList());
    }
    
    @Override
    public UserEventBatch getNextUserEvents() {
        // Strategy: Find user with most unprocessed events (your logic)
        UUID nextUser = eventRepository.findUserWithOldestAnalysis();
        if (nextUser == null) return null;
        
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<ExternalEvent> events = getEventsForUser(nextUser, since, null);
        
        // OPTIONAL: Include user context for richer LLM analysis
        Map<String, Object> userContext = buildUserContext(nextUser);
        
        return UserEventBatch.builder()
            .userId(nextUser)
            .events(events)
            .totalEventCount(events.size())
            .userContext(userContext)  // ← Enriches LLM understanding
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
                "accountAge", user.getAccountAge(),
                "location", user.getCity(),
                "totalSpent", user.getTotalOrderValue()
                // Include any metadata that helps understand behavior
            ))
            .orElse(Map.of());
    }
    
    private ExternalEvent toExternalEvent(MyEventEntity entity) {
        return ExternalEvent.builder()
            .eventType(entity.getAction())      // "purchase", "click", etc.
            .eventData(entity.getMetadata())     // Flexible Map<String, Object>
            .timestamp(entity.getOccurredAt())
            .source(entity.getPlatform())        // "web", "mobile"
            .build();
    }
}
```

**That's it!** The module will auto-detect this bean and use it.

---

### Step 5: (Optional) Implement Custom Storage

By default, insights are saved to your database via JPA. If you want to send them elsewhere (CRM, data lake, etc.):

```java
package com.myapp.behavior;

import com.ai.infrastructure.behavior.spi.BehaviorInsightStore;
import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class SalesforceBehaviorStore implements BehaviorInsightStore {
    
    @Autowired
    private SalesforceClient salesforceClient;
    
    @Override
    public void save(BehaviorInsights insight) {
        // Sync to Salesforce
        salesforceClient.updateContact(
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
        // Fetch from Salesforce (or return empty to always do fresh analysis)
        return salesforceClient.getContactBehavior(userId.toString())
            .map(this::toBehaviorInsights);
    }
    
    @Override
    public void deleteByUserId(UUID userId) {
        // GDPR compliance
        salesforceClient.deleteBehavioralData(userId.toString());
    }
}
```

If this bean is present, **all storage operations** route to it instead of the database.

---

### Step 6: Use the Module

#### A. Trigger Analysis for a User

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private BehaviorAnalysisService behaviorService;
    
    @PostMapping("/{userId}/analyze")
    public ResponseEntity<BehaviorInsights> analyzeUser(@PathVariable UUID userId) {
        BehaviorInsights insights = behaviorService.analyzeUser(userId);
        return ResponseEntity.ok(insights);
    }
}
```

**What happens:**
1. Module calls `yourEventProvider.getEventsForUser(userId, since, until)`
2. LLM analyzes the events
3. Insight is saved (via your custom store OR database)
4. If FULL mode: Automatically indexed for discovery

#### B. Fetch Insights for Personalization

```java
@Service
public class PersonalizationService {
    
    @Autowired
    private BehaviorInsightsRepository insightsRepository;
    
    public void personalizeHomepage(UUID userId, Model model) {
        Optional<BehaviorInsights> insights = insightsRepository.findByUserId(userId);
        
        if (insights.isPresent()) {
            String segment = insights.get().getSegment();
            List<String> recommendations = insights.get().getRecommendations();
            
            // Use for personalization
            model.addAttribute("segment", segment);
            model.addAttribute("suggestions", recommendations);
        }
    }
}
```

#### C. Cross-User Analytics (FULL Mode Only)

```java
@Service
public class AnalyticsService {
    
    @Autowired
    private RelationshipQueryService queryService;
    
    @Autowired
    private UserRepository userRepository;
    
    public List<User> findChurnRiskUsers() {
        // Query behavioral insights only
        RAGResponse response = queryService.execute(
            "Find users in the Power User segment with churn risk > 0.7",
            List.of("behavior-insight")  // NO user table join needed
        );
        
        // Extract user IDs from behavioral results
        List<UUID> userIds = response.getDocuments().stream()
            .map(doc -> UUID.fromString(doc.getId()))
            .collect(Collectors.toList());
        
        // Fetch full user details separately if needed
        return userRepository.findAllById(userIds);
    }
}
```

**Key Point:** The module queries `behavior-insight` entities only. No joins with User tables. You control how to fetch additional user data.

---

## 4. Database-Specific Examples

### PostgreSQL

```sql
CREATE TABLE ai_behavior_insights (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    segment VARCHAR(100),
    patterns JSONB,
    recommendations JSONB,
    insights JSONB,
    analyzed_at TIMESTAMP NOT NULL,
    confidence DOUBLE PRECISION,
    ai_model_used VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_behavior_insights_user_id ON ai_behavior_insights(user_id);
CREATE INDEX idx_behavior_insights_analyzed_at ON ai_behavior_insights(analyzed_at DESC);
CREATE INDEX idx_behavior_insights_segment ON ai_behavior_insights(segment);
```

### MySQL

```sql
CREATE TABLE ai_behavior_insights (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL UNIQUE,
    segment VARCHAR(100),
    patterns JSON,
    recommendations JSON,
    insights JSON,
    analyzed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confidence DOUBLE,
    ai_model_used VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_behavior_insights_user_id ON ai_behavior_insights(user_id);
CREATE INDEX idx_behavior_insights_analyzed_at ON ai_behavior_insights(analyzed_at DESC);
CREATE INDEX idx_behavior_insights_segment ON ai_behavior_insights(segment);
```

### H2 (Testing)

```sql
CREATE TABLE ai_behavior_insights (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    segment VARCHAR(100),
    patterns VARCHAR(5000),  -- H2 doesn't have native JSON
    recommendations VARCHAR(5000),
    insights VARCHAR(5000),
    analyzed_at TIMESTAMP NOT NULL,
    confidence DOUBLE,
    ai_model_used VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_behavior_insights_user_id ON ai_behavior_insights(user_id);
CREATE INDEX idx_behavior_insights_analyzed_at ON ai_behavior_insights(analyzed_at DESC);
CREATE INDEX idx_behavior_insights_segment ON ai_behavior_insights(segment);
```

### Oracle

```sql
CREATE TABLE ai_behavior_insights (
    id RAW(16) PRIMARY KEY,
    user_id RAW(16) NOT NULL UNIQUE,
    segment VARCHAR2(100),
    patterns CLOB,
    recommendations CLOB,
    insights CLOB,
    analyzed_at TIMESTAMP NOT NULL,
    confidence BINARY_DOUBLE,
    ai_model_used VARCHAR2(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_behavior_insights_user_id ON ai_behavior_insights(user_id);
CREATE INDEX idx_behavior_insights_analyzed_at ON ai_behavior_insights(analyzed_at DESC);
CREATE INDEX idx_behavior_insights_segment ON ai_behavior_insights(segment);
```

---

## 5. Testing Your Integration

### Test 1: Schema Validation

Start your application. If schema is correct, you'll see:

```
✅ BehaviorAIAutoConfiguration: Behavior AI Addon initialized in LIGHT mode
✅ Hibernate: Schema validation passed
```

If schema is missing or incorrect:
```
❌ Schema-validation: missing table [ai_behavior_insights]
```

### Test 2: SPI Detection

Check logs for:
```
✅ Found ExternalEventProvider: MyAppEventProvider
✅ Found BehaviorInsightStore: SalesforceBehaviorStore (if implemented)
```

### Test 3: Analyze a User

```java
@SpringBootTest
class BehaviorModuleIntegrationTest {
    
    @Autowired
    private BehaviorAnalysisService behaviorService;
    
    @Test
    void testUserAnalysis() {
        UUID testUser = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        
        BehaviorInsights result = behaviorService.analyzeUser(testUser);
        
        assertNotNull(result);
        assertNotNull(result.getSegment());
        assertTrue(result.getConfidence() >= 0.0 && result.getConfidence() <= 1.0);
    }
}
```

---

## 6. Troubleshooting

### Problem: "No bean of type ExternalEventProvider found"

**Solution:** You must implement `ExternalEventProvider` as a Spring `@Component`.

```java
@Component  // ← Don't forget this!
public class MyEventProvider implements ExternalEventProvider { ... }
```

---

### Problem: "Schema validation failed: missing table"

**Solution:** Run your migration tool:
```bash
# Flyway
./mvnw flyway:migrate

# Liquibase
./mvnw liquibase:update
```

Or check that Hibernate auto-DDL is enabled (dev only):
```yaml
spring.jpa.hibernate.ddl-auto: update
```

---

### Problem: "UnsupportedOperationException: JSON not supported"

**Solution:** Your database doesn't support native JSON. Use `VARCHAR` and implement custom converters:

```java
@Converter
public class JsonListConverter implements AttributeConverter<List<String>, String> {
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        try {
            return mapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error converting to JSON", e);
        }
    }
    
    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        try {
            return mapper.readValue(dbData, new TypeReference<List<String>>() {});
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}
```

---

### Problem: Module runs but no analysis happens

**Check:**
1. Is `ai.behavior.enabled: true` in config?
2. Does `ExternalEventProvider.getEventsForUser()` return events?
3. Check logs for LLM errors

---

## 📚 Next Steps

- **Production Deployment:** Switch to `ddl-auto: validate` and use migration tools
- **Monitoring:** Add metrics for analysis latency and success rate
- **GDPR Compliance:** Implement `deleteByUserId()` in your custom store
- **Performance Tuning:** Adjust `default-window-days` and `max-events-per-analysis`

---

**End of Integration Guide**

