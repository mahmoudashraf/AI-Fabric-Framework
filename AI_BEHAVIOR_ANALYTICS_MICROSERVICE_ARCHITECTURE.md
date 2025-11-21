# AI Behavior Analytics Microservice Architecture

**Pattern:** AI-First Microservice with Automatic Indexing

**Integration:** Behavior Module + AI Core Module (dependency)

---

## 📖 DOCUMENT FAMILY & EVOLUTION

This document represents the **FINAL, CORRECT ARCHITECTURE** after clarification that automatic indexing is already implemented in AI Core via `@AICapable` and `@AIProcess` annotations.

### Document Relationships:

| Document | Purpose | Status | Use When |
|----------|---------|--------|----------|
| **AI_BEHAVIOR_ANALYTICS_MODULE_V2_PHILOSOPHY.md** | Original v2 design thinking | **Reference Only** | Understanding initial requirements & philosophy |
| **AI_BEHAVIOR_ANALYTICS_MODULE_V2_IMPLEMENTATION_CHECKLIST.md** | v2 implementation tasks | **Partially Outdated** | Task structure (update tasks to use @AIProcess pattern) |
| **THIS DOCUMENT** | Final integrated architecture | ✅ **CURRENT** | **USE THIS FOR IMPLEMENTATION** |

### Key Evolution:

**v2 Philosophy → Final Architecture**
- ❌ "We need to manually manage indexing and orchestration"
- ✅ "AI Core handles ALL orchestration and indexing via @AIProcess"

- ❌ "Manual REST endpoints for search"
- ✅ "AI-Orchestrated search endpoint using AI Core's RAGOrchestrator"

- ❌ "Worker implements all business logic"
- ✅ "Worker uses policy hooks (domain-agnostic)"

---

## 🎯 CORE CONCEPT

```
Behavior Module generates insights
          ↓
        @AICapable on BehaviorInsights
          ↓
      @AIProcess on generation method
          ↓
    [AI Core Auto-Indexing Kicks In]
          ↓
AISearchableEntity created automatically
  ├─ type: "behavior-insight"
  ├─ entity_id: insight.userId
  ├─ vectorized + indexed
  └─ async strategy (configured)
          ↓
      FULLY SEARCHABLE
   + Vector embeddings
   + Full-text index
   + Semantic search ready
```

---

## 🏗️ ARCHITECTURE

### Layer 1: Event Ingestion (Behavior Module)

```
User Events
    ↓
POST /api/behavior/events
    ↓
BehaviorEventIngestionService
    ├─ Store in ai_behavior_events_temp (TTL)
    └─ Return 202 Accepted (non-blocking)
```

### Layer 2: Async AI Analysis (Behavior Module)

```
Scheduled Worker (every 5 min)
    ↓
BehaviorAnalysisWorker
    ├─ Query unprocessed events
    ├─ Call AIAnalyzer (LLM from core)
    ├─ Generate: patterns, recommendations, insights
    └─ Call: generateAndIndexInsights()
```

### Layer 3: @AIProcess Method - Automatic Indexing Trigger

```java
@Service
public class BehaviorAnalysisService {
    
    @AIProcess(
        type = "behavior-insight",
        indexingStrategy = "async"  // Async indexing
    )
    public BehaviorInsights generateAndIndexInsights(
        UUID userId, 
        List<BehaviorSignal> events
    ) {
        // Step 1: Generate insights via LLM
        BehaviorInsights insights = aiAnalyzer.analyze(userId, events);
        
        // Step 2: Store to database
        BehaviorInsights saved = insightsRepository.save(insights);
        
        // Step 3: @AIProcess triggers AI Core's auto-indexing
        //         ├─ Creates AISearchableEntity
        //         ├─ type = "behavior-insight"
        //         ├─ entity_id = userId
        //         ├─ Embeds insights content
        //         └─ Async indexed (configured strategy)
        
        return saved;
    }
}
```

