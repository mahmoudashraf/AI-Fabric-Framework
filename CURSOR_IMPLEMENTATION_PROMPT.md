# 🚀 Cursor IDE Implementation Prompt: AI Behavior Analytics Module

**Copy this entire prompt into Cursor and run it sequentially.**

---

## CONTEXT & OVERVIEW

You are implementing a new **AI Behavior Analytics Microservice** that:
- Ingests user behavior events (single or batch)
- Asynchronously analyzes them using AI Core
- Generates insights with patterns, recommendations, and user segments
- Provides an AI-orchestrated search endpoint with PII detection
- Automatically indexes results via AI Core's `@AICapable` and `@AIProcess` annotations

**Key Principle:** The behavior module is DOMAIN-AGNOSTIC via policy hooks. Customers implement domain logic through `BehaviorAnalysisPolicy` interface.

---

## REFERENCE DOCUMENTS

Before implementing, read these in order:

1. **AI_BEHAVIOR_ANALYTICS_MICROSERVICE_ARCHITECTURE.md** 
   - Technical architecture overview
   - Complete system design
   - API specifications

2. **AI_BEHAVIOR_ANALYTICS_IMPLEMENTATION_SEQUENCE.md** 
   - Step-by-step implementation guide (THIS IS YOUR ROADMAP)
   - Exact file paths and code examples
   - Phase-by-phase breakdown
   - Success criteria for each phase

3. **AI_BEHAVIOR_ANALYTICS_MODULE_V2_PHILOSOPHY.md** 
   - Original requirements and thinking
   - Use for context and reference

4. **COUPLING_QUICK_ANSWER.txt** 
   - Hook-based decoupling patterns
   - Why configuration + hooks prevent coupling

---

## IMPLEMENTATION PHASES (Follow Sequentially)

### ✅ PHASE 1: FOUNDATION (Week 1)

**Goal:** Set up module structure, Maven configuration, and configuration classes.

**Tasks:**
1. Create directory structure: `ai-infrastructure-behavior/` with `src/main/java/com/ai/behavior/` subdirectories
2. Update `ai-infrastructure-module/ai-infrastructure-behavior/pom.xml` with all required dependencies
3. Create `BehaviorModuleConfiguration.java` - Base configuration with `@EnableScheduling`, `@EnableCaching`, `@EnableAsync`
4. Create `BehaviorModuleProperties.java` - Configuration properties class using `@ConfigurationProperties(prefix = "ai.behavior")`
5. Create `application.yml` with all configuration sections

**Success Criteria:**
- All dependencies resolve without conflicts
- Tests compile with Java 21
- Configuration class loads without errors

**Reference:**
- See `AI_BEHAVIOR_ANALYTICS_IMPLEMENTATION_SEQUENCE.md` → "Phase 1: Foundation" for exact code

---

### ✅ PHASE 2: EVENT PIPELINE (Weeks 1-2)

**Goal:** Build the event ingestion and temporary storage layer.

**Tasks:**

#### Task 2.1: Database Schema
1. Create Liquibase migration file: `src/main/resources/db/changelog/V001__create_behavior_tables.sql`
2. Create two tables:
   - `ai_behavior_events_temp` (with TTL, indexes on user_id + created_at, expires_at)
   - `ai_behavior_events_failed` (for failed retry tracking)
3. Add indexes for query optimization

#### Task 2.2: Entity Models
1. Create `BehaviorEventEntity.java` - JPA entity for temporary events
   - Fields: id (UUID), userId, eventType, eventData (JSONB), source, created_at, processed, processingStatus, retryCount, expiresAt
   - Include @PrePersist lifecycle hook
2. Update existing `BehaviorInsights.java` entity - ADD TWO CRITICAL ANNOTATIONS:
   - `@AICapable` - Tell AI Core this entity is searchable
   - `@AIProcess(strategy = AIProcessStrategy.ASYNC, type = "behavior-insight")` - Mark method for AI Core auto-indexing
   - Add method `notifyInsightsReady()` that AI Core calls automatically

