# AI Behavior Module v2 - Technical Specification & Implementation Plan

**Version:** 2.3  
**Focus:** App-Side Behavioral Evolution & Framework Integration

---

## 1. Core Interfaces (SPIs)

These interfaces must be implemented by the application developer to "plug" their data sources and custom sinks into the module.

### 1.1 `ExternalEventProvider`
The bridge to the application's behavioral data.
```java
public interface ExternalEventProvider {
    /**
     * CASE 1: Fetch new interactions for a specific user since a specific time.
     */
    List<ExternalEvent> getEventsForUser(UUID userId, LocalDateTime since);

    /**
     * CASE 2: Discovery Mode. Provide the next batch of events for a user
     * needing analysis.
     */
    UserEventBatch getNextUserEvents();
}
```

### 1.2 `BehaviorInsightStore`
The "Sink" for behavioral intelligence.
```java
public interface BehaviorInsightStore {
    /**
     * Store or update an insight. Defaults to JPA if no bean provided.
     */
    void storeInsight(BehaviorInsights insight);

    /**
     * Retrieve the current state for evolution analysis.
     */
    Optional<BehaviorInsights> findByUserId(UUID userId);
}
```

---

## 2. Core Service Logic: The "Evolution" Weaver

The `BehaviorAnalysisService` is responsible for the stateful transformation of raw events into evolved insights.

### 2.1 Analysis Flow (Pseudocode)
```java
@Service
public class BehaviorAnalysisService {

    @AIProcess(type = "behavior-insight", processType = "create") // Triggers Framework Indexing
    public BehaviorInsights analyzeAndEvolve(UUID userId) {
        // 1. Fetch Previous State
        Optional<BehaviorInsights> previous = insightStore.findByUserId(userId);
        LocalDateTime since = previous.map(BehaviorInsights::getAnalyzedAt)
                                     .orElse(LocalDateTime.MIN);

        // 2. Pull New Raw Data from App
        List<ExternalEvent> newEvents = eventProvider.getEventsForUser(userId, since);
        
        if (newEvents.isEmpty()) return previous.orElse(null);

        // 3. Perform Evolutionary Analysis (LLM)
        BehaviorInsights updatedInsight = llmAnalyzer.evolve(previous, newEvents);

        // 4. Persistence & Sink
        insightStore.storeInsight(updatedInsight);

        return updatedInsight;
    }
}
```

---

## 3. Data Models

### 3.1 `BehaviorInsights` (The Entity)
```java
@Entity
@AICapable(entityType = "behavior-insight")
public class BehaviorInsights {
    @Id private UUID id;
    private UUID userId;
    
    // AI-Generated Metadata
    private String segment;          // e.g., "Power User", "At Risk"
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> patterns;   // e.g., ["mobile_shopper", "price_sensitive"]
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> recommendations;
    
    private LocalDateTime analyzedAt;
    private Double confidence;       // 0.0 - 1.0
}
```

---

## 4. Configuration & Mode Toggling

### 4.1 `BehaviorAIAutoConfiguration`
Automatically registers module presets into the framework's `AIEntityConfigurationLoader`.

```java
@Configuration
public class BehaviorAIAutoConfiguration {
    @Value("${ai.behavior.mode:LIGHT}") 
    private String mode; // LIGHT (Context) vs FULL (Discovery)

    @PostConstruct
    public void init() {
        if (!loader.hasEntityConfig("behavior-insight")) {
            loader.loadConfigurationFromFile("behavior-ai-" + mode.toLowerCase() + ".yml");
        }
    }
}
```

---

## 5. Framework Integration: Discovery & Search

In **FULL mode**, the framework's `@AIProcess` interceptor handles the "heavy lifting" of making behavioral intelligence searchable.

1.  **Automatic Vectorization**: The framework takes the `patterns` and `recommendations` fields and generates vector embeddings.
2.  **AISearchableEntity Creation**: A record is created in the global AI index with the behavioral data as metadata.
3.  **Relationship Query Support**: The `RelationshipQueryPlanner` can now execute queries like:
    - *"Find similar users to 'user-123' based on patterns"*
    - *"How many 'At Risk' users are in the 'Electronics' category?"* (Cross-entity join).

---

## 6. Implementation Roadmap (Technical Steps)

### Phase 1: Infrastructure
- [ ] Define `ExternalEventProvider` and `BehaviorInsightStore` interfaces.
- [ ] Implement `DefaultJpaBehaviorInsightStore` as the default sink.
- [ ] Create `BehaviorInsights` JPA entity with `@AICapable`.

### Phase 2: The Evolve Processor
- [ ] Create `BehaviorAnalysisService` with `analyzeAndEvolve` logic.
- [ ] Implement LLM Prompt Template for "Stateful Evolution" (Previous State + New Actions).
- [ ] Integrate `@AIProcess` annotation on the service level.

### Phase 3: Addon Configuration
- [ ] Create internal presets `behavior-ai-light.yml` and `behavior-ai-full.yml`.
- [ ] Implement `BehaviorAIAutoConfiguration` to load presets based on `application.yml`.

### Phase 4: Discovery Worker
- [ ] Implement `BehaviorAnalysisWorker` to trigger `analyzeAndEvolve` via the `getNextUserEvents()` pattern for batch discovery.

### Phase 5: Search Integration
- [ ] Register `BehaviorInsights` in the `RelationshipSchemaProvider` to enable hybrid AI/SQL queries.