### Layer 4: AI Core Module - Automatic Indexing

```
[AI Core Module]
    ↓
Detects @AIProcess annotation
    ├─ Intercepts method return (BehaviorInsights)
    ├─ Builds searchable content:
    │  └─ patterns + insights + recommendations + user segment
    ├─ Calls embedding service
    │  └─ Generates vector for semantic search
    ├─ Creates AISearchableEntity:
    │  ├─ type: "behavior-insight"
    │  ├─ entity_id: userId
    │  ├─ searchable_content: text representation
    │  ├─ metadata: { patterns, segment, confidence }
    │  └─ embeddings: vector
    ├─ Queues for async indexing (strategy configured)
    └─ Returns to caller (non-blocking)
```

### Layer 5: Search & Query Layer

```
GET /api/search/behavior-insights?q=power_user
    ↓
SearchService (uses AISearchableEntity)
    ├─ Full-text search on searchable_content
    ├─ Semantic search on embeddings
    └─ Return ranked results with metadata
```

---

## 📋 MODEL ANNOTATIONS

### BehaviorInsights Entity

```java
@Entity
@Table(name = "ai_behavior_insights")
@Data
@Builder
@AICapable  // ← Makes it discoverable by AI Core
public class BehaviorInsights {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(columnDefinition = "jsonb")
    private List<String> patterns;
    
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> insights;
    
    @Column(columnDefinition = "jsonb")
    private List<String> recommendations;
    
    @Column(columnDefinition = "vector(384)")
    private float[] embeddings;  // For semantic search
    
    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;
    
    @Column(name = "ai_model_used")
    private String aiModelUsed;  // "gpt-4o", "local-onnx", etc
    
    @Column(name = "confidence_score")
    private Double confidenceScore;
    
    @Column(name = "segment")
    private String segment;  // "power_user", "active", etc
    
    // AI Core will use this to determine searchable content
    public String getSearchableContent() {
        return String.format(
            "User Segment: %s. Patterns: %s. Insights: %s. Recommendations: %s",
            segment,
            String.join(", ", patterns != null ? patterns : List.of()),
            insights != null ? insights.toString() : "",
            String.join(", ", recommendations != null ? recommendations : List.of())
        );
    }
}
```

### Generation Method with @AIProcess

```java
@Service
@RequiredArgsConstructor
public class BehaviorAnalysisService {
    
    private final AIAnalyzer aiAnalyzer;
    private final BehaviorInsightsRepository repository;
    private final BehaviorRetentionService retentionService;
    
    /**
     * Generate AI insights and automatically index them
     * 
     * @AIProcess annotation triggers:
     * 1. Intercepts method execution
     * 2. Extracts return value (BehaviorInsights)
     * 3. Creates AISearchableEntity with type "behavior-insight"
     * 4. Embeds + Indexes asynchronously
     * 5. Returns to caller (non-blocking)
     */
    @Transactional
    @AIProcess(
        type = "behavior-insight",
        indexingStrategy = "async",
        embeddingStrategy = "semantic"
    )
    public BehaviorInsights generateAndIndexInsights(
        UUID userId,
        List<BehaviorSignal> events,
        LocalDateTime analyzedAt
    ) {
        
        // STEP 1: Call AI/LLM to generate insights
        String prompt = buildAnalysisPrompt(events);
        String llmResponse = aiAnalyzer.analyze(prompt);
        
        // STEP 2: Parse LLM response
        LLMAnalysisResult result = parseResponse(llmResponse);
        
        // STEP 3: Build BehaviorInsights entity
        BehaviorInsights insights = BehaviorInsights.builder()
            .userId(userId)
            .patterns(result.getPatterns())
            .insights(result.getInsights())
            .recommendations(result.getRecommendations())
            .segment(result.getSegment())
            .confidenceScore(result.getConfidence())
            .analyzedAt(analyzedAt)
            .aiModelUsed("gpt-4o")
            .build();
        
        // STEP 4: Save to database
        BehaviorInsights saved = repository.save(insights);
        
        // STEP 5: Return
        // ← @AIProcess intercepts here!
        //   ├─ Detects type = "behavior-insight"
        //   ├─ Builds searchable content from insights
        //   ├─ Creates AISearchableEntity
        //   ├─ Embeds using configured strategy
        //   ├─ Queues async indexing job
        //   └─ Returns immediately (non-blocking)
        return saved;
    }
    
    private String buildAnalysisPrompt(List<BehaviorSignal> events) {
        return String.format(
            """
            Analyze user behavior from these %d events and provide JSON with:
            {
              "segment": "power_user|active|steady|dormant|emerging",
              "patterns": ["pattern1", "pattern2"],
              "recommendations": ["rec1", "rec2"],
              "insights": {
                "engagement": "high|medium|low",
                "churnRisk": 0.0-1.0,
                "ltv": "high|medium|low"
              },
              "confidence": 0.0-1.0
            }
            
            Events: %s
            """,
            events.size(),
            events.toString()
        );
    }
    
    private LLMAnalysisResult parseResponse(String response) {
        // Parse JSON from LLM response
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(response, LLMAnalysisResult.class);
    }
}
```