#### Task 2.3: Repositories
1. Create `BehaviorEventRepository.java` - Spring Data JPA repository
   - Include custom queries: findUnprocessedEvents(), findExpiredEvents()
2. Create `BehaviorInsightsRepository.java` - Spring Data JPA repository
   - Include queries by userId, segment, retention date

#### Task 2.4: Ingestion Service
1. Create `BehaviorEventIngestionService.java`
   - Implement `ingestSingleEvent()` - stores one event with TTL
   - Implement `ingestBatchEvents()` - stores multiple events with TTL
   - Implement `markProcessed()` - mark event as completed
   - Implement `getUnprocessedEvents()` - fetch for worker

**Success Criteria:**
- Database migrations run without errors
- Can create/retrieve BehaviorEventEntity
- TTL calculation works correctly
- Integration test: Single and batch ingestion pass

**Reference:**
- See `AI_BEHAVIOR_ANALYTICS_IMPLEMENTATION_SEQUENCE.md` → "Phase 2: Event Pipeline"

---

### ✅ PHASE 3: AI ANALYSIS (Weeks 2-3)

**Goal:** Implement analysis logic using policy hooks (domain-agnostic).

**Tasks:**

#### Task 3.1: Create Policy Hook Interface
1. Create `BehaviorAnalysisPolicy.java` interface
   - Methods: detectPatterns(), determineSegment(), generateRecommendations(), calculateConfidence()
   - Each method receives: userId, events, scores (or equivalent)
   - CRITICAL: This allows customers to inject their domain logic

#### Task 3.2: Default Implementation
1. Create `DefaultBehaviorAnalysisPolicy.java` - Default implementation
   - Use `@ConditionalOnMissingBean` - only loaded if customer doesn't provide one
   - Implement all hook methods with sensible defaults
   - Use configurable thresholds from `BehaviorModuleProperties` (NOT hardcoded!)
   - Example: `properties.getProcessing().getAnalyzer().getEngagementThreshold()`

#### Task 3.3: Analysis Service
1. Create `BehaviorAnalyzerService.java`
   - Inject `BehaviorAnalysisPolicy` - this is the hook!
   - Implement `analyzeUserBehavior()` method:
     - Compute scores from events (engagement, recency, etc.)
     - Call policy.detectPatterns() ← USES HOOK
     - Call policy.determineSegment() ← USES HOOK
     - Call policy.generateRecommendations() ← USES HOOK
     - Call policy.calculateConfidence() ← USES HOOK
     - Build BehaviorInsights entity
     - Save to repository (AI Core auto-indexes via @AIProcess!)

#### Task 3.4: Background Worker
1. Create `BehaviorAnalysisWorker.java` - Scheduled component
   - Method `processUnprocessedEvents()` with `@Scheduled` annotation
   - Configurable delay: `${ai.behavior.processing.worker.delaySeconds}`
   - Groups events by user, calls analyzerService
   - Handles retries with exponential backoff
   - Method `cleanupExpiredEvents()` - scheduled cleanup at 3 AM
   - Uses `@Scheduled(cron = "${ai.behavior.retention.cleanupSchedule}")`

**Success Criteria:**
- Policy interface is clean and reusable
- Default implementation uses configurable values (no hardcoding!)
- Analysis service delegates ALL business logic to policy hook
- Worker processes events successfully
- Integration test: Worker picks up events, creates insights

**Reference:**
- See `AI_BEHAVIOR_ANALYTICS_IMPLEMENTATION_SEQUENCE.md` → "Phase 3: AI Analysis"
- See `COUPLING_QUICK_ANSWER.txt` → "Solution 2: Hook-Based Architecture"

---

### ✅ PHASE 4: SEARCH & ORCHESTRATION (Weeks 3-4)

**Goal:** Implement AI-orchestrated search with PII detection and query transformation.

**Tasks:**

#### Task 4.1: Search Service
1. Create `BehaviorSearchService.java`
   - Implement `search()` method accepting `SearchParameters`
   - Support filtering by: segment, pattern, confidence
   - Returns List<BehaviorInsights>

