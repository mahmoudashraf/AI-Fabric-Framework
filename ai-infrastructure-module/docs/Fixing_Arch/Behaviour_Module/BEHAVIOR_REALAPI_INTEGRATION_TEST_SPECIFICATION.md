# Behavior Module - Real API Integration Test Specification

**Version:** 1.0.0  
**Status:** Implementation Ready  
**Purpose:** Comprehensive test coverage for behavior module real API integration  
**Target Module:** `ai-infrastructure-module/integration-Testing/behavior-integration-tests`

---

## 📋 TABLE OF CONTENTS

1. [Executive Summary](#1-executive-summary)
2. [Implementation Document Review](#2-implementation-document-review)
3. [Critical Test Scenarios Matrix](#3-critical-test-scenarios-matrix)
4. [Test Suite Architecture](#4-test-suite-architecture)
5. [Test Specifications by Category](#5-test-specifications-by-category)
6. [GitHub Action Integration](#6-github-action-integration)
7. [Test Data Patterns](#7-test-data-patterns)
8. [Success Criteria & Assertions](#8-success-criteria--assertions)
9. [Performance & Quality Benchmarks](#9-performance--quality-benchmarks)
10. [Implementation Checklist](#10-implementation-checklist)

---

## 1. Executive Summary

### 1.1 Purpose

This document specifies comprehensive real API integration tests for the **AI Behavior Module v2**, ensuring:
- **End-to-end validation** with actual LLM API calls (OpenAI, Anthropic, etc.)
- **Complete coverage** of sentiment analysis, churn prediction, and trend evolution
- **Production-readiness** through resilience, error handling, and edge case validation
- **Quality assurance** for LLM output accuracy and consistency

### 1.2 Scope

**Implementation Documents Reviewed:**
1. `AI_BEHAVIOR_V2_TECHNICAL_IMPLEMENTATION.md` - Core architecture and evolutionary analysis
2. `BEHAVIOR_SENTIMENT_CHURN_IMPLEMENTATION.md` - Sentiment/churn analytics with enums

**Test Coverage Areas:**
- ✅ Baseline behavioral analysis (new users)
- ✅ Sentiment detection across all 6 labels (DELIGHTED → CHURNING)
- ✅ Churn risk prediction across all risk levels (0.0 → 1.0)
- ✅ Trend evolution tracking (NEW_USER, IMPROVING, DECLINING, etc.)
- ✅ Delta calculations and state transitions
- ✅ Enum validation and fallback handling
- ✅ Batch processing and discovery mode
- ✅ Error resilience and recovery
- ✅ API integration (Processing & Analytics APIs)
- ✅ Persistence and update semantics
- ✅ LLM output validation and quality

### 1.3 Test Execution Context

**Profile:** `realapi` (Maven Failsafe plugin)  
**Runner Script:** `run-behavior-realapi-tests.sh`  
**GitHub Action:** `integration-tests-manual.yml` (Job: `behavior-tests`)  
**Test Pattern:** `**/*RealApiIT.java` or `**/*IntegrationIT.java` in `src/test/java/.../realapi/`

**Environment Requirements:**
- Java 21+
- Maven 3.8+
- OPENAI_API_KEY (or other provider API keys)
- H2 or PostgreSQL database
- Provider matrix: `LLM:EMBEDDING:VECTOR_DB` (e.g., `openai:onnx:lucene`)

---

## 2. Implementation Document Review

### 2.1 AI_BEHAVIOR_V2_TECHNICAL_IMPLEMENTATION.md

**Key Architecture Components:**

| Component | Description | Test Impact |
|-----------|-------------|-------------|
| **ExternalEventProvider SPI** | User-implemented interface to pull events | Tests must provide mock/test implementation |
| **BehaviorAnalysisService** | Core evolutionary analysis logic | Tests validate LLM prompt building, response parsing |
| **BehaviorStorageAdapter** | Routes to custom store or JPA repository | Tests verify persistence and updates |
| **@AIProcess Annotation** | Triggers framework indexing in FULL mode | Tests in FULL mode verify searchability |
| **Evolutionary Prompt** | Includes previous state for trend detection | Tests validate delta calculations |
| **User Context** | Optional metadata for discovery mode | Tests validate context inclusion in LLM prompts |

**Critical Flows to Test:**

1. **Case 1: Targeted Analysis**
   ```
   User → analyzeUser(userId) → Fetch existing insight → Pull events → 
   Build prompt → LLM call → Parse response → Save → Return
   ```

2. **Case 2: Discovery/Batch Processing**
   ```
   Scheduler → processNextUser() → Provider.getNextUserEvents() → 
   Include user context → LLM call → Save → Loop until null
   ```

### 2.2 BEHAVIOR_SENTIMENT_CHURN_IMPLEMENTATION.md

**Domain Enums - Test Coverage Required:**

**SentimentLabel Enum (6 values):**
```
DELIGHTED     → Extremely positive (referrals, upgrades)
SATISFIED     → Positive without friction
NEUTRAL       → No strong signals
CONFUSED      → Help-seeking, repeated attempts
FRUSTRATED    → Errors, support tickets, abandonment
CHURNING      → Cancellation signals, competitor research
```

**BehaviorTrend Enum (6 values):**
```
NEW_USER              → First analysis, no previous data
RAPIDLY_IMPROVING     → Δsentiment >0.4 OR Δchurn <-0.4
IMPROVING             → Δsentiment >0.2 OR Δchurn <-0.2
STABLE                → |Δsentiment| <0.2 AND |Δchurn| <0.2
DECLINING             → Δsentiment <-0.2 OR Δchurn >0.2
RAPIDLY_DECLINING     → Δsentiment <-0.4 OR Δchurn >0.4 ⚠️
```

**Key Features to Validate:**

| Feature | Test Requirement |
|---------|------------------|
| **Sentiment Score** | Range: -1.0 to 1.0, LLM-generated, clamped |
| **Churn Risk** | Range: 0.0 to 1.0, LLM-generated, clamped |
| **Churn Reason** | Required for risk >0.5, specific and actionable |
| **Previous Values** | Preserved from last analysis for delta tracking |
| **Delta Calculations** | Computed via @Transient methods (getSentimentDelta, getChurnDelta) |
| **Trend Fallback** | If LLM doesn't provide valid trend, compute from deltas |
| **Helper Methods** | isSentimentImproving(), isChurnRiskIncreasing(), requiresImmediateAction() |

**Prompt Engineering Validation:**
- System prompt instructs LLM on output format (JSON with specific fields)
- Evolutionary prompt includes previous analysis for comparison
- User context (if provided) included in prompt for richer analysis

---

## 3. Critical Test Scenarios Matrix

### 3.1 Priority 1 - Core Functionality (Must Have)

| Test ID | Scenario | Category | LLM Confidence | Implementation Doc Reference |
|---------|----------|----------|----------------|------------------------------|
| **P1-1** | New user with positive onboarding → DELIGHTED/SATISFIED | Baseline | 95%+ | V2 §5.1, Sentiment §2 |
| **P1-2** | New user with confused behavior → CONFUSED/NEUTRAL | Baseline | 95%+ | Sentiment §2 |
| **P1-3** | Happy user → frustration events → DECLINING trend | Evolution | 92%+ | V2 §5.1, Sentiment §3 |
| **P1-4** | Struggling user → successful events → IMPROVING trend | Evolution | 92%+ | V2 §5.1, Sentiment §3 |
| **P1-5** | High churn risk with specific reason | Churn Prediction | 90%+ | Sentiment §4 |
| **P1-6** | Critical churn (0.8+) → requiresImmediateAction() = true | Churn Prediction | 90%+ | Sentiment §4 |
| **P1-7** | Previous values preserved across analyses | State Management | N/A | V2 §5.1, Sentiment §5 |
| **P1-8** | Delta calculations (sentiment, churn) accurate | State Management | N/A | Sentiment §5 |
| **P1-9** | Batch processing via processNextUser() | Batch Mode | N/A | V2 §5.1 |
| **P1-10** | User context included in discovery mode prompt | Batch Mode | N/A | V2 Appendix A |

### 3.2 Priority 2 - Sentiment Analysis Coverage (Should Have)

| Test ID | Scenario | Expected Label | Behavioral Signals |
|---------|----------|----------------|-------------------|
| **P2-1** | Referrals + upgrades + positive feedback | DELIGHTED | Advocacy, expansion, satisfaction |
| **P2-2** | Consistent usage, no errors | SATISFIED | Regular engagement, success |
| **P2-3** | Mixed signals, minimal interaction | NEUTRAL | Passive usage |
| **P2-4** | Repeated help page views, failed attempts | CONFUSED | Help-seeking, low success rate |
| **P2-5** | Errors + support tickets + abandonment | FRUSTRATED | Friction, incomplete workflows |
| **P2-6** | Cancellation research + competitor pricing | CHURNING | Departure signals |

### 3.3 Priority 2 - Churn Risk Levels (Should Have)

| Test ID | Scenario | Expected Risk | Key Signals |
|---------|----------|---------------|-------------|
| **P2-7** | Active engaged user (90-day streak) | 0.0 - 0.2 | Daily usage, integrations, team collaboration |
| **P2-8** | Declining engagement frequency | 0.4 - 0.6 | Login drop, feature usage reduction |
| **P2-9** | Payment issues + cancellation page | 0.8 - 1.0 | Billing failures, deletion attempts |
| **P2-10** | Data export + competitor research | 0.8 - 1.0 | Migration signals |

### 3.4 Priority 2 - Trend Evolution (Should Have)

| Test ID | Scenario | Expected Trend | Delta Thresholds |
|---------|----------|----------------|------------------|
| **P2-11** | First analysis | NEW_USER | No previous data |
| **P2-12** | Major positive shift | RAPIDLY_IMPROVING | Δsentiment >0.4 OR Δchurn <-0.4 |
| **P2-13** | Positive shift | IMPROVING | Δsentiment >0.2 OR Δchurn <-0.2 |
| **P2-14** | No significant change | STABLE | \|Δ\| <0.2 |
| **P2-15** | Negative shift | DECLINING | Δsentiment <-0.2 OR Δchurn >0.2 |
| **P2-16** | Major negative shift | RAPIDLY_DECLINING | Δsentiment <-0.4 OR Δchurn >0.4 |

### 3.5 Priority 3 - Edge Cases & Error Handling (Nice to Have)

| Test ID | Scenario | Expected Behavior |
|---------|----------|-------------------|
| **P3-1** | Empty events for existing user | Return existing insight unchanged |
| **P3-2** | Empty events for non-existent user | Return null |
| **P3-3** | LLM returns invalid sentiment label | Fallback to NEUTRAL with warning log |
| **P3-4** | LLM returns invalid trend | Compute from deltas with warning log |
| **P3-5** | Sentiment score out of range (e.g., 1.5) | Clamp to [-1.0, 1.0] |
| **P3-6** | Churn risk out of range (e.g., -0.2) | Clamp to [0.0, 1.0] |
| **P3-7** | High churn risk (>0.5) without reason | Generate default reason with warning |
| **P3-8** | Multiple analyses preserve entity ID | Update existing, don't create new |
| **P3-9** | Timestamps: createdAt preserved, updatedAt refreshed | JPA lifecycle hooks validated |
| **P3-10** | LLM timeout/error | Return existing insight or minimal fallback |

### 3.6 Priority 3 - API Integration (Nice to Have)

| Test ID | Scenario | Endpoint | Expected Response |
|---------|----------|----------|-------------------|
| **P3-11** | Trigger targeted analysis | POST /api/behavior/processing/users/{userId} | BehaviorInsights with analysis result |
| **P3-12** | Batch processing | POST /api/behavior/processing/batch | {processedCount, duration} |
| **P3-13** | Get rapid decline alerts | GET /api/behavior/analytics/rapid-decline | List of users with RAPIDLY_DECLINING trend |
| **P3-14** | Get trend distribution | GET /api/behavior/analytics/trend-distribution | Map<TrendLabel, Count> |
| **P3-15** | Get user trend detail | GET /api/behavior/analytics/users/{userId}/trend | UserTrendDTO with deltas |

### 3.7 Priority 3 - FULL Mode Integration (Nice to Have)

| Test ID | Scenario | Expected Behavior |
|---------|----------|-------------------|
| **P3-16** | FULL mode: @AIProcess triggers indexing | AISearchableEntity created after save |
| **P3-17** | FULL mode: Semantic search for sentiment labels | Query "frustrated users" returns FRUSTRATED label users |
| **P3-18** | FULL mode: Search by behavioral patterns | Query "help seekers" returns users with help-seeking patterns |
| **P3-19** | LIGHT mode: No indexing occurs | AISearchableEntity NOT created |

---

## 4. Test Suite Architecture

### 4.1 Test Class Organization

```
src/test/java/com/ai/infrastructure/behavior/it/realapi/
├── BehaviorRealApiIntegrationIT.java           (Existing - Core baseline & evolution)
├── BehaviorSentimentChurnRealApiIT.java        (Existing - Sentiment/churn detection)
├── BehaviorTrendEvolutionRealApiIT.java        (Existing - Trend tracking)
├── BehaviorEnumValidationRealApiIT.java        (Existing - Enum fallback handling)
├── BehaviorBatchProcessingRealApiIT.java       (Existing - Batch mode)
│
├── BehaviorEdgeCasesRealApiIT.java             [NEW] ← Edge cases & error handling
├── BehaviorApiEndpointsRealApiIT.java          [NEW] ← REST API integration
├── BehaviorFullModeIndexingRealApiIT.java      [NEW] ← FULL mode semantic search
├── BehaviorPersistenceRealApiIT.java           [NEW] ← Persistence semantics
└── BehaviorLlmQualityRealApiIT.java            [NEW] ← LLM output quality validation
```

### 4.2 Test Base Configuration

**Common Setup (All Test Classes):**

```java
@SpringBootTest(
    classes = BehaviorIntegrationTestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "ai.behavior.enabled=true",
        "ai.behavior.mode=FULL",  // or LIGHT for specific tests
        "ai.behavior.processing.api-enabled=true",
        "spring.jpa.show-sql=false"
    }
)
@ActiveProfiles("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("realapi")
```

**Key Dependencies:**
- `BehaviorAnalysisService` - Core analysis logic
- `BehaviorInsightsRepository` - Persistence
- `TestEventProvider` - Mock event source (implements ExternalEventProvider)
- `AICoreService` - LLM integration
- `TestRestTemplate` - API testing (for API endpoint tests)

### 4.3 Test Data Provider Pattern

**TestEventProvider Pattern (Existing):**

```java
@Component
@Profile("integration")
public class TestEventProvider implements ExternalEventProvider {
    private Map<UUID, List<ExternalEvent>> userEvents = new ConcurrentHashMap<>();
    private Queue<UserEventBatch> batchQueue = new ConcurrentLinkedQueue<>();
    
    // Targeted mode
    public void addEventsForUser(UUID userId, List<ExternalEvent> events) { ... }
    
    // Discovery/batch mode
    public void addBatchUser(UUID userId, List<ExternalEvent> events, Map<String, Object> context) { ... }
    
    // Cleanup
    public void reset() { userEvents.clear(); batchQueue.clear(); }
}
```

---

## 5. Test Specifications by Category

### 5.1 NEW TEST CLASS: BehaviorEdgeCasesRealApiIT.java

**Purpose:** Validate error handling, resilience, and edge case recovery

**Test Specifications:**

#### TC-EDGE-1: Empty Event Handling - Existing User
```yaml
Given:
  - Existing BehaviorInsights in repository (userId=X, sentiment=SATISFIED, churn=0.2)
  - TestEventProvider returns empty list for userId=X
When:
  - Call analysisService.analyzeUser(userId)
Then:
  - Returns the existing BehaviorInsights unchanged
  - No LLM call made (verify via logs or spy)
  - analyzedAt timestamp NOT updated
```

#### TC-EDGE-2: Empty Event Handling - Non-Existent User
```yaml
Given:
  - No BehaviorInsights exists for userId=Y
  - TestEventProvider returns empty list for userId=Y
When:
  - Call analysisService.analyzeUser(userId)
Then:
  - Returns null
  - No database insert occurs
  - No LLM call made
```

#### TC-EDGE-3: LLM Returns Invalid Sentiment Label
```yaml
Given:
  - User with 5 events
  - Mock LLM response: sentiment.label = "SUPER_HAPPY" (invalid enum)
When:
  - Analyze user
Then:
  - SentimentLabel.fromString() returns NEUTRAL (fallback)
  - Warning logged: "Invalid sentiment label 'SUPER_HAPPY', defaulted to NEUTRAL"
  - Analysis completes successfully with NEUTRAL sentiment
```

#### TC-EDGE-4: LLM Returns Invalid Trend
```yaml
Given:
  - User with previous analysis (baseline exists)
  - LLM response: trend = "GETTING_BETTER" (invalid)
When:
  - Analyze user
Then:
  - BehaviorTrend.fromString() returns fallback
  - Trend computed from deltas using BehaviorTrend.fromDeltas()
  - Warning logged: "Invalid trend 'GETTING_BETTER', computing from deltas"
```

#### TC-EDGE-5: Sentiment Score Clamping
```yaml
Given:
  - User with events
  - LLM response: sentiment.score = 1.8 (out of range)
When:
  - Parse LLM response
Then:
  - sentimentScore clamped to 1.0
  - No exception thrown
  - Analysis completes successfully
```

#### TC-EDGE-6: Churn Risk Clamping
```yaml
Given:
  - LLM response: churn.risk = -0.3 (invalid)
When:
  - Parse LLM response
Then:
  - churnRisk clamped to 0.0
  - No exception thrown
```

#### TC-EDGE-7: High Churn Without Reason - Generate Default
```yaml
Given:
  - LLM response: churn.risk = 0.75, churn.reason = null
When:
  - Parse LLM response
Then:
  - churnReason set to "Behavioral drift detected" (default)
  - Warning logged: "High churn risk without reason for user {userId}"
```

#### TC-EDGE-8: LLM JSON Extraction from Markdown
```yaml
Given:
  - LLM response wrapped in markdown code block:
    ```json
    {"segment": "Test", ...}
    ```
When:
  - extractJson() called
Then:
  - Extracts JSON correctly (strips markdown)
  - Parses successfully
```

#### TC-EDGE-9: LLM Response Missing Required Fields
```yaml
Given:
  - LLM response: {"segment": "Test"} (missing sentiment, churn, etc.)
When:
  - Parse LLM response
Then:
  - Uses default values (sentiment=null, churn=null)
  - Analysis completes with partial data
  - Warning logged
```

#### TC-EDGE-10: Concurrent Analysis of Same User
```yaml
Given:
  - Two threads analyze same userId simultaneously
When:
  - Both call analyzeUser(userId)
Then:
  - Only one entity created (unique constraint on userId)
  - Second analysis updates the same entity
  - No DuplicateKeyException
```

---

### 5.2 NEW TEST CLASS: BehaviorApiEndpointsRealApiIT.java

**Purpose:** Validate REST API endpoints for processing and analytics

**Test Specifications:**

#### TC-API-1: POST /api/behavior/processing/users/{userId} - Targeted Analysis
```yaml
Given:
  - User with events in TestEventProvider
When:
  - POST /api/behavior/processing/users/{userId}
Then:
  - Response: 200 OK
  - Body: BehaviorInsights JSON with all fields populated
  - userId matches request
  - Entity saved in database
```

#### TC-API-2: POST /api/behavior/processing/batch - Batch Processing
```yaml
Given:
  - 3 users in batch queue (via TestEventProvider.addBatchUser)
  - Request body: {"maxUsers": 5, "maxDurationMinutes": 2}
When:
  - POST /api/behavior/processing/batch
Then:
  - Response: 200 OK
  - Body: {"processedCount": 3, "duration": <ms>, "completed": true}
  - All 3 users analyzed and saved
```

#### TC-API-3: POST /api/behavior/processing/continuous - Start Continuous Job
```yaml
Given:
  - Request body: {"usersPerBatch": 2, "intervalMinutes": 0, "maxIterations": 1}
When:
  - POST /api/behavior/processing/continuous
Then:
  - Response: 200 OK
  - Body: {"jobId": <uuid>, "status": "RUNNING"}
  - Job executes and completes within timeout
```

#### TC-API-4: POST /api/behavior/processing/continuous/{jobId}/cancel
```yaml
Given:
  - Active continuous job with jobId=X
When:
  - POST /api/behavior/processing/continuous/{jobId}/cancel
Then:
  - Response: 200 OK
  - Body: {"jobId": X, "status": "CANCELLED"}
  - Job stops processing
```

#### TC-API-5: POST /api/behavior/processing/scheduled/pause
```yaml
When:
  - POST /api/behavior/processing/scheduled/pause
Then:
  - Response: 200 OK
  - Body: {"paused": true, "message": "..."}
  - Scheduled processing paused
```

#### TC-API-6: POST /api/behavior/processing/scheduled/resume
```yaml
Given:
  - Scheduled processing is paused
When:
  - POST /api/behavior/processing/scheduled/resume
Then:
  - Response: 200 OK
  - Body: {"paused": false, "message": "..."}
```

#### TC-API-7: GET /api/behavior/analytics/rapid-decline
```yaml
Given:
  - 2 users with RAPIDLY_DECLINING trend in DB
  - 1 user with IMPROVING trend in DB
When:
  - GET /api/behavior/analytics/rapid-decline
Then:
  - Response: 200 OK
  - Body: Array of 2 TrendAlertDTO objects
  - Each has: userId, sentiment, churnRisk, churnReason, trend, recommendations
```

#### TC-API-8: GET /api/behavior/analytics/trend-distribution
```yaml
Given:
  - 3 IMPROVING users, 2 DECLINING users, 1 STABLE user in DB
When:
  - GET /api/behavior/analytics/trend-distribution
Then:
  - Response: 200 OK
  - Body: {"IMPROVING": 3, "DECLINING": 2, "STABLE": 1}
```

#### TC-API-9: GET /api/behavior/analytics/sentiment-distribution
```yaml
Given:
  - 2 DELIGHTED, 3 SATISFIED, 1 FRUSTRATED users in DB
When:
  - GET /api/behavior/analytics/sentiment-distribution
Then:
  - Response: 200 OK
  - Body: {"DELIGHTED": 2, "SATISFIED": 3, "FRUSTRATED": 1}
```

#### TC-API-10: GET /api/behavior/analytics/users/{userId}/trend
```yaml
Given:
  - User with:
    - currentSentiment=0.6, previousSentiment=0.2 (Δ=0.4)
    - currentChurn=0.3, previousChurn=0.5 (Δ=-0.2)
    - trend=IMPROVING
When:
  - GET /api/behavior/analytics/users/{userId}/trend
Then:
  - Response: 200 OK
  - Body: UserTrendDTO with:
    - sentimentDelta: 0.4
    - churnDelta: -0.2
    - trend: "IMPROVING"
    - recommendations: [...]
```

---

### 5.3 NEW TEST CLASS: BehaviorFullModeIndexingRealApiIT.java

**Purpose:** Validate semantic search integration in FULL mode

**Test Specifications:**

#### TC-FULL-1: FULL Mode - AISearchableEntity Created After Analysis
```yaml
Given:
  - Application running in FULL mode (ai.behavior.mode=FULL)
  - User analyzed successfully
When:
  - BehaviorInsights saved via @AIProcess annotation
Then:
  - AISearchableEntity created with:
    - entityType = "behavior-insight"
    - entityId = behaviorInsights.id
    - searchableContent includes segment, patterns, sentiment, churn
  - Entity indexed in vector database
```

#### TC-FULL-2: FULL Mode - Semantic Search by Sentiment
```yaml
Given:
  - 3 users analyzed in FULL mode:
    - User A: FRUSTRATED sentiment
    - User B: FRUSTRATED sentiment
    - User C: DELIGHTED sentiment
When:
  - Execute semantic search: "Find frustrated users"
Then:
  - Returns User A and User B
  - Does NOT return User C
  - Relevance scores > 0.7
```

#### TC-FULL-3: FULL Mode - Search by Behavioral Patterns
```yaml
Given:
  - User X: patterns = ["help_seeker", "feature_explorer"]
  - User Y: patterns = ["power_user", "daily_active"]
When:
  - Search: "Users seeking help frequently"
Then:
  - Returns User X (high relevance)
  - User Y has lower relevance or not returned
```

#### TC-FULL-4: FULL Mode - Search by Churn Risk Level
```yaml
Given:
  - User A: churnRisk=0.9, churnReason="payment failures"
  - User B: churnRisk=0.15, churnReason=null
When:
  - Search: "High churn risk users"
Then:
  - Returns User A
  - User B has lower relevance
```

#### TC-FULL-5: LIGHT Mode - No Indexing Occurs
```yaml
Given:
  - Application running in LIGHT mode (ai.behavior.mode=LIGHT)
  - User analyzed successfully
When:
  - BehaviorInsights saved
Then:
  - AISearchableEntity NOT created
  - No vector indexing occurs
  - Preset config disables auto-embedding and indexing
```

#### TC-FULL-6: Searchable Content Format Validation
```yaml
Given:
  - BehaviorInsights:
    - segment="At Risk"
    - sentiment=FRUSTRATED (0.3)
    - churn=0.7 with reason="billing issues"
    - trend=DECLINING
When:
  - getSearchableContent() called
Then:
  - Returns: "Segment: At Risk. Patterns: [...]. Sentiment: FRUSTRATED (-0.30) [↓0.40]. Churn Risk: High (0.70) [⚠️↑0.20] - billing issues. Trend: Negative shift. Recommendations: [...]"
  - Contains all key dimensions for semantic search
```

---

### 5.4 NEW TEST CLASS: BehaviorPersistenceRealApiIT.java

**Purpose:** Validate persistence semantics, updates, and database operations

**Test Specifications:**

#### TC-PERSIST-1: First Analysis - Entity Created
```yaml
Given:
  - No existing BehaviorInsights for userId=X
When:
  - Analyze user X
Then:
  - New entity created with:
    - id = generated UUID
    - userId = X
    - createdAt = NOW
    - updatedAt = NOW
    - analyzedAt = NOW
    - previousSentimentScore = null
    - previousChurnRisk = null
    - trend = NEW_USER
```

#### TC-PERSIST-2: Second Analysis - Entity Updated (Not Recreated)
```yaml
Given:
  - Existing BehaviorInsights: id=ID1, createdAt=T1
When:
  - Re-analyze same user
Then:
  - Same entity updated:
    - id = ID1 (unchanged)
    - createdAt = T1 (preserved)
    - updatedAt > T1 (refreshed)
    - analyzedAt > T1 (new analysis time)
    - previousSentimentScore = old sentimentScore
    - previousChurnRisk = old churnRisk
```

#### TC-PERSIST-3: Previous Values Copying
```yaml
Given:
  - First analysis: sentiment=0.7, churn=0.2
When:
  - Second analysis: sentiment=0.5, churn=0.4
Then:
  - Result has:
    - sentimentScore = 0.5
    - churnRisk = 0.4
    - previousSentimentScore = 0.7
    - previousChurnRisk = 0.2
    - sentimentDelta = -0.2 (computed)
    - churnDelta = 0.2 (computed)
```

#### TC-PERSIST-4: Unique Constraint on userId
```yaml
Given:
  - BehaviorInsights exists for userId=X
When:
  - Attempt to create new BehaviorInsights with same userId=X
Then:
  - Update occurs instead of insert (handled by JPA merge)
  - No unique constraint violation
```

#### TC-PERSIST-5: Repository Query - findByUserId
```yaml
Given:
  - BehaviorInsights for userId=X exists
When:
  - repository.findByUserId(X)
Then:
  - Returns Optional.of(behaviorInsights)
  - Entity fully hydrated with all fields
```

#### TC-PERSIST-6: Repository Query - findRapidlyDecliningUsers
```yaml
Given:
  - 2 users with trend=RAPIDLY_DECLINING (churn=0.9, 0.7)
  - 1 user with trend=DECLINING (churn=0.5)
When:
  - repository.findRapidlyDecliningUsers()
Then:
  - Returns 2 users
  - Ordered by churnRisk DESC (0.9 first, then 0.7)
```

#### TC-PERSIST-7: Repository Query - findByTrend (Enum Type-Safety)
```yaml
Given:
  - 3 IMPROVING users, 2 STABLE users
When:
  - repository.findByTrend(BehaviorTrend.IMPROVING)
Then:
  - Returns exactly 3 users
  - All have trend=IMPROVING
```

#### TC-PERSIST-8: Repository Query - findBySentimentLabel (Enum Type-Safety)
```yaml
Given:
  - 2 FRUSTRATED users, 1 DELIGHTED user
When:
  - repository.findBySentimentLabel(SentimentLabel.FRUSTRATED)
Then:
  - Returns exactly 2 users
```

#### TC-PERSIST-9: Delta-Based Query - findIncreasingChurnRisk
```yaml
Given:
  - User A: churn=0.7, previousChurn=0.3 (Δ=0.4)
  - User B: churn=0.5, previousChurn=0.4 (Δ=0.1)
When:
  - repository.findIncreasingChurnRisk(0.2)
Then:
  - Returns only User A (delta > 0.2)
  - Ordered by delta DESC
```

#### TC-PERSIST-10: Transient Methods Return Correct Deltas
```yaml
Given:
  - Entity loaded from DB:
    - sentimentScore=0.5, previousSentimentScore=0.8
    - churnRisk=0.6, previousChurnRisk=0.3
When:
  - Call entity.getSentimentDelta()
  - Call entity.getChurnDelta()
Then:
  - getSentimentDelta() returns -0.3
  - getChurnDelta() returns 0.3
  - isChurnRiskIncreasing() returns true
  - isSentimentImproving() returns false
```

---

### 5.5 NEW TEST CLASS: BehaviorLlmQualityRealApiIT.java

**Purpose:** Validate LLM output quality, accuracy, and consistency

**Test Specifications:**

#### TC-LLM-1: Sentiment Detection Accuracy - Delighted Signals
```yaml
Given:
  - Events: upgrade_completed, referral_sent×3, positive_feedback(rating=5), daily_streak(30)
When:
  - Analyze user (real LLM call)
Then:
  - sentimentScore >= 0.6 (strong positive)
  - sentimentLabel IN [DELIGHTED, SATISFIED]
  - confidence >= 0.85
  - LLM correctly identified advocacy signals
```

#### TC-LLM-2: Sentiment Detection Accuracy - Churning Signals
```yaml
Given:
  - Events: cancellation_page_viewed×2, competitor_research, data_export, account_deletion_initiated
When:
  - Analyze user
Then:
  - sentimentScore <= 0.0 (negative)
  - sentimentLabel IN [CHURNING, FRUSTRATED]
  - churnRisk >= 0.7
  - churnReason mentions cancellation/competitor
```

#### TC-LLM-3: Churn Reason Quality - Specific and Actionable
```yaml
Given:
  - Events: payment_failed×3, billing_dispute_ticket, downgrade_viewed
When:
  - Analyze user
Then:
  - churnRisk >= 0.5
  - churnReason:
    - Length >= 20 characters (detailed)
    - Contains keywords: "payment" OR "billing" OR "price"
    - Provides actionable context
  - Example: "Multiple payment failures followed by billing dispute and downgrade exploration"
```

#### TC-LLM-4: Pattern Identification Quality
```yaml
Given:
  - Events: help_page_viewed×5, feature_attempted(success=false)×4, tutorial_watched
When:
  - Analyze user
Then:
  - patterns array NOT empty
  - patterns includes at least one of: ["help_seeker", "confused", "learning"]
  - recommendations include: "onboarding" OR "tutorial" OR "support"
```

#### TC-LLM-5: Recommendations Relevance - Frustrated User
```yaml
Given:
  - User with FRUSTRATED sentiment, churnRisk=0.65
When:
  - Analyze user
Then:
  - recommendations array size >= 2
  - recommendations contain action-oriented items
  - At least one recommendation mentions: "support" OR "resolution" OR "contact"
```

#### TC-LLM-6: Recommendations Relevance - Churning User
```yaml
Given:
  - User with CHURNING sentiment, churnRisk=0.85
When:
  - Analyze user
Then:
  - recommendations contain urgency indicators: "urgent" OR "immediate" OR "retention"
  - Should suggest proactive intervention
```

#### TC-LLM-7: Confidence Score Validation
```yaml
Given:
  - User with clear behavioral signals (7+ events, consistent pattern)
When:
  - Analyze user
Then:
  - confidence >= 0.75 (high confidence for clear patterns)
  - confidence stored in entity
```

#### TC-LLM-8: Confidence Score - Ambiguous Signals
```yaml
Given:
  - User with mixed signals (positive + negative events)
When:
  - Analyze user
Then:
  - confidence might be lower (0.5-0.7 acceptable)
  - Analysis still completes successfully
```

#### TC-LLM-9: Prompt Engineering - User Context Inclusion
```yaml
Given:
  - Batch mode with user context: {subscriptionTier: "premium", accountAge: 365}
When:
  - processNextUser() builds prompt
Then:
  - Prompt includes user context section
  - LLM considers context in analysis (e.g., premium users get different recommendations)
```

#### TC-LLM-10: Consistency - Same Events Produce Similar Results
```yaml
Given:
  - Same event set analyzed twice for different users
When:
  - Analyze User A and User B (same events)
Then:
  - sentimentLabel matches (or differs by max 1 level, e.g., SATISFIED vs NEUTRAL acceptable)
  - churnRisk within 0.2 of each other
  - segment similar or identical
Note: Some variance acceptable due to LLM non-determinism, but major differences indicate prompt issues
```

---

## 6. GitHub Action Integration

### 6.1 Workflow Configuration

**File:** `.github/workflows/integration-tests-manual.yml`

**Job:** `behavior-tests` (lines 271-333)

**Trigger:**
```yaml
workflow_dispatch:
  inputs:
    modules: ['behavior' or 'all']
    llm_provider: ['openai', 'anthropic', ...]
    embedding_provider: ['onnx', 'openai', ...]
    vector_database: ['lucene', 'pinecone', ...]
    persistence_database: ['h2', 'postgresql']
```

**Key Steps:**
```yaml
- Build AI Infrastructure Module (skip unit tests)
- Run Behavior Integration Tests (Real API):
    cd ai-infrastructure-module/integration-Testing/behavior-integration-tests
    bash run-behavior-realapi-tests.sh "$LLM:$EMBEDDING:$VECTOR_DB"
- Upload test reports (failsafe-reports)
- Publish test results
```

### 6.2 Test Execution Script

**File:** `ai-infrastructure-module/integration-Testing/behavior-integration-tests/run-behavior-realapi-tests.sh`

**Key Configuration:**
```bash
MAVEN_PROFILE="realapi"
TEST_MODULE="behavior-integration-tests"
MATRIX_SPEC="${1:-openai:onnx}"  # LLM:EMBEDDING[:VECTOR_DB]

# Maven Command
mvn -P${MAVEN_PROFILE} \
    -DforkCount=1 -DreuseForks=false \
    failsafe:integration-test failsafe:verify
```

**Environment Variables:**
- `OPENAI_API_KEY` (required for openai provider)
- `AI_INFRASTRUCTURE_LLM_PROVIDER`
- `AI_INFRASTRUCTURE_EMBEDDING_PROVIDER`
- `AI_INFRASTRUCTURE_VECTOR_DATABASE`
- `AI_INFRASTRUCTURE_PERSISTENCE_DATABASE`

### 6.3 Test Discovery Pattern

**Maven Failsafe Plugin:** `maven-failsafe-plugin:3.0.0`

**Test Inclusion:**
```xml
<includes>
    <include>**/*IT.java</include>
</includes>
```

**Active Profile:** `realapi`

**Test Location:** `src/test/java/com/ai/infrastructure/behavior/it/realapi/`

**Required Naming:**
- All test classes MUST end with `IT.java` (e.g., `BehaviorEdgeCasesRealApiIT.java`)
- All test classes MUST be annotated with `@Tag("realapi")`
- All test classes MUST be in package `com.ai.infrastructure.behavior.it.realapi`

---

## 7. Test Data Patterns

### 7.1 Event Builder Pattern

**Helper Method (in each test class):**

```java
private ExternalEvent createEvent(String type, LocalDateTime timestamp, Map<String, Object> data) {
    return ExternalEvent.builder()
        .eventType(type)
        .timestamp(timestamp)
        .eventData(data)
        .source("test")
        .build();
}
```

### 7.2 Common Event Types by Category

**Positive Signals (DELIGHTED/SATISFIED):**
```
- signup (source: web/mobile)
- profile_completed (completeness: 100%)
- feature_explored (feature: name)
- successful_action (action: name)
- upgrade_completed (plan: premium/professional)
- positive_feedback (rating: 4-5)
- referral_sent (count: N)
- daily_login (streak: N)
- team_collaboration (team_size: N)
- integration_added (integrations: N)
```

**Neutral Signals:**
```
- login (type: regular)
- feature_used (feature: name)
- page_viewed (page: name)
```

**Confused Signals:**
```
- help_page_viewed (topic: getting_started/tutorial/faq)
- feature_attempted (success: false)
- video_tutorial_watched (completion: 50%)
- help_search (query: "how to...")
```

**Frustrated Signals:**
```
- error_occurred (type: validation/payment/critical)
- feature_abandoned (feature: name, reason: error)
- support_ticket (topic: issue, priority: high)
- negative_feedback (rating: 1-2, comment: "...")
- incomplete_workflow (abandoned_step: N)
```

**Churning Signals:**
```
- login_gap (days_since_last: N)
- cancellation_page_viewed (duration_seconds: N)
- competitor_research (competitors: N)
- pricing_page_viewed (competitor_comparison: true)
- data_export (reason: migration, full_export: true)
- account_settings_accessed (section: delete_account)
- downgrade_completed (from: premium, to: free)
- payment_failed (attempts: N)
- account_deletion_initiated (status: pending)
```

### 7.3 Temporal Patterns

**Progressive Event Sequences:**

**Improvement Arc (7-14 days):**
```
Day 1:  error_occurred, help_page_viewed
Day 2:  feature_attempted (success=false)
Day 3:  support_ticket
Day 4:  tutorial_watched
Day 5:  successful_action (first success)
Day 6:  feature_explored (gaining confidence)
Day 7:  daily_login (streak starting)
Day 10: positive_feedback (rating=4)
Day 14: upgrade_viewed
```

**Decline Arc (7-14 days):**
```
Day 1:  daily_login (streak=30) [baseline]
Day 2:  successful_action [baseline]
Day 5:  login_gap (days=3)
Day 7:  error_occurred
Day 8:  support_ticket (topic: billing)
Day 10: cancellation_page_viewed
Day 12: competitor_research
Day 14: downgrade_viewed
```

### 7.4 User Context Patterns (Discovery Mode)

**Trial User:**
```java
Map.of(
    "subscriptionTier", "trial",
    "accountAge", 7,
    "location", "US",
    "signupSource", "web"
)
```

**Paid Power User:**
```java
Map.of(
    "subscriptionTier", "professional",
    "accountAge", 365,
    "totalSpent", 1200,
    "teamSize", 15
)
```

**At-Risk User:**
```java
Map.of(
    "subscriptionTier", "premium",
    "accountAge", 180,
    "paymentFailures", 2,
    "supportTicketsCount", 5
)
```

---

## 8. Success Criteria & Assertions

### 8.1 Sentiment Assertions

**DELIGHTED:**
```java
assertThat(result.getSentimentScore()).isGreaterThan(0.6);
assertThat(result.getSentimentLabel()).isIn(SentimentLabel.DELIGHTED, SentimentLabel.SATISFIED);
assertThat(result.getChurnRisk()).isLessThan(0.3);
assertThat(result.getConfidence()).isGreaterThan(0.85);
```

**SATISFIED:**
```java
assertThat(result.getSentimentScore()).isBetween(0.2, 0.8);
assertThat(result.getSentimentLabel()).isIn(SentimentLabel.SATISFIED, SentimentLabel.NEUTRAL);
assertThat(result.getChurnRisk()).isLessThan(0.5);
```

**CONFUSED:**
```java
assertThat(result.getSentimentLabel()).isIn(SentimentLabel.CONFUSED, SentimentLabel.NEUTRAL, SentimentLabel.FRUSTRATED);
assertThat(result.getRecommendations()).isNotEmpty()
    .anyMatch(rec -> rec.toLowerCase().contains("support") || 
                    rec.toLowerCase().contains("tutorial") ||
                    rec.toLowerCase().contains("onboarding"));
```

**FRUSTRATED:**
```java
assertThat(result.getSentimentScore()).isLessThan(0.2);
assertThat(result.getSentimentLabel()).isIn(SentimentLabel.FRUSTRATED, SentimentLabel.CONFUSED);
assertThat(result.getChurnRisk()).isGreaterThan(0.4);
assertThat(result.getRecommendations()).anyMatch(rec -> 
    rec.toLowerCase().contains("urgent") || 
    rec.toLowerCase().contains("resolution"));
```

**CHURNING:**
```java
assertThat(result.getSentimentScore()).isLessThan(0.0);
assertThat(result.getSentimentLabel()).isIn(SentimentLabel.CHURNING, SentimentLabel.FRUSTRATED);
assertThat(result.getChurnRisk()).isGreaterThan(0.7);
assertThat(result.getChurnReason()).isNotBlank();
assertThat(result.requiresImmediateAction()).isTrue();
```

### 8.2 Trend Assertions

**NEW_USER:**
```java
assertThat(result.getTrend()).isEqualTo(BehaviorTrend.NEW_USER);
assertThat(result.getPreviousSentimentScore()).isNull();
assertThat(result.getPreviousChurnRisk()).isNull();
assertThat(result.getSentimentDelta()).isNull();
assertThat(result.getChurnDelta()).isNull();
```

**IMPROVING / RAPIDLY_IMPROVING:**
```java
assertThat(result.getTrend()).isIn(BehaviorTrend.IMPROVING, BehaviorTrend.RAPIDLY_IMPROVING);
assertThat(result.getPreviousSentimentScore()).isNotNull();
assertThat(result.getPreviousChurnRisk()).isNotNull();

Double sentimentDelta = result.getSentimentDelta();
assertThat(sentimentDelta).isNotNull().isGreaterThan(0); // Improved

assertThat(result.isSentimentImproving()).isTrue();
```

**DECLINING / RAPIDLY_DECLINING:**
```java
assertThat(result.getTrend()).isIn(BehaviorTrend.DECLINING, BehaviorTrend.RAPIDLY_DECLINING);

Double sentimentDelta = result.getSentimentDelta();
if (sentimentDelta != null) {
    assertThat(sentimentDelta).isLessThan(0); // Worsened
}

Double churnDelta = result.getChurnDelta();
if (churnDelta != null) {
    assertThat(churnDelta).isGreaterThan(0); // Increased risk
}

assertThat(result.isChurnRiskIncreasing()).isTrue();
if (result.getTrend() == BehaviorTrend.RAPIDLY_DECLINING) {
    assertThat(result.requiresImmediateAction()).isTrue();
}
```

### 8.3 Churn Risk Assertions

**Low Risk (0.0-0.2):**
```java
assertThat(result.getChurnRisk()).isLessThan(0.3);
assertThat(result.getSentimentLabel()).isIn(SentimentLabel.DELIGHTED, SentimentLabel.SATISFIED);
```

**Moderate Risk (0.4-0.6):**
```java
assertThat(result.getChurnRisk()).isBetween(0.3, 0.7);
assertThat(result.getChurnReason()).isNotBlank();
assertThat(result.getRecommendations()).isNotEmpty();
```

**Critical Risk (0.8-1.0):**
```java
assertThat(result.getChurnRisk()).isGreaterThan(0.7);
assertThat(result.getChurnReason()).isNotBlank();
assertThat(result.getChurnReason().length()).isGreaterThan(20); // Detailed
assertThat(result.requiresImmediateAction()).isTrue();
assertThat(result.getRecommendations()).anyMatch(rec -> 
    rec.toLowerCase().contains("urgent") || 
    rec.toLowerCase().contains("save") ||
    rec.toLowerCase().contains("retention"));
```

### 8.4 Metadata Assertions

**AI Model & Processing:**
```java
assertThat(result.getAiModelUsed()).isNotBlank();
assertThat(result.getProcessingTimeMs()).isGreaterThan(0L);
assertThat(result.getModelPromptVersion()).isNotBlank();
```

**Timestamps:**
```java
assertThat(result.getCreatedAt()).isNotNull();
assertThat(result.getUpdatedAt()).isNotNull();
assertThat(result.getAnalyzedAt()).isNotNull();
assertThat(result.getUpdatedAt()).isAfterOrEqualTo(result.getCreatedAt());
```

**Confidence:**
```java
assertThat(result.getConfidence()).isNotNull()
    .isBetween(0.0, 1.0);
// For clear signals:
assertThat(result.getConfidence()).isGreaterThan(0.75);
```

### 8.5 Persistence Assertions

**Update vs Create:**
```java
// First analysis
BehaviorInsights first = analysisService.analyzeUser(userId);
UUID firstId = first.getId();
LocalDateTime firstCreatedAt = first.getCreatedAt();

// Second analysis
BehaviorInsights second = analysisService.analyzeUser(userId);

assertThat(second.getId()).isEqualTo(firstId); // Same ID (update)
assertThat(second.getCreatedAt()).isEqualTo(firstCreatedAt); // Preserved
assertThat(second.getUpdatedAt()).isAfter(first.getUpdatedAt()); // Refreshed
assertThat(second.getAnalyzedAt()).isAfter(first.getAnalyzedAt()); // New analysis
```

---

## 9. Performance & Quality Benchmarks

### 9.1 LLM Accuracy Targets

| Dimension | Target Accuracy | Measurement Method |
|-----------|----------------|-------------------|
| **Sentiment Detection** | 95%+ | Clear signal events → correct sentiment label |
| **Churn Prediction** | 90%+ | Churning events → churnRisk >0.7 |
| **Trend Classification** | 92%+ | Delta >0.4 → RAPIDLY_IMPROVING/DECLINING |
| **Pattern Identification** | 85%+ | Help-seeking events → "help_seeker" in patterns |
| **Recommendation Relevance** | 80%+ | Frustrated user → support-related recommendation |

### 9.2 Performance Targets

| Metric | Target | Notes |
|--------|--------|-------|
| **LLM Response Time** | <3s per analysis | Median, excludes retries |
| **End-to-End Analysis** | <5s per user | Including DB I/O |
| **Batch Processing** | 10+ users/min | Discovery mode throughput |
| **Confidence Score** | >0.75 for clear signals | LLM self-assessment |
| **Processing Time** | Recorded in processingTimeMs | For monitoring |

### 9.3 Quality Metrics

**Churn Reason Quality:**
- Minimum length: 20 characters
- Should reference specific events (e.g., "payment", "cancellation")
- Actionable context provided

**Recommendations Quality:**
- Minimum: 2 recommendations per analysis
- Action-oriented (verbs: "contact", "offer", "resolve")
- Severity-appropriate (frustrated → "support", churning → "urgent retention")

**Consistency (Non-Determinism Tolerance):**
- Same events analyzed twice:
  - Sentiment label: ±1 level variance acceptable
  - Churn risk: ±0.2 variance acceptable
  - Segment: Similar or identical

---

## 10. Implementation Checklist

### 10.1 Phase 1: New Test Classes (3-4 hours)

- [ ] **BehaviorEdgeCasesRealApiIT.java** (10 tests)
  - [ ] TC-EDGE-1 to TC-EDGE-10 implemented
  - [ ] All edge cases covered (empty events, invalid enums, clamping, etc.)
  - [ ] Logging validation for warnings

- [ ] **BehaviorApiEndpointsRealApiIT.java** (10 tests)
  - [ ] TC-API-1 to TC-API-10 implemented
  - [ ] All REST endpoints tested
  - [ ] Request/response validation
  - [ ] Error responses tested (404, 400, etc.)

- [ ] **BehaviorFullModeIndexingRealApiIT.java** (6 tests)
  - [ ] TC-FULL-1 to TC-FULL-6 implemented
  - [ ] FULL vs LIGHT mode differentiation
  - [ ] Semantic search validation
  - [ ] Searchable content format verification

- [ ] **BehaviorPersistenceRealApiIT.java** (10 tests)
  - [ ] TC-PERSIST-1 to TC-PERSIST-10 implemented
  - [ ] Create vs update semantics
  - [ ] Repository queries (enum-based)
  - [ ] Delta-based queries (JPQL arithmetic)
  - [ ] Transient method validation

- [ ] **BehaviorLlmQualityRealApiIT.java** (10 tests)
  - [ ] TC-LLM-1 to TC-LLM-10 implemented
  - [ ] Sentiment accuracy validation
  - [ ] Churn reason quality checks
  - [ ] Pattern identification validation
  - [ ] Consistency tests (same events → similar results)

### 10.2 Phase 2: Test Infrastructure (1-2 hours)

- [ ] **TestEventProvider enhancements**
  - [ ] Verify all event types supported
  - [ ] Batch user queue management
  - [ ] Reset functionality for cleanup

- [ ] **Common assertion helpers**
  - [ ] Sentiment assertion methods
  - [ ] Trend assertion methods
  - [ ] Churn risk assertion methods
  - [ ] Quality validation helpers (churn reason, recommendations)

- [ ] **Test data builders**
  - [ ] Event builder patterns documented
  - [ ] Temporal sequence builders (improvement arc, decline arc)
  - [ ] User context builders

### 10.3 Phase 3: Integration & Validation (2-3 hours)

- [ ] **Local execution**
  - [ ] All tests pass with `mvn -Prealapi failsafe:integration-test`
  - [ ] OPENAI_API_KEY configured
  - [ ] Provider matrix tested (openai:onnx:lucene, openai:openai:pinecone)

- [ ] **GitHub Action validation**
  - [ ] Workflow dispatched with modules='behavior'
  - [ ] All tests picked up by Failsafe
  - [ ] Test reports uploaded
  - [ ] Test results published successfully

- [ ] **Test coverage verification**
  - [ ] All 56 test scenarios (P1, P2, P3) implemented
  - [ ] Edge cases covered
  - [ ] LLM quality benchmarks validated

### 10.4 Phase 4: Documentation (1 hour)

- [ ] **Update TEST_SCENARIOS_COVERAGE.md**
  - [ ] List all new test classes
  - [ ] Map test IDs to test methods
  - [ ] Coverage percentage calculated

- [ ] **Update README_REALAPI_TESTS.md**
  - [ ] New test classes documented
  - [ ] Execution instructions updated
  - [ ] Provider matrix compatibility

- [ ] **Implementation notes**
  - [ ] Known limitations documented
  - [ ] LLM provider differences noted
  - [ ] Performance benchmarks recorded

---

## 11. Appendix

### 11.1 Test ID Mapping Template

**Format:**
```
Test ID: P1-5 (Priority 1, Scenario 5)
Test Class: BehaviorSentimentChurnRealApiIT.java
Test Method: testCriticalChurnRisk()
@DisplayName: "Churn: Critical risk (0.8-1.0) - Imminent departure signals"
```

### 11.2 Sample Test Skeleton

```java
package com.ai.infrastructure.behavior.it.realapi;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.it.BehaviorIntegrationTestApp;
import com.ai.infrastructure.behavior.it.support.TestEventProvider;
import com.ai.infrastructure.behavior.model.BehaviorTrend;
import com.ai.infrastructure.behavior.model.ExternalEvent;
import com.ai.infrastructure.behavior.model.SentimentLabel;
import com.ai.infrastructure.behavior.repository.BehaviorInsightsRepository;
import com.ai.infrastructure.behavior.service.BehaviorAnalysisService;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Test Class Purpose]
 * 
 * Tests:
 * - [Test scenario 1]
 * - [Test scenario 2]
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@SpringBootTest(
    classes = BehaviorIntegrationTestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "ai.behavior.enabled=true",
        "ai.behavior.mode=FULL",
        "spring.jpa.show-sql=false"
    }
)
@ActiveProfiles("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("realapi")
class BehaviorExampleRealApiIT {

    private static final Logger log = LoggerFactory.getLogger(BehaviorExampleRealApiIT.class);

    @Autowired
    private BehaviorAnalysisService analysisService;

    @Autowired
    private BehaviorInsightsRepository repository;

    @Autowired
    private TestEventProvider testEventProvider;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        testEventProvider.reset();
        log.info("===== Test Setup Complete =====");
    }

    @Test
    @Order(1)
    @DisplayName("[Test Description]")
    void testScenario() {
        // GIVEN: [Setup]
        UUID userId = UUID.randomUUID();
        testEventProvider.addEventsForUser(userId, List.of(
            createEvent("event_type", LocalDateTime.now().minusDays(1), Map.of("key", "value"))
        ));

        // WHEN: [Action]
        BehaviorInsights result = analysisService.analyzeUser(userId);

        // THEN: [Assertions]
        assertThat(result).isNotNull();
        // Add specific assertions...

        log.info("✅ Test passed - [Summary]");
    }

    // Helper methods
    private ExternalEvent createEvent(String type, LocalDateTime timestamp, Map<String, Object> data) {
        return ExternalEvent.builder()
            .eventType(type)
            .timestamp(timestamp)
            .eventData(data)
            .source("test")
            .build();
    }
}
```

### 11.3 Provider Compatibility Matrix

| Provider Combination | Supported | Notes |
|---------------------|-----------|-------|
| openai:onnx:lucene | ✅ | Default, fastest |
| openai:openai:lucene | ✅ | OpenAI embeddings |
| openai:onnx:pinecone | ✅ | Cloud vector DB |
| anthropic:onnx:lucene | ✅ | Claude LLM |
| azure-openai:openai:lucene | ✅ | Azure OpenAI |

### 11.4 Known Limitations

1. **LLM Non-Determinism:** Same events may produce slightly different sentiment scores (±0.1 variance acceptable)
2. **API Rate Limits:** Real API tests may be throttled; retry logic recommended
3. **Cost Considerations:** Each test makes real LLM API calls; monitor usage
4. **Execution Time:** Real API tests slower than mocked tests (3-5s per test)

---

**Document Version:** 1.0.0  
**Created:** 2024-12-28  
**Status:** ✅ Ready for Implementation  
**Author:** AI Infrastructure Team  
**Review:** Pending

---

**END OF SPECIFICATION**