---

## 🔄 COMPLETE FLOW

### Flow: Event → Insight → Indexed

```
1️⃣ USER GENERATES EVENT
   POST /api/behavior/events
   ├─ userId: "550e8400-..."
   ├─ eventType: "purchase"
   └─ eventData: { amount: 99.99, ... }
   
   ↓ Stored in: ai_behavior_events_temp (TTL 24h)
   
2️⃣ WORKER PROCESSES EVENTS (every 5 min)
   BehaviorAnalysisWorker.processEvents()
   ├─ Query: unprocessed events
   ├─ For each user:
   │  └─ Call: generateAndIndexInsights(userId, events)
   
3️⃣ AI ANALYSIS HAPPENS
   generateAndIndexInsights()
   ├─ Call LLM (gpt-4o via AI Core)
   ├─ Parse response
   ├─ Build BehaviorInsights
   ├─ Save to DB: ai_behavior_insights
   └─ RETURN ← @AIProcess intercepts!
   
4️⃣ @AIProcess INTERCEPTS
   AI Core Module Auto-Indexing:
   ├─ Detects @AIProcess annotation
   ├─ Extracts BehaviorInsights from return
   ├─ Builds searchable content:
   │  └─ "Segment: power_user. Patterns: high_engagement, recent. 
   │      Recommendations: loyalty_program. Confidence: 0.92"
   ├─ Embeds using embedding service
   │  └─ Generates 384-dim vector
   ├─ Creates AISearchableEntity:
   │  ├─ type: "behavior-insight"
   │  ├─ entity_id: userId
   │  ├─ searchable_content: (built above)
   │  ├─ metadata: { patterns, segment, confidence }
   │  └─ vector_id: (stored in embedding table)
   └─ Queues indexing job (async, configured)
   
5️⃣ ASYNC INDEXING EXECUTES
   [Background Thread/Job]
   ├─ Receive AISearchableEntity
   ├─ Index to configured backend:
   │  ├─ Elasticsearch (if configured)
   │  ├─ Lucene (if configured)
   │  └─ Vector DB (if configured)
   ├─ Update ai_searchable_entities table
   └─ Mark as indexed
   
6️⃣ USER QUERIES INSIGHTS
   GET /api/search/behavior-insights?q=power_user+loyalty
   
   SearchService:
   ├─ Query AISearchableEntity (full-text search)
   │  └─ WHERE type='behavior-insight' 
   │     AND searchable_content LIKE '%power_user%'
   ├─ Call embedding service for semantic match
   │  └─ Embed query: "users interested in loyalty programs"
   │     Find similar vectors
   ├─ Combine results (ranked by relevance)
   └─ Return with metadata + original BehaviorInsights
   
7️⃣ CLEANUP RUNS (hourly)
   ProcessedEventCleanupJob:
   ├─ Delete from ai_behavior_events_temp (processed)
   └─ No need to delete AISearchableEntity 
      (it's permanent, searchable record)
```