#### Task 4.2: DTOs for Orchestrated Query
1. Create `OrchestratedQueryRequest.java`
   - Fields: query (String), limit (int), includeExplanation (boolean), userId (String)
2. Create `OrchestratedSearchResponse.java`
   - Fields: query, executedAt, piiDetected, error, results (SearchResults)
   - Nested class SearchResults: matchedUsers, totalMatches, searchStrategy, aiExplanation

#### Task 4.3: Query Orchestrator Service
1. Create `BehaviorQueryOrchestrator.java` - CRITICAL: Integrates with AI Core
   - Inject: `RAGOrchestrator` (from AI Core), `PIIDetectionService` (from AI Core)
   - Implement `executeQuery()` with 6 steps:
     1. PII Detection: `piiDetection.detect(query)`
        - If PII found: return error response
     2. Query Orchestration: `ragOrchestrator.orchestrate(RAGOrchestrationContext)`
        - Gets transformed query + search strategy
     3. Transform to SearchParameters: Parse AI-transformed query into structured filters
     4. Execute Search: Call searchService.search()
     5. Generate Explanation: Call `ragOrchestrator.generateExplanation()` (or simple string for MVP)
     6. Return OrchestratedSearchResponse with all details

**Key:** This is where Behavior Module delegates to AI Core for orchestration!

#### Task 4.4: REST Controllers
1. Create `BehaviorEventController.java`
   - `POST /api/behavior/events` - Ingest single event
     - Returns `202 Accepted` with event ID
   - `POST /api/behavior/events/batch` - Ingest batch
     - Returns `202 Accepted` with count

2. Create `BehaviorSearchController.java`
   - `POST /api/search/orchestrated` - AI-orchestrated search
     - Calls `queryOrchestrator.executeQuery()`
     - Returns full response with results + AI explanation

**Success Criteria:**
- Single event ingestion returns 202 Accepted
- Batch ingestion returns 202 Accepted
- Orchestrated search endpoint works with PII detection
- Search returns ranked results with AI explanation
- Integration test: End-to-end query flow

**Reference:**
- See `AI_BEHAVIOR_ANALYTICS_IMPLEMENTATION_SEQUENCE.md` → "Phase 4: Search & Orchestration"
- See `AI_BEHAVIOR_ANALYTICS_MICROSERVICE_ARCHITECTURE.md` → "🤖 NEW: AI-ORCHESTRATED QUERY ENDPOINT"

---

### ✅ PHASE 5: INTEGRATION TESTING (Weeks 4-5)

**Goal:** Test all components with real database (Testcontainers).

**Tasks:**

#### Task 5.1: Test Base Class
1. Create `BehaviorAnalyticsIntegrationTest.java`
   - Use `@SpringBootTest`, `@Testcontainers`, `@AutoConfigureMockMvc`
   - Configure PostgreSQL container with Testcontainers
   - Inject `MockMvc` for HTTP testing

#### Task 5.2: Event Ingestion Tests
1. Create `EventIngestionIntegrationTest.java`
   - Test: Single event ingestion - verify saved with TTL
   - Test: Batch event ingestion - verify all saved
   - Test: Mark as processed - verify status updated

#### Task 5.3: Analysis Tests
1. Create `BehaviorAnalysisIntegrationTest.java`
   - Test: Analyze user behavior - verify insights created
   - Test: Policy hook called - verify correct patterns/recommendations
   - Test: Custom policy implementation - verify domain logic used

#### Task 5.4: Search Tests
1. Create `BehaviorSearchIntegrationTest.java`
   - Test: Simple search by segment - verify results
   - Test: PII detection - verify error response for sensitive queries
   - Test: Orchestrated query - verify end-to-end flow
   - Test: Empty results - verify graceful response

**Success Criteria:**
- All tests pass with Java 21
- 80%+ code coverage
- Tests use real database (Testcontainers)
- Integration tests verify full workflows

**Reference:**
- See `AI_BEHAVIOR_ANALYTICS_IMPLEMENTATION_SEQUENCE.md` → "Phase 5: Integration Testing"

---

