# AI Behavior Analytics Module v2 - Architecture Philosophy

**Version:** 2.0  
**Status:** Architecture Design  
**Date:** November 2025  
**Author:** Architecture Review

---

## 📋 TABLE OF CONTENTS

1. [Overview](#overview)
2. [Module Goals](#module-goals)
3. [Architecture](#architecture)
4. [Data Flow](#data-flow)
5. [Core Components](#core-components)
6. [API Design](#api-design)
7. [Reusable Code](#reusable-code)
8. [Implementation Plan](#implementation-plan)

---

## 🎯 OVERVIEW

The **AI Behavior Analytics Module v2** is a complete redesign focusing on:

- ✅ **Non-blocking event ingestion** (return immediately)
- ✅ **Async AI-based analysis** (scheduled workers, no rules)
- ✅ **Temporary event storage** (deleted after AI processing)
- ✅ **LLM-aware insights** (semantic embeddings)
- ✅ **Fault-tolerant processing** (DB-backed state)
- ✅ **GDPR-compliant cleanup** (automatic retention)
- ✅ **Simple API** (single endpoint for analytics)

---

## 🎯 MODULE GOALS

### Primary Goals

1. **Track User Behavior Events**
   - Accept single events or batches
   - Store temporarily until processed
   - Support multiple event types (strings, no enum)

2. **AI-Based Analysis (Not Rules)**
   - Use LLM configured via ai-infrastructure-core
   - Generate insights, patterns, recommendations
   - Create semantic embeddings for search
   - Async scheduled workers (no real-time blocking)

3. **Provide Analytics to LLM**
   - REST API endpoint for user analytics
   - JSON format for LLM consumption
   - Semantic embeddings for similarity search

4. **Comply with Data Privacy**
   - Delete raw events after processing
   - Retain only AI-generated insights
   - Scheduled cleanup jobs
   - Audit trail for compliance

---

## 🏗️ ARCHITECTURE

### High-Level Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ EVENT INGESTION LAYER                                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ POST /api/behavior/events (single)                             │
│ POST /api/behavior/events/batch (batch)                        │
│                                                                 │
│ Returns: 202 Accepted (non-blocking)                           │
│ Processing: < 5ms per event                                    │
│                                                                 │
└──────────────────────────┬──────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ TEMPORARY STORAGE LAYER (TTL)                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ Table: ai_behavior_events_temp                                 │
│ Structure:                                                      │
│ ├─ id (UUID, PK)                                               │
│ ├─ user_id (UUID, indexed)                                     │
│ ├─ event_type (STRING) ← Simple, no enum                       │
│ ├─ event_data (JSON) ← flexible attributes                     │
│ ├─ source (STRING) ← web, mobile, api                          │
│ ├─ created_at (TIMESTAMP, indexed)                             │
│ ├─ processed (BOOLEAN) ← Mark for deletion                     │
│ ├─ processing_status (STRING) ← pending/processing/failed      │
│ ├─ retry_count (INT) ← 0, max 1 retry                          │
│ └─ expires_at (TIMESTAMP, TTL)                                 │
│                                                                 │
│ TTL: Until processed OR 30 days (configurable)                │
│ Indexes: (user_id, created_at), (processed, expires_at)       │
│                                                                 │
└──────────────────────────┬──────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ ASYNC WORKER LAYER (Scheduled Processing)                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ Scheduled Job (every 5 minutes)                                │
│ ├─ Query: unprocessed events from DB                           │
│ ├─ Batch: 1000 events per cycle (configurable)                │
│ ├─ Partition: By user_id (avoid conflicts)                    │
│ ├─ Processing:                                                 │
│ │  ├─ Fetch events for each user                              │
│ │  ├─ Call AIAnalyzer (LLM via ai-infrastructure-core)        │
│ │  ├─ Generate insights/patterns/recommendations              │
│ │  ├─ Create semantic embeddings                              │
│ │  ├─ Store BehaviorInsights                                  │
│ │  └─ Mark as processed=true                                  │
│ │                                                              │
│ ├─ Failure Handling:                                           │
│ │  ├─ If error: increment retry_count                         │
│ │  ├─ If retry_count < 1: retry in next cycle               │
│ │  ├─ If retry_count >= 1: move to ai_behavior_events_failed │
│ │  └─ Log for manual inspection                               │
│ │                                                              │
│ ├─ Crash Recovery:                                             │
│ │  ├─ DB tracks processing_status                             │
│ │  ├─ On restart: query processing_status='processing'       │
│ │  ├─ Reset to pending (worker crashed, retry)              │
│ │  └─ Continue from last checkpoint                           │
│ │                                                              │
│ └─ No partitioning (for now)                                   │
│                                                                 │
└──────────────────────────┬──────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ AI INSIGHTS STORAGE (Permanent)                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ Table: ai_behavior_insights                                    │
│ Structure:                                                      │
│ ├─ id (UUID, PK)                                               │
│ ├─ user_id (UUID, indexed)                                     │
│ ├─ patterns (JSON array) ← AI-generated patterns             │
│ ├─ insights (JSON object) ← Structured insights              │
│ ├─ recommendations (JSON array) ← AI recommendations          │
│ ├─ embeddings (VECTOR) ← For semantic search                 │
│ ├─ analyzed_at (TIMESTAMP)                                    │
│ ├─ ai_model_used (STRING) ← gpt-4o, local-model, etc        │
│ ├─ confidence_score (FLOAT 0-1) ← AI confidence             │
│ ├─ retention_until (TIMESTAMP) ← Deletion deadline           │
│ └─ created_at (TIMESTAMP, indexed)                            │
│                                                                 │
│ REUSE: BehaviorInsights model (adapt if needed)               │
│ Indexes: (user_id, analyzed_at DESC), (embeddings)            │
│                                                                 │
└──────────────────────────┬──────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ SEMANTIC EMBEDDING LAYER                                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ Purpose: Enable semantic search on user analytics              │
│                                                                 │
│ Process:                                                        │
│ ├─ After AI analysis completes                                │
│ ├─ Convert insights/patterns to text representation           │
│ ├─ Call embedding service (from ai-infrastructure-core)       │
│ ├─ Store vector in ai_behavior_insights.embeddings           │
│ └─ Enable: "Find similar users" queries                       │
│                                                                 │
│ REUSE: EmbeddingService from core                             │
│                                                                 │
│ Example:                                                        │
│   Insight text: "Power user, 45% engagement, recent,          │
│                  prefers mobile, high purchase value"          │
│   → embedding: [0.23, -0.45, 0.12, ..., 0.89]               │
│   → Store for similarity search                               │
│                                                                 │
└──────────────────────────┬──────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ SCHEDULED CLEANUP LAYER                                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ Cleanup Job 1: Remove Processed Events (hourly)               │
│ ├─ Query: WHERE processed=true AND expires_at < NOW()        │
│ ├─ Action: DELETE                                              │
│ ├─ Log: Count of deleted events                               │
│ └─ GDPR: Fulfills "right to erasure" for raw data            │
│                                                                 │
│ Cleanup Job 2: Archive Old Insights (weekly)                  │
│ ├─ Query: WHERE retention_until < NOW()                       │
│ ├─ Action: ARCHIVE or DELETE (based on policy)               │
│ ├─ Log: Audit trail for compliance                            │
│ └─ GDPR: Fulfills "data minimization"                        │
│                                                                 │
│ Cleanup Job 3: Recover Failed Events (daily)                  │
│ ├─ Query: processing_status='processing' AND                  │
│ │        created_at < NOW()-1hour                            │
│ ├─ Action: Reset to pending (worker crash recovery)          │
│ ├─ Log: Recovery events for monitoring                        │
│ └─ Reliability: Automatic crash recovery                      │
│                                                                 │
│ REUSE: Adapt BehaviorRetentionService                         │
│ Config: Retention policies, schedules, thresholds             │
│                                                                 │
└──────────────────────────┬──────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│ REST API LAYER (Single Endpoint for LLM)                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ Endpoint: GET /api/ai/analytics/users/{userId}               │
│                                                                 │
│ Response: {                                                     │
│   userId: "uuid",                                             │
│   insights: {                                                  │
│     segment: "power_user",                                    │
│     totalEvents: 450,                                         │
│     primaryBehaviors: ["purchase", "engagement"],             │
│     riskScore: 0.15,                                          │
│     lastActive: "2025-11-19T10:30:00Z"                       │
│   },                                                           │
│   patterns: [                                                  │
│     "high_engagement",                                        │
│     "recent_activity",                                        │
│     "mobile_preference"                                       │
│   ],                                                           │
│   recommendations: [                                           │
│     "offer_loyalty_program",                                  │
│     "personalized_content"                                    │
│   ],                                                           │
│   confidence: 0.92,                                           │
│   analyzedAt: "2025-11-19T02:30:00Z"                         │
│ }                                                              │
│                                                                 │
│ Usage in LLM Prompt:                                           │
│   "User analytics: {{insights}}"                              │
│   "Patterns: {{patterns}}"                                    │
│   "Recommended: {{recommendations}}"                          │
│                                                                 │
│ Format: JSON (simple, LLM-friendly)                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📊 DATA FLOW - DETAILED

### Flow 1: Normal Processing (Happy Path)

```
USER EVENT
  ↓
POST /api/behavior/events
  ├─ Validate event
  ├─ Generate ID (UUID)
  ├─ INSERT into ai_behavior_events_temp
  │  └─ Set: processed=false, processing_status=pending
  └─ Return 202 Accepted (< 5ms)
  ↓
[5 minutes later...]
ASYNC WORKER RUNS
  ├─ Query: SELECT * FROM ai_behavior_events_temp
  │          WHERE processed=false
  │          LIMIT 1000
  ├─ For each user_id:
  │  ├─ Collect events for user
  │  ├─ UPDATE: processing_status='processing'
  │  ├─ Call AIAnalyzer.analyze(userId, events)
  │  │  └─ LLM generates: patterns, insights, recommendations
  │  ├─ Generate embeddings
  │  ├─ INSERT into ai_behavior_insights
  │  │  └─ Store: patterns, insights, recommendations, embeddings
  │  ├─ UPDATE: processed=true
  │  └─ COMMIT
  └─ Success! Events processed
  ↓
[Hourly...]
CLEANUP JOB 1 RUNS
  ├─ Query: DELETE FROM ai_behavior_events_temp
  │          WHERE processed=true
  └─ Events deleted (raw data gone)
  ↓
USER/LLM REQUESTS ANALYTICS
  ├─ GET /api/ai/analytics/users/{userId}
  ├─ Query: ai_behavior_insights (still there!)
  └─ Return insights as JSON
```

### Flow 2: Failure Scenario

```
WORKER PROCESSING
  ├─ UPDATE: processing_status='processing'
  ├─ Call AIAnalyzer.analyze(...)
  ├─ ERROR! (LLM timeout, network error, etc)
  ├─ UPDATE: retry_count=1
  └─ ROLLBACK (no changes)
  ↓
[5 minutes later...]
WORKER RUNS AGAIN
  ├─ Query: processing_status=pending, retry_count=0
  ├─ (Events with retry_count=1 still in queue)
  ├─ Later in cycle: Process retry_count=1 events
  ├─ Call AIAnalyzer again
  ├─ Still fails? increment retry_count=2
  ├─ Insert into ai_behavior_events_failed (manual review)
  └─ Continue processing others
  ↓
[Daily...]
MANUAL REVIEW
  ├─ Query: ai_behavior_events_failed
  ├─ Investigate, fix, re-process if needed
  └─ Delete when no longer needed
```

### Flow 3: Crash Recovery

```
WORKER RUNNING
  ├─ UPDATE: processing_status='processing'
  ├─ Processing event batch...
  ├─ CRASH! (server dies mid-processing)
  └─ DB still shows: processing_status='processing'
  ↓
[1 hour later...]
RECOVERY JOB RUNS
  ├─ Query: WHERE processing_status='processing'
  │          AND created_at < NOW()-1hour
  ├─ For each "stuck" record:
  │  ├─ UPDATE: processing_status='pending'
  │  ├─ UPDATE: retry_count=0 (or increment)
  │  └─ Log recovery event
  └─ Continue processing...
  ↓
WORKER PICKS UP WHERE IT LEFT OFF
  ├─ Query: processing_status='pending'
  ├─ Processes (including recovered ones)
  └─ No data loss!
```

---

## 🔧 CORE COMPONENTS

### 1. Event Ingestion Service

```
File: ai-infrastructure-behavior/ingestion/BehaviorEventIngestionService.java

Responsibility:
├─ Accept single or batch events
├─ Validate event structure
├─ Store to temporary table
├─ Return immediately (non-blocking)
└─ Handle duplicate detection

Reuse from v1:
├─ ValidationUtil (if exists)
└─ Event schema validation logic (adapt)
```

### 2. AI Analyzer Service

```
File: ai-infrastructure-behavior/analyzer/AIAnalyzer.java

Responsibility:
├─ Accept user events + userId
├─ Call LLM (via ai-infrastructure-core)
├─ Parse LLM response
├─ Generate patterns, insights, recommendations
├─ Return structured BehaviorInsights

NEW! (Not rules-based)
├─ Uses LLM configured by core
├─ Generates semantic understanding
└─ Returns confidence scores

Dependencies:
├─ AIProviderManager (from core)
├─ ResponseParser (JSON parsing)
└─ PromptBuilder (construct LLM prompt)
```

### 3. Async Worker Service

```
File: ai-infrastructure-behavior/worker/BehaviorAnalysisWorker.java

Responsibility:
├─ Scheduled: every 5 minutes
├─ Query unprocessed events
├─ Batch process (1000 at a time)
├─ Call AIAnalyzer
├─ Store results
├─ Mark processed
├─ Handle failures with retry

Crash Recovery:
├─ Query processing_status='processing'
├─ Check timeout (> 1 hour)
├─ Reset to pending
└─ Continue from DB state

Reuse from v1:
├─ Worker pattern (if exists)
└─ Transaction management
```

### 4. Embedding Service Integration

```
File: ai-infrastructure-behavior/embedding/BehaviorAnalyticsEmbedder.java

Responsibility:
├─ After AI analysis completes
├─ Convert insights to text
├─ Call embedding service (from core)
├─ Store vectors in ai_behavior_insights
├─ Enable semantic search

Integration:
├─ EmbeddingService (from ai-infrastructure-core)
└─ Update ai_behavior_insights.embeddings

Reuse from v1:
├─ BehaviorEmbeddingService (adapt)
└─ Embedding storage schema
```

### 5. Cleanup Service

```
File: ai-infrastructure-behavior/cleanup/BehaviorAnalyticsCleanupService.java

Responsibility:
├─ Job 1: Delete processed raw events (hourly)
├─ Job 2: Archive old insights (weekly)
├─ Job 3: Recover stuck processing (daily)
├─ Log all deletions (audit trail)
└─ GDPR compliance

Reuse from v1:
├─ ADAPT: BehaviorRetentionService
├─ Scheduled job pattern
└─ Retention policy logic

Configuration:
├─ Retention policies
├─ Job schedules
└─ Thresholds
```

### 6. REST API Controller

```
File: ai-infrastructure-behavior/api/BehaviorAnalyticsController.java

Endpoints:

1. POST /api/behavior/events
   ├─ Single event ingestion
   ├─ Return 202 Accepted
   └─ Payload: { userId, eventType, eventData, source }

2. POST /api/behavior/events/batch
   ├─ Batch event ingestion
   ├─ Return 202 Accepted + batch_id
   └─ Payload: { events: [...] }

3. GET /api/ai/analytics/users/{userId}
   ├─ Retrieve user analytics
   ├─ For LLM consumption
   └─ Response: BehaviorInsights JSON
```

---

## 📡 API DESIGN - DETAILS

### Endpoint 1: Single Event Ingestion

```
POST /api/behavior/events

Request:
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "purchase",              ← String, simple
  "eventData": {
    "product_id": "prod-123",
    "amount": 99.99,
    "currency": "USD",
    "category": "electronics"
  },
  "source": "web",                      ← web, mobile, api, etc
  "timestamp": "2025-11-19T10:30:00Z"
}

Response: 202 Accepted
{
  "eventId": "550e8400-e29b-41d4-a716-446655440001",
  "status": "queued_for_analysis",
  "message": "Event accepted. Will be analyzed asynchronously."
}

Processing:
├─ ~ 2-5ms response time (non-blocking)
├─ Event stored in ai_behavior_events_temp
├─ Processed by worker in next cycle
└─ User gets analytics in GET /api/ai/analytics/users/{userId}
```

### Endpoint 2: Batch Event Ingestion

```
POST /api/behavior/events/batch

Request:
{
  "events": [
    {
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "eventType": "view",
      "eventData": { ... }
    },
    {
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "eventType": "click",
      "eventData": { ... }
    },
    ...
  ]
}

Response: 202 Accepted
{
  "batchId": "batch-550e8400",
  "totalEvents": 500,
  "acceptedEvents": 500,
  "status": "queued_for_analysis"
}

Processing:
├─ Batch stored in single transaction
├─ Status tracked by batch_id (optional)
└─ Events processed individually by worker
```

### Endpoint 3: Get User Analytics (LLM-Ready)

```
GET /api/ai/analytics/users/{userId}

Query Parameters (optional):
├─ ?latest=true        ← Get most recent analysis
├─ ?format=json        ← Response format
└─ ?include_vectors=false  ← Exclude embeddings (huge)

Response: 200 OK
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "analytics": {
    "insights": {
      "segment": "power_user",
      "behaviorScore": 0.87,
      "engagementLevel": "high",
      "purchaseFrequency": "weekly",
      "averageOrderValue": 150.50,
      "preferredChannel": "mobile",
      "lastActiveAt": "2025-11-19T10:30:00Z",
      "riskOfChurn": 0.05
    },
    "patterns": [
      "high_engagement",
      "recent_activity",
      "mobile_preference",
      "purchase_consistent"
    ],
    "recommendations": [
      "offer_premium_membership",
      "suggest_related_products",
      "provide_exclusive_deals"
    ],
    "aiModel": "gpt-4o",
    "confidence": 0.92,
    "analyzedAt": "2025-11-19T02:30:00Z",
    "validUntil": "2025-11-20T02:30:00Z"
  }
}

FOR LLM CONSUMPTION:

Example prompt integration:
  "User {{userId}} has these behavioral insights:
   Segment: {{insights.segment}}
   Patterns: {{patterns}}
   Recommendations: {{recommendations}}
   
   Based on this, suggest next best action..."

Format: ✅ JSON (simple, LLM-friendly, no XML/YAML)
```

---

## 📁 REUSABLE CODE FROM v1 MODULE

### Models to REUSE/ADAPT

```
✅ REUSE AS-IS:

1. BehaviorSignal
   Location: ai-infrastructure-behavior/model/BehaviorSignal.java
   Usage: Base model for events
   Changes: None needed

2. BehaviorInsights  
   Location: ai-infrastructure-behavior/model/BehaviorInsights.java
   Usage: Store AI-generated insights
   Changes: Add fields:
   ├─ embeddings (VECTOR)
   ├─ aiModel (STRING)
   ├─ confidence (DOUBLE)
   └─ retentionUntil (TIMESTAMP)

🔄 ADAPT FROM v1:

1. BehaviorRetentionService
   Location: ai-infrastructure-behavior/retention/BehaviorRetentionService.java
   Current: Simple retention logic
   New Usage: Full cleanup orchestration
   Changes:
   ├─ Add Job 1: Delete processed raw events
   ├─ Add Job 2: Archive old insights
   ├─ Add Job 3: Recover stuck processing
   └─ Add comprehensive logging

2. BehaviorEmbeddingService
   Location: ai-infrastructure-behavior/service/BehaviorEmbeddingService.java
   Current: Embedding generation
   New Usage: Integrate with AI insights
   Changes:
   ├─ Embed insights text (not just text fields)
   ├─ Store in ai_behavior_insights.embeddings
   └─ Add similarity search queries

3. Storage/Repository Layer
   Location: ai-infrastructure-behavior/storage/
   Current: BehaviorSignalRepository
   New Usage: Add repositories:
   ├─ BehaviorEventTemporaryRepository (ai_behavior_events_temp)
   ├─ BehaviorAnalyticsInsightsRepository (ai_behavior_insights)
   └─ BehaviorEventFailedRepository (ai_behavior_events_failed)

❌ DO NOT REUSE:

1. PatternAnalyzer
   Current: Rules-based (if engagement >= 0.75...)
   New: AI-based (LLM generates patterns)
   Action: REPLACE, not adapt

2. SegmentationAnalyzer
   Current: Hardcoded segmentation logic
   New: AI generates segments
   Action: REPLACE with AIAnalyzer
```

---

## 🚀 IMPLEMENTATION PLAN

### Phase 1: Data Layer (3 days)

```
Tasks:
├─ [ ] Create ai_behavior_events_temp table
├─ [ ] Create ai_behavior_insights table (adapt from v1)
├─ [ ] Create ai_behavior_events_failed table
├─ [ ] Create repositories (3 new repos)
├─ [ ] Add indexes (6 indexes total)
└─ [ ] Create migration scripts

Dependencies: None (DB only)
Reuse: Storage patterns from v1

Code Files:
├─ db/changelog/V001__create_behavior_tables.sql
├─ storage/BehaviorEventTemporaryRepository.java
├─ storage/BehaviorAnalyticsInsightsRepository.java
└─ storage/BehaviorEventFailedRepository.java
```

### Phase 2: API Layer (2 days)

```
Tasks:
├─ [ ] Create BehaviorEventIngestionService
├─ [ ] Create BehaviorAnalyticsController
│  ├─ POST /api/behavior/events
│  ├─ POST /api/behavior/events/batch
│  └─ GET /api/ai/analytics/users/{userId}
├─ [ ] Add request validation
└─ [ ] Add response serialization (JSON)

Dependencies: Phase 1 (data layer)
Reuse: Validation from v1

Code Files:
├─ api/BehaviorAnalyticsController.java
├─ ingestion/BehaviorEventIngestionService.java
├─ dto/BehaviorEventRequest.java
└─ dto/BehaviorAnalyticsResponse.java
```

### Phase 3: AI Integration (3 days)

```
Tasks:
├─ [ ] Create AIAnalyzer service
├─ [ ] Integrate with ai-infrastructure-core (AIProviderManager)
├─ [ ] Create prompt template for LLM
├─ [ ] Create response parser (JSON)
├─ [ ] Add error handling + retry logic
└─ [ ] Test with sample events

Dependencies: Phase 1 (data), ai-infrastructure-core (LLM)
Reuse: NONE (new AI-based approach)

Code Files:
├─ analyzer/AIAnalyzer.java
├─ analyzer/PromptBuilder.java
├─ analyzer/LLMResponseParser.java
└─ config/AIAnalyzerProperties.java
```

### Phase 4: Async Worker (3 days)

```
Tasks:
├─ [ ] Create BehaviorAnalysisWorker
├─ [ ] Implement @Scheduled(fixedRate=300000)
├─ [ ] Batch processing (1000 events/cycle)
├─ [ ] Error handling + retry logic
├─ [ ] Crash recovery logic
└─ [ ] Add monitoring/metrics

Dependencies: Phase 3 (AI)
Reuse: Worker patterns from v1

Code Files:
├─ worker/BehaviorAnalysisWorker.java
├─ worker/WorkerHealthMonitor.java
└─ config/WorkerProperties.java
```

### Phase 5: Embedding Integration (2 days)

```
Tasks:
├─ [ ] Adapt BehaviorEmbeddingService
├─ [ ] Embed insights after AI analysis
├─ [ ] Store vectors in ai_behavior_insights
├─ [ ] Add similarity search capability
└─ [ ] Test with sample data

Dependencies: Phase 3 (AI) + Phase 4 (Worker)
Reuse: BehaviorEmbeddingService from v1

Code Files:
├─ embedding/BehaviorAnalyticsEmbedder.java
└─ embedding/EmbeddingStorageService.java
```

### Phase 6: Cleanup Layer (2 days)

```
Tasks:
├─ [ ] Adapt BehaviorRetentionService
├─ [ ] Implement Job 1: Delete processed events
├─ [ ] Implement Job 2: Archive old insights
├─ [ ] Implement Job 3: Crash recovery
├─ [ ] Add comprehensive logging
└─ [ ] Add audit trail

Dependencies: Phase 1 (data layer)
Reuse: BehaviorRetentionService, retention logic

Code Files:
├─ cleanup/BehaviorAnalyticsCleanupService.java
├─ cleanup/ProcessedEventCleanupJob.java
├─ cleanup/InsightArchivalJob.java
└─ cleanup/CrashRecoveryJob.java
```

### Phase 7: Testing & Integration (3 days)

```
Tasks:
├─ [ ] Unit tests (services)
├─ [ ] Integration tests (full flow)
├─ [ ] Performance tests (1M events/day)
├─ [ ] Failure scenario tests
├─ [ ] Documentation
└─ [ ] Demo

Dependencies: All phases
Tests:
├─ BehaviorAnalysisWorkerTest.java
├─ AIAnalyzerIntegrationTest.java
├─ EndToEndFlowTest.java
└─ CrashRecoveryTest.java
```

---

## 📋 CONFIGURATION

```properties
# application.yml

ai:
  behavior:
    ingestion:
      max-batch-size: 1000
      duplicate-detection: true
      
    worker:
      enabled: true
      schedule: "0 */5 * * * *"     # Every 5 minutes
      batch-size: 1000
      max-retries: 1
      
    storage:
      temp-ttl-hours: 24            # Delete after 24h
      temp-retention-after-process: false  # Delete when processed
      
    embedding:
      enabled: true
      model: "all-MiniLM-L6-v2"
      batch-size: 100
      
    cleanup:
      job1-schedule: "0 0 * * * *"  # Every hour
      job2-schedule: "0 0 0 * * 0"  # Weekly
      job3-schedule: "0 0 * * * *"  # Every hour
      
    retention:
      insights-retention-days: 90
      failed-events-retention-days: 30
      
    ai-provider:
      type: "gpt-4o"                # From ai-infrastructure-core
      fallback: "local-onnx"
```

---

## 🎯 KEY DESIGN DECISIONS

| Decision | Rationale | Impact |
|----------|-----------|--------|
| **Async Workers** | Scale + non-blocking | 5min latency acceptable |
| **Temp Storage TTL** | GDPR compliance + cost | Raw events deleted after 24h |
| **AI-based (not rules)** | Better insights + semantic | Requires LLM provider |
| **DB-backed state** | Crash recovery | Single worker bottleneck (for now) |
| **Event types as strings** | Flexibility | No validation (mitigated by schema) |
| **Single analytics endpoint** | LLM simplicity | Limited querying |
| **Retry once then fail** | Simplicity | Some events lost (mitigated by monitoring) |
| **No partitioning** | MVP scope | Won't scale to 100M events/day yet |

---

## ✅ DONE CORRECTLY

vs v1:

| Aspect | v1 (Rules) | v2 (AI-Based) |
|--------|-----------|---------------|
| **Analysis** | Synchronous | ✅ Async |
| **AI** | Rules-based | ✅ LLM-based |
| **Storage** | Permanent | ✅ Temp + permanent |
| **Cleanup** | None | ✅ Automated + GDPR |
| **Failure Handling** | None | ✅ Retry + recovery |
| **API** | Multiple endpoints | ✅ Single endpoint |
| **Scalability** | Limited | ⏳ Medium (improved) |
| **LLM Integration** | Optional | ✅ Native |

---

## 📝 SUMMARY

**v2 Module delivers:**

✅ Non-blocking event ingestion (< 5ms response)
✅ Async LLM-based analysis (configurable schedules)
✅ GDPR-compliant data lifecycle (temporary → delete)
✅ Semantic embeddings for intelligent search
✅ Fault-tolerant processing (crash recovery)
✅ Simple REST API for LLM consumption
✅ Comprehensive cleanup & auditing

**Effort: 2-3 weeks, team of 2**

**Risks:**
- ⚠️ LLM provider dependency (rate limits, costs)
- ⚠️ Worker bottleneck at 100M+ events/day
- ⚠️ Retry-once loses some events (needs monitoring)

**Future Improvements:**
- 🔮 Partitioning by user_id for scale
- 🔮 Multiple worker instances
- 🔮 Event streaming (Kafka) instead of polling
- 🔮 Real-time notifications on important patterns