---

## 🗄️ DATABASE SCHEMA

### Existing Tables (Behavior Module)

```
ai_behavior_events_temp
├─ id (UUID)
├─ user_id (UUID)
├─ event_type (VARCHAR)
├─ event_data (JSON)
├─ processed (BOOLEAN)
├─ expires_at (TIMESTAMP)
└─ ...

ai_behavior_insights
├─ id (UUID)
├─ user_id (UUID)
├─ patterns (JSON array)
├─ insights (JSON)
├─ recommendations (JSON array)
├─ embeddings (VECTOR 384)
├─ segment (VARCHAR)
├─ confidence_score (FLOAT)
├─ analyzed_at (TIMESTAMP)
└─ ...
```

### Reused Tables (AI Core Module - Already Exists!)

```
ai_searchable_entities
├─ id (VARCHAR, PK)
├─ entity_type (VARCHAR)      ← "behavior-insight"
├─ entity_id (VARCHAR)         ← userId
├─ searchable_content (TEXT)   ← Combined text representation
├─ metadata (JSON)             ← patterns, segment, confidence
├─ vector_id (VARCHAR)         ← Link to embeddings
├─ created_at (TIMESTAMP)
├─ updated_at (TIMESTAMP)
└─ Indexes:
   ├─ (entity_type, entity_id)
   ├─ (vector_id) for semantic search
   └─ Full-text index on searchable_content
```

---

## 🔧 CONFIGURATION (Behavior Module)

### In application.yml

```yaml
ai:
  behavior:
    # Ingestion
    ingestion:
      max-batch-size: 1000
      
    # Worker
    worker:
      enabled: true
      schedule: "0 */5 * * * *"
      batch-size: 1000
      
    # AI Analysis (uses ai-infrastructure-core)
    analysis:
      ai-provider: "gpt-4o"
      fallback-provider: "local-onnx"
      
    # Indexing (delegated to AI Core)
    indexing:
      enabled: true
      strategy: "async"
      batch-size: 100
      # AI Core uses its own indexing config
      
    # Storage
    storage:
      temp-ttl-hours: 24
      
    # Cleanup
    cleanup:
      enabled: true
      schedule: "0 0 * * * *"  # hourly
```

---

## 🎯 KEY FEATURES

### What You Get Automatically

```
✅ Behavior Insights
├─ Generated by AI (LLM configurable)
├─ Stored in database
└─ Always available

✅ Automatic Indexing (from AI Core)
├─ AISearchableEntity created automatically
├─ Type: "behavior-insight"
├─ Full-text searchable
├─ Vector embeddings (semantic search)
└─ Async indexing (configured)

✅ Search Capabilities
├─ Full-text: "power_user"
├─ Semantic: "users interested in loyalty"
├─ Filtered: by segment, confidence, pattern
└─ Ranked by relevance

✅ Query Features
├─ Single user: GET /users/{id}/insights
├─ Cross-user search: GET /search/behavior-insights
├─ Advanced: POST /query with filters
├─ Reports: GET /reports/* endpoints
└─ Semantic: similar users via embeddings
```

### Zero Extra Indexing Code Needed!

```
❌ NO manual indexing code
❌ NO manual embedding calls
❌ NO manual AISearchableEntity creation
❌ NO manual vector storage

✅ JUST annotate with @AIProcess
✅ AI Core handles everything else
✅ Async, efficient, scalable
```

---

## 🏢 AS A MICROSERVICE

### Single Responsibility

```
AI Behavior Analytics Microservice:
├─ Accepts user events
├─ Analyzes behavior patterns with AI
├─ Stores searchable insights
├─ Serves analytics queries
└─ (Automatic indexing handled by core)
```

### Deployable Independently