### ✅ PHASE 6: PRODUCTION HARDENING (Weeks 5-6)

**Goal:** Add security, monitoring, and deployment readiness.

**Tasks:**

#### Task 6.1: Security
1. Create `RateLimitingConfiguration.java`
   - Implement per-user rate limiting (100 requests/hour)
   - Use bucket4j library
   - Add `@RateLimitCheck` interceptor to endpoints

2. Create `BehaviorAuditService.java`
   - Log all event ingestions
   - Log all query executions
   - Log PII detections with timestamp + query
   - Use structured logging (JSON format recommended)

#### Task 6.2: Monitoring
1. Add Actuator endpoints to `application.yml`
   - Expose: health, metrics, info, prometheus
   - Enable liveness/readiness probes

2. Create `BehaviorMetricsService.java`
   - Track: event ingestion count, analysis latency, search latency, PII detection rate
   - Use Micrometer for metrics

#### Task 6.3: Configuration
1. Update `application.yml` with:
   - Database connection pooling (HikariCP)
   - Redis caching config (optional)
   - Rate limiting config
   - Actuator endpoints

**Success Criteria:**
- Rate limiting works (verify with load test)
- Audit logs are comprehensive
- Metrics available at `/actuator/metrics`
- Prometheus endpoint works

**Reference:**
- See `AI_BEHAVIOR_ANALYTICS_IMPLEMENTATION_SEQUENCE.md` → "Phase 6: Production Hardening"

---

## IMPLEMENTATION CHECKLIST

Copy this checklist and check off as you complete each task:

### Phase 1: Foundation
- [ ] Task 1.1.1: Create directory structure
- [ ] Task 1.1.2: Update pom.xml with dependencies
- [ ] Task 1.1.3: Create BehaviorModuleConfiguration
- [ ] Task 1.1.4: Create BehaviorModuleProperties

### Phase 2: Event Pipeline
- [ ] Task 2.1.1: Create Liquibase migrations
- [ ] Task 2.1.2: Configure Liquibase in application.yml
- [ ] Task 2.2.1: Create BehaviorEventEntity
- [ ] Task 2.2.2: Update BehaviorInsights with @AICapable + @AIProcess
- [ ] Task 2.3.1: Create BehaviorEventRepository
- [ ] Task 2.3.2: Create BehaviorInsightsRepository
- [ ] Task 2.4.1: Create BehaviorEventIngestionService

### Phase 3: AI Analysis
- [ ] Task 3.1.1: Create BehaviorAnalysisPolicy interface
- [ ] Task 3.1.2: Create DefaultBehaviorAnalysisPolicy
- [ ] Task 3.2.1: Create BehaviorAnalyzerService
- [ ] Task 3.3.1: Create BehaviorAnalysisWorker

### Phase 4: Search & Orchestration
- [ ] Task 4.1.1: Create BehaviorSearchService
- [ ] Task 4.2.1: Create OrchestratedQueryRequest/Response DTOs
- [ ] Task 4.2.2: Create BehaviorQueryOrchestrator
- [ ] Task 4.3.1: Create BehaviorSearchController
- [ ] Task 4.3.2: Create BehaviorEventController

### Phase 5: Integration Testing
- [ ] Task 5.1.1: Create BehaviorAnalyticsIntegrationTest base class
- [ ] Task 5.1.2: Create EventIngestionIntegrationTest
- [ ] Task 5.1.3: Create BehaviorAnalysisIntegrationTest
- [ ] Task 5.2.1: Create BehaviorSearchIntegrationTest

### Phase 6: Production Hardening
- [ ] Task 6.1.1: Create RateLimitingConfiguration
- [ ] Task 6.1.2: Create BehaviorAuditService
- [ ] Task 6.2.1: Create BehaviorMetricsService
- [ ] Task 6.2.2: Update application.yml with monitoring config

---

## KEY DESIGN PRINCIPLES

**MUST FOLLOW** to maintain architecture integrity:

### 1. **Policy Hooks (Domain Decoupling)**
   ```
   ❌ WRONG: Hardcode business logic
   if (totalPurchase > 5000) { segment = "vip"; }
   
   ✅ RIGHT: Inject via hook
   String segment = policy.determineSegment(userId, patterns, scores);
   ```
   - All business logic goes in `BehaviorAnalysisPolicy` implementation
   - Library code stays domain-agnostic
   - Customers implement their own `@Component` extending the interface

### 2. **Configuration-Driven Values**
   ```
   ❌ WRONG: Hardcoded thresholds
   double threshold = 0.75;
   
   ✅ RIGHT: From configuration
   double threshold = properties.getProcessing().getAnalyzer().getEngagementThreshold();
   ```
   - All numeric values must be configurable via YAML
   - Customers can adjust without code changes

### 3. **Automatic Indexing (AI Core)**
   ```
   ❌ WRONG: Manual indexing
   insights = analyzer.analyze();
   vectorStore.upsert(insights.toVector());  // Manual!
   elasticSearch.index(insights);  // Manual!
   
   ✅ RIGHT: Let AI Core handle it
   @AICapable  // ← Mark entity
   @Entity
   public class BehaviorInsights { ... }
   
   @AIProcess  // ← Mark method
   public void notifyInsightsReady() { }
   
   // In service:
   repository.save(insights);  // AI Core auto-indexes!
   ```

### 4. **Non-Blocking Event Ingestion**
   ```
   ✅ CORRECT: Always return 202 Accepted
   POST /api/behavior/events → 202 Accepted
   (Worker processes asynchronously)
   
   ❌ WRONG: Blocking ingestion
   POST /api/behavior/events → 200 OK (after analysis) ❌
   ```

### 5. **Delegation to AI Core**
   - **PII Detection:** Use AI Core's `PIIDetectionService`
   - **Query Orchestration:** Use AI Core's `RAGOrchestrator`
   - **Auto-Indexing:** Use AI Core's `@AIProcess` annotation
   - Never reimplement these features

---

## CRITICAL CODE PATTERNS

### Pattern 1: Injecting Policy Hook

```java
@Service
public class BehaviorAnalyzerService {
    
    @Autowired
    private BehaviorAnalysisPolicy policy;  // ← INJECTED HOOK
    
    public BehaviorInsights analyze(UUID userId, List<BehaviorEventEntity> events) {
        // Call hook (customer's domain logic)
        List<String> patterns = policy.detectPatterns(userId, events, scores);
        String segment = policy.determineSegment(userId, patterns, scores);
        
        // Framework stays clean!
    }
}
```

### Pattern 2: Marked for Automatic Indexing

```java
@Entity
@AICapable  // ← TELL AI CORE
public class BehaviorInsights {
    
    @AIProcess(strategy = AIProcessStrategy.ASYNC, type = "behavior-insight")
    public void notifyInsightsReady() {
        // AI Core calls this automatically!
    }
}

// In service:
BehaviorInsights insights = analyzer.analyze(...);
repository.save(insights);  // ← Triggers AI Core indexing!
```

### Pattern 3: AI Core Orchestration

```java
@Service
public class BehaviorQueryOrchestrator {
    
    @Autowired
    private RAGOrchestrator ragOrchestrator;  // ← FROM AI CORE
    
    @Autowired
    private PIIDetectionService piiDetection;  // ← FROM AI CORE
    
    public OrchestratedSearchResponse executeQuery(OrchestratedQueryRequest request) {
        // Step 1: PII Detection via AI Core
        PIIDetectionResult piiResult = piiDetection.detect(request.getQuery());
        
        // Step 2: Query Orchestration via AI Core
        RAGResponse ragResponse = ragOrchestrator.orchestrate(context);
        
        // Step 3-4: Transform & Search
        SearchParameters params = transformToSearchParams(ragResponse);
        List<BehaviorInsights> results = searchService.search(params);
        
        return response;
    }
}
```

### Pattern 4: Configurable Values