```
Requirements:
├─ ai-infrastructure-core (dependency)
├─ PostgreSQL (with pgvector)
├─ Optional: Elasticsearch/Lucene
└─ Optional: Redis cache

Can run:
├─ Standalone REST service
├─ Part of larger system
├─ Behind load balancer
└─ Horizontal scaling (multiple instances)
```

### API Endpoints

```
Event Ingestion:
├─ POST /api/behavior/events
├─ POST /api/behavior/events/batch

Analytics Queries:
├─ GET /api/ai/analytics/users/{userId}
├─ GET /api/search/behavior-insights?q=...
├─ POST /api/query (complex filters)

NEW! AI-Orchestrated Query (AI Core Integration):
├─ POST /api/search/orchestrated
│  ├─ Input: Natural language user query
│  ├─ PII Detection (via AI Core)
│  ├─ Query Transformation
│  ├─ Search Execution
│  └─ Response Generation
└─ Response: AI-generated insights + matched data

Reports:
├─ GET /api/reports/segments
├─ GET /api/reports/patterns
└─ GET /api/reports/recommendations
```

---

## 📊 ARCHITECTURE DIAGRAM

```
┌─────────────────────────────────────────────────────────┐
│ BEHAVIOR ANALYTICS MICROSERVICE                         │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ REST API Layer                                          │
│ ├─ POST /events (ingestion)                           │
│ ├─ GET /insights (queries)                            │
│ └─ GET /search (AI-powered search)                    │
│                                                         │
│ Service Layer (Behavior Module)                        │
│ ├─ BehaviorEventIngestionService                      │
│ ├─ BehaviorAnalysisWorker                             │
│ ├─ BehaviorAnalysisService @AIProcess ← Key!          │
│ └─ BehaviorRetentionService                           │
│                                                         │
│ AI Integration (via AI Core dependency)               │
│ ├─ AIAnalyzer (calls LLM)                            │
│ ├─ @AIProcess interceptor (auto-indexing)            │
│ ├─ EmbeddingService (semantic vectors)               │
│ └─ SearchService (query via AISearchableEntity)       │
│                                                         │
│ Storage Layer                                          │
│ ├─ ai_behavior_events_temp (TTL)                     │
│ ├─ ai_behavior_insights (permanent)                  │
│ ├─ ai_searchable_entities (indexed, AI Core)        │
│ └─ embeddings (vector table, AI Core)                │
│                                                         │
└─────────────────────────────────────────────────────────┘
         ↑ All indexing automated by @AIProcess!
```

---

## 🚀 IMPLEMENTATION

### Only Need to Code (Behavior Module):

1. ✅ BehaviorEventIngestionService
2. ✅ BehaviorAnalysisWorker  
3. ✅ BehaviorAnalysisService with @AIProcess
4. ✅ Query/Search endpoints

### AI Core Module Provides:

1. ✅ @AIProcess interceptor
2. ✅ AISearchableEntity creation
3. ✅ Embedding generation
4. ✅ Async indexing
5. ✅ Search capabilities
6. ✅ Vector storage

### Total Code: ~800 lines in Behavior Module

- BehaviorEventIngestionService: 100 lines
- BehaviorAnalysisWorker: 150 lines
- BehaviorAnalysisService: 200 lines (@AIProcess only!)
- Controllers (REST API): 300 lines
- DTOs/Models: 100 lines

---

## ✅ SUMMARY

**This is a TRUE AI-First Microservice because:**

1. ✅ All insights generated by AI (LLM)
2. ✅ All insights automatically indexed by @AIProcess
3. ✅ All insights fully searchable (full-text + semantic)
4. ✅ All indexing handled by AI Core (zero manual indexing code)
5. ✅ Async everything (non-blocking)
6. ✅ Completely independent (just depends on AI Core)
7. ✅ Scales horizontally (stateless workers)

**Effort: 3 weeks to MVP**

---

## 🤖 NEW: AI-ORCHESTRATED QUERY ENDPOINT

### Endpoint: POST /api/search/orchestrated

**Purpose:** Accept user queries, orchestrate through AI Core with PII detection, execute searches, return AI-enriched results

### Request

```json
{
  "query": "Show me high-value users who have mobile preference and are at risk of churn",
  "limit": 20,
  "includeExplanation": true
}
```

### Response

```json
{
  "query": "Show me high-value users who have mobile preference and are at risk of churn",
  "executedAt": "2025-11-19T10:30:00Z",
  "piiDetected": false,
  "results": {
    "matchedUsers": [
      {
        "userId": "550e8400-e29b-41d4-a716-446655440000",
        "segment": "high_value",
        "patterns": ["power_user", "mobile_preference", "high_risk_churn"],
        "recommendations": ["loyalty_program", "retention_offer"],
        "confidence": 0.92,
        "analyzedAt": "2025-11-19T02:30:00Z"
      },
      ...
    ],
    "totalMatches": 234,
    "searchStrategy": "semantic+filter",
    "aiExplanation": "Found 234 users matching your criteria. These are your most valuable customers at risk. Recommend sending personalized retention offers immediately."
  }
}
```

### Implementation

```java
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class BehaviorSearchController {
    
    private final BehaviorQueryOrchestrator queryOrchestrator;
    
    @PostMapping("/orchestrated")
    public ResponseEntity<OrchestratedSearchResponse> executeOrchestratedQuery(
        @RequestBody OrchestratedQueryRequest request
    ) {
        // Delegate to orchestrator
        OrchestratedSearchResponse response = 
            queryOrchestrator.executeQuery(request);
        
        return ResponseEntity.ok(response);
    }
}
```

### Service: BehaviorQueryOrchestrator

```java
@Service
@RequiredArgsConstructor
public class BehaviorQueryOrchestrator {
    
    private final RAGOrchestrator ragOrchestrator;  // ← From AI Core!
    private final BehaviorSearchService searchService;
    private final PIIDetectionService piiDetection;  // ← From AI Core
    
    public OrchestratedSearchResponse executeQuery(
        OrchestratedQueryRequest request
    ) {
        // STEP 1: PII Detection (via AI Core)
        PIIDetectionResult piiResult = piiDetection.detect(request.getQuery());
        
        if (piiResult.hasSensitiveData()) {
            return OrchestratedSearchResponse.builder()
                .query(request.getQuery())
                .piiDetected(true)
                .error("Query contains PII. Please rephrase without sensitive data.")
                .build();
        }
        
        // STEP 2: Orchestrate through AI Core
        RAGOrchestrationContext context = RAGOrchestrationContext.builder()
            .userQuery(request.getQuery())
            .source("behavior-analytics")
            .dataType("behavior-insights")
            .intent("search")
            .build();
        
        RAGResponse ragResponse = ragOrchestrator.orchestrate(context);
        
        // STEP 3: Transform AI query to search parameters
        SearchParameters searchParams = transformToSearchParams(
            ragResponse.getTransformedQuery()
        );
        
        // STEP 4: Execute search against AISearchableEntity
        List<BehaviorInsights> searchResults = searchService.search(
            searchParams,
            request.getLimit()
        );
        
        // STEP 5: Generate AI explanation
        String explanation = ragOrchestrator.generateExplanation(
            request.getQuery(),
            searchResults
        );
        
        // STEP 6: Return orchestrated response
        return OrchestratedSearchResponse.builder()
            .query(request.getQuery())
            .executedAt(LocalDateTime.now())
            .piiDetected(false)
            .results(SearchResults.builder()
                .matchedUsers(searchResults)
                .totalMatches(searchResults.size())
                .searchStrategy(ragResponse.getSearchStrategy())
                .aiExplanation(explanation)
                .build())
            .build();
    }
    
    private SearchParameters transformToSearchParams(String transformedQuery) {
        // Parse AI-transformed query into structured search params
        // Example: "segment=high_value AND pattern=mobile_preference"
        // → SearchParameters with filters
        
        return SearchParameters.builder()
            .query(transformedQuery)
            .filters(parseFilters(transformedQuery))
            .build();
    }
}
```