```java
@Component
@ConfigurationProperties(prefix = "ai.behavior")
public class BehaviorModuleProperties {
    
    private Processing processing = new Processing();
    
    @Getter @Setter
    public static class Processing {
        private Analyzer analyzer = new Analyzer();
        
        @Getter @Setter
        public static class Analyzer {
            private double engagementThreshold = 0.75;  // ← Default, configurable
        }
    }
}

// In service:
double threshold = properties.getProcessing()
    .getAnalyzer()
    .getEngagementThreshold();  // ← From config/YAML!
```

---

## DEPENDENCY INJECTION QUICK REFERENCE

All services should use constructor injection with `@RequiredArgsConstructor`:

```java
@Service
@RequiredArgsConstructor
public class BehaviorAnalyzerService {
    
    private final BehaviorAnalysisPolicy policy;
    private final BehaviorInsightsRepository repository;
    private final ObjectMapper objectMapper;
    
    // All injected via constructor!
}
```

---

## TESTING EXPECTATIONS

Each phase should have integration tests:

```
Phase 2 (Event Pipeline):
  ✅ Can ingest single event
  ✅ Can ingest batch events
  ✅ TTL calculation works

Phase 3 (AI Analysis):
  ✅ Policy hooks called correctly
  ✅ Analysis produces valid insights
  ✅ Worker processes events

Phase 4 (Search & Orchestration):
  ✅ PII detection works
  ✅ Search returns results
  ✅ Orchestrated query works end-to-end

Phase 5 (Testing):
  ✅ 80%+ code coverage
  ✅ All integration tests pass
  ✅ Real database (Testcontainers)
```

Use `@SpringBootTest`, `@Testcontainers`, `MockMvc` for all tests.

---

## COMMON PITFALLS TO AVOID

### ❌ Pitfall 1: Hardcoding Business Logic
```java
// WRONG
if (engagement > 0.75) segment = "vip";

// RIGHT
String segment = policy.determineSegment(...);
```

### ❌ Pitfall 2: Blocking Event Ingestion
```java
// WRONG
@PostMapping("/events")
public ResponseEntity<BehaviorEventEntity> ingest(...) {
    // Do analysis here (SLOW!)
    return ResponseEntity.ok(result);
}

// RIGHT
@PostMapping("/events")
public ResponseEntity<?> ingest(...) {
    // Just store, return 202
    return ResponseEntity.accepted().build();
}
```

### ❌ Pitfall 3: Manual Indexing
```java
// WRONG
insights = analyzer.analyze();
vectorStore.upsert(insights);  // Manual!
elasticSearch.index(insights);  // Manual!

// RIGHT
insights = analyzer.analyze();
repository.save(insights);  // AI Core auto-indexes via @AIProcess!
```

### ❌ Pitfall 4: Not Using Configuration
```java
// WRONG
double threshold = 0.75;  // Hardcoded!

// RIGHT
double threshold = properties.getProcessing().getAnalyzer().getEngagementThreshold();
```

### ❌ Pitfall 5: Reimplementing AI Core Features
```java
// WRONG
BehaviorQueryOrchestrator myOrch = new CustomOrchestrator();

// RIGHT
@Autowired
private RAGOrchestrator ragOrchestrator;  // Use AI Core's!
```

---

## DEBUGGING TIPS

If tests fail:

1. **Event not being ingested?**
   - Check: Liquibase migration ran
   - Check: BehaviorEventRepository can save
   - Check: @Transactional present on service method

2. **Analysis not producing insights?**
   - Check: Worker is running (verify @Scheduled)
   - Check: Policy hook is injected (check bean context)
   - Check: BehaviorInsightsRepository can save

3. **Search not working?**
   - Check: Insights exist in database
   - Check: AI Core RAGOrchestrator is accessible
   - Check: PIIDetectionService can detect

4. **AI Core integration failing?**
   - Check: AI Core module is dependency
   - Check: RAGOrchestrator bean exists
   - Check: @AICapable/@AIProcess annotations correct

---

## SUCCESS CRITERIA (END-TO-END)

**Phase 1:**
- ✅ Maven builds without errors
- ✅ All dependencies resolve
- ✅ Configuration loads