### Request/Response DTOs

```java
@Data
@Builder
public class OrchestratedQueryRequest {
    private String query;  // Natural language query
    private int limit;  // Max results (default 20)
    private boolean includeExplanation;  // Default true
    private String userId;  // Optional: for audit
}

@Data
@Builder
public class OrchestratedSearchResponse {
    private String query;
    private LocalDateTime executedAt;
    private boolean piiDetected;
    private String error;  // If PII detected
    private SearchResults results;
}

@Data
@Builder
public class SearchResults {
    private List<BehaviorInsights> matchedUsers;
    private long totalMatches;
    private String searchStrategy;  // "semantic+filter" | "dense+sparse"
    private String aiExplanation;  // AI-generated summary
}
```

### Complete Flow Diagram

```
User Query
    ↓
POST /api/search/orchestrated
    ↓
BehaviorQueryOrchestrator.executeQuery()
    │
    ├─ STEP 1: PII Detection
    │  └─ PIIDetectionService.detect() [AI Core]
    │     ├─ If PII found → Return error (STOP)
    │     └─ If clean → Continue
    │
    ├─ STEP 2: Query Orchestration
    │  └─ RAGOrchestrator.orchestrate() [AI Core]
    │     ├─ Query classification
    │     ├─ Intent extraction
    │     ├─ Context enrichment
    │     └─ Return: transformed query + strategy
    │
    ├─ STEP 3: Transform to Search Params
    │  └─ Parse: "segment=high_value AND risk<0.2"
    │     → SearchParameters(filters, limit)
    │
    ├─ STEP 4: Execute Search
    │  └─ BehaviorSearchService.search()
    │     └─ Query ai_searchable_entities
    │        + embeddings (semantic)
    │        + filters (structured)
    │        + ranking
    │
    ├─ STEP 5: Generate Explanation
    │  └─ RAGOrchestrator.generateExplanation() [AI Core]
    │     ├─ Query + Results → LLM
    │     └─ Return: "Found 234 users... Recommend..."
    │
    └─ STEP 6: Return Response
       └─ OrchestratedSearchResponse
          ├─ matchedUsers: [...]
          ├─ totalMatches: 234
          ├─ searchStrategy: "semantic+filter"
          └─ aiExplanation: "..."
```

### Query Examples

```
Query 1: "Show me users at risk of churn"
↓ AI Core transforms to ↓
SearchParams: { filters: { riskScore: { max: 0.5 } } }
↓
Results: All users with risk_score ≤ 0.5

Query 2: "Which power users prefer mobile?"
↓ AI Core transforms to ↓
SearchParams: { 
  patterns: ["power_user"],
  filters: { patterns: { contains: "mobile_preference" } }
}
↓
Results: Users with both patterns

Query 3: "Find high-value users we should offer loyalty programs"
↓ AI Core transforms to ↓
SearchParams: {
  segment: "high_value",
  recommendations: { notContains: "loyalty_program" }
}
↓
Results: High-value users not yet offered loyalty program
```

### Security & Compliance

```
✅ PII Detection (AI Core)
   └─ Detects sensitive data in queries
   └─ Returns error if found
   └─ Audit logged

✅ Query Auditing
   ├─ Log all queries (with userId)
   ├─ Log results count
   └─ Log execution time

✅ Rate Limiting
   ├─ Per user: max 100 queries/hour
   └─ Per endpoint: max 1000 queries/hour

✅ Response Filtering
   ├─ Don't return raw data (only insights)
   ├─ Redact sensitive metadata
   └─ Use AISearchableEntity (already safe)
```

### Configuration

```yaml
ai:
  behavior:
    search:
      orchestrated:
        enabled: true
        pii-detection: true  # AI Core handles
        max-results: 100
        timeout: 30s
        rate-limit:
          per-user: 100/hour
          per-endpoint: 1000/hour
```

---

## 📝 COMPLETE CODE CHECKLIST

- [ ] BehaviorInsights with @AICapable
- [ ] generateAndIndexInsights() with @AIProcess
- [ ] BehaviorEventIngestionService
- [ ] BehaviorAnalysisWorker
- [ ] REST endpoints (events, queries, search)
- [ ] **NEW: BehaviorQueryOrchestrator (AI-orchestrated)**
- [ ] **NEW: OrchestratedSearchResponse DTOs**
- [ ] **NEW: BehaviorSearchController with /orchestrated endpoint**
- [ ] Configuration in application.yml
- [ ] Security: PII detection, audit logging, rate limiting
- [ ] No manual indexing code! (AI Core handles it)
- [ ] No manual orchestration code! (AI Core handles it)

---

## 🔄 MIGRATION FROM V2 PHILOSOPHY

If you're implementing using the v2 checklist, here are the **KEY DIFFERENCES** to adapt:

### ❌ OLD V2 APPROACH (Don't do this)

```java
// v2: Manual indexing in worker
public void analyzeUserBehavior(UUID userId) {
    List<BehaviorSignal> signals = getSignals(userId);
    BehaviorInsights insights = analyzer.analyze(signals);
    
    // Manual storage
    repository.save(insights);
    
    // Manual indexing (YOUR RESPONSIBILITY)
    vectorStore.upsert(insights.toVector());  // ❌ Don't!
    elasticSearch.index(insights);  // ❌ Don't!
}
```

### ✅ NEW APPROACH (Use this instead)

```java
// NEW: Use @AICapable + @AIProcess
@Entity
@AICapable  // ← Tell AI Core about this entity
@Table(name = "behavior_insights")
public class BehaviorInsights {
    // ... fields ...
    
    @AIProcess(
        strategy = AIProcessStrategy.ASYNC,  // Configurable!
        type = "behavior-insight"
    )
    public void notifyInsightsReady() {
        // Called automatically by AI Core after this object is saved
        // AI Core will:
        // 1. Vectorize this entity
        // 2. Create AISearchableEntity
        // 3. Index in vector DB + full-text
        // 4. All async! (configurable)
    }
}

// In Worker: Just generate, AI Core indexes automatically!
public void analyzeUserBehavior(UUID userId) {
    List<BehaviorSignal> signals = getSignals(userId);
    BehaviorInsights insights = analyzer.analyze(signals);
    
    // Save triggers @AIProcess automatically
    repository.save(insights);  // ← AI Core does the rest!
    
    // No manual indexing needed!
}
```

### MIGRATION CHECKLIST

From v2 Philosophy to Final Architecture:

- [ ] Replace manual indexing code with `@AIProcess` annotations
- [ ] Remove manual vectorStore/elasticSearch calls
- [ ] Add AI Core as dependency (already done)
- [ ] Add `@AICapable` to BehaviorInsights entity
- [ ] Update worker to use policy hooks (not hardcoded logic)
- [ ] Replace raw search with BehaviorQueryOrchestrator
- [ ] Add PII detection via AI Core (don't implement yourself)
- [ ] Configuration in application.yml for @AIProcess strategy

### WHAT TO REUSE FROM V2 CHECKLIST

✅ Database schema (mostly same, but let AI Core create ai_searchable_entities)
✅ Event ingestion logic
✅ TTL configuration for temp events
✅ Worker scheduling strategy
✅ REST endpoint structure (update with orchestration)
✅ Testing approach (add orchestration tests)

### WHAT TO CHANGE FROM V2 CHECKLIST

❌ Remove: "Manual indexing task"
❌ Remove: "Manual search implementation"
❌ Remove: "Orchestration task"
✅ Add: "@AIProcess configuration"
✅ Add: "Query orchestration tests"
✅ Add: "Policy hook implementation"