**Phase 2:**
- ✅ Can POST single event → 202 Accepted
- ✅ Can POST batch → 202 Accepted
- ✅ Events stored in database with TTL

**Phase 3:**
- ✅ Worker processes events automatically
- ✅ BehaviorInsights created with patterns + recommendations
- ✅ Custom policy implementation works

**Phase 4:**
- ✅ Can POST orchestrated query
- ✅ PII detection blocks sensitive queries
- ✅ Search returns ranked results with AI explanation

**Phase 5:**
- ✅ All integration tests pass
- ✅ 80%+ code coverage
- ✅ Real database (Testcontainers)

**Phase 6:**
- ✅ Rate limiting works
- ✅ Audit logs comprehensive
- ✅ Metrics available at /actuator/metrics
- ✅ Ready for production deployment

---

## QUICK REFERENCE: Files to Create

```
ai-infrastructure-behavior/
├── src/main/java/com/ai/behavior/
│   ├── config/
│   │   ├── BehaviorModuleConfiguration.java
│   │   ├── BehaviorModuleProperties.java
│   │   └── RateLimitingConfiguration.java
│   ├── controller/
│   │   ├── BehaviorEventController.java
│   │   └── BehaviorSearchController.java
│   ├── dto/
│   │   ├── OrchestratedQueryRequest.java
│   │   └── OrchestratedSearchResponse.java
│   ├── model/
│   │   ├── BehaviorEventEntity.java (NEW)
│   │   └── BehaviorInsights.java (UPDATE with @AICapable + @AIProcess)
│   ├── policy/
│   │   ├── BehaviorAnalysisPolicy.java
│   │   └── DefaultBehaviorAnalysisPolicy.java
│   ├── repository/
│   │   ├── BehaviorEventRepository.java
│   │   └── BehaviorInsightsRepository.java
│   ├── service/
│   │   ├── BehaviorEventIngestionService.java
│   │   ├── BehaviorAnalyzerService.java
│   │   ├── BehaviorSearchService.java
│   │   ├── BehaviorQueryOrchestrator.java
│   │   ├── BehaviorAuditService.java
│   │   └── BehaviorMetricsService.java
│   └── worker/
│       └── BehaviorAnalysisWorker.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── db/changelog/
│   │   └── V001__create_behavior_tables.sql
│   └── behavior/schemas/
│       └── default-schemas.yml
├── src/test/java/com/ai/behavior/integration/
│   ├── BehaviorAnalyticsIntegrationTest.java
│   ├── EventIngestionIntegrationTest.java
│   ├── BehaviorAnalysisIntegrationTest.java
│   └── BehaviorSearchIntegrationTest.java
└── pom.xml (UPDATE with dependencies)
```

---

## START HERE

1. **Read:** AI_BEHAVIOR_ANALYTICS_IMPLEMENTATION_SEQUENCE.md (5 minutes)
2. **Start:** Phase 1, Task 1.1.1 (Create directory structure)
3. **Follow:** Each task sequentially
4. **Reference:** Architecture doc as needed
5. **Test:** Each phase before moving to next
6. **Deploy:** Phase 6 when all tests pass

---

## WHEN STUCK

If you're blocked on something:

1. **Check:** Reference documents (Architecture, Philosophy)
2. **Search:** COUPLING_QUICK_ANSWER.txt for design patterns
3. **Test:** Write integration test to verify behavior
4. **Review:** Code patterns section above
5. **Ask:** With context about what you're trying to do

---

## SUCCESS SIGNAL

You'll know you're done when:

✅ All 6 phases complete
✅ Full integration test suite passes
✅ Can ingest event → worker analyzes → insights created → search works
✅ PII detection prevents sensitive queries
✅ Custom policy implementation recognized
✅ Monitoring & metrics active
✅ Rate limiting functional
✅ Audit logs comprehensive
✅ Ready for production deployment

---

**NOW GO BUILD! 🚀**

Follow `AI_BEHAVIOR_ANALYTICS_IMPLEMENTATION_SEQUENCE.md` step-by-step, and you'll have a production-ready AI Behavior Analytics microservice.

Good luck! 🎯

