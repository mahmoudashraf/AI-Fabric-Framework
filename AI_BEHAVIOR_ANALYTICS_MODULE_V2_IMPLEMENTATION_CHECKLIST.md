# v2 Module Implementation Checklist

**Start Date:** TBD  
**Target Completion:** +21 days (3 weeks)  
**Team Size:** 2 developers  
**Status:** Ready for Implementation

---

## 📊 PHASE 1: Data Layer (3 days)

### Database Schema

- [ ] Create `ai_behavior_events_temp` table
  - [ ] Columns: id, user_id, event_type, event_data, source, created_at, processed, processing_status, retry_count, expires_at
  - [ ] Primary key: id
  - [ ] Indexes: (user_id, created_at), (processed, expires_at)
  - [ ] TTL/Partitioning: Enable on expires_at

- [ ] Create `ai_behavior_insights` table
  - [ ] Columns: id, user_id, patterns, insights, recommendations, embeddings, analyzed_at, ai_model_used, confidence_score, retention_until, created_at
  - [ ] **REUSE:** BehaviorInsights model from v1, add new fields
  - [ ] Indexes: (user_id, analyzed_at DESC), (embeddings) for vector search

- [ ] Create `ai_behavior_events_failed` table
  - [ ] Columns: id, user_id, event_type, event_data, error_reason, retry_count, created_at, manual_review_required
  - [ ] Indexes: (user_id, created_at), (manual_review_required)

- [ ] Migration scripts
  - [ ] File: `db/changelog/V001__create_behavior_analytics_tables.sql`
  - [ ] Include: DDL for 3 tables, indexes, constraints

### Repository Layer

- [ ] Create `BehaviorEventTemporaryRepository`
  - [ ] Methods: findUnprocessed(), markAsProcessed(), countUnprocessed()
  - [ ] Extends: JpaRepository<BehaviorEventTemp, UUID>

- [ ] Create `BehaviorAnalyticsInsightsRepository`
  - [ ] Methods: findLatestByUserId(), findBySimilarityToVector(), saveInsight()
  - [ ] Extends: JpaRepository<BehaviorInsights, UUID>

- [ ] Create `BehaviorEventFailedRepository`
  - [ ] Methods: findFailedEvents(), saveFailedEvent()
  - [ ] Extends: JpaRepository<FailedEvent, UUID>

- [ ] Entity classes
  - [ ] BehaviorEventTemp.java
  - [ ] BehaviorInsights.java (adapt from v1)
  - [ ] FailedEvent.java

**Code Location:** `ai-infrastructure-behavior/storage/`

---

## 📡 PHASE 2: API Layer (2 days)

### REST Controller

- [ ] Create `BehaviorAnalyticsController`
  - [ ] POST `/api/behavior/events` (single)
    - [ ] Validate request
    - [ ] Generate event ID
    - [ ] Store in temp table
    - [ ] Return 202 Accepted
    - [ ] Response time target: < 5ms

  - [ ] POST `/api/behavior/events/batch` (batch)
    - [ ] Validate batch
    - [ ] Store all events (transaction)
    - [ ] Return 202 Accepted + batch_id
    
  - [ ] GET `/api/ai/analytics/users/{userId}` (LLM endpoint)
    - [ ] Query latest insights
    - [ ] Include patterns, recommendations, insights
    - [ ] Response format: JSON
    - [ ] Add query params: ?latest=true, ?include_vectors=false

**Code Location:** `ai-infrastructure-behavior/api/`

### DTOs

- [ ] Create `BehaviorEventRequest.java`
  - [ ] Fields: userId, eventType, eventData, source, timestamp

- [ ] Create `BehaviorEventBatchRequest.java`
  - [ ] Fields: events (List<BehaviorEventRequest>)

- [ ] Create `BehaviorAnalyticsResponse.java`
  - [ ] Fields: userId, analytics, patterns, recommendations, confidence, analyzedAt

- [ ] Create `BehaviorEventIngestResponse.java`
  - [ ] Fields: eventId, status, message

**Code Location:** `ai-infrastructure-behavior/api/dto/`

### Error Handling

- [ ] Create `BehaviorAnalyticsExceptionHandler.java`
  - [ ] Handle validation errors (400)
  - [ ] Handle not found (404)
  - [ ] Handle internal errors (500)

---

## 🤖 PHASE 3: AI Integration (3 days)

### AI Analyzer Service

- [ ] Create `AIAnalyzer.java`
  - [ ] Method: analyze(userId, events) → BehaviorInsights
  - [ ] Dependency: AIProviderManager (from ai-infrastructure-core)
  - [ ] Error handling: Throws AIAnalysisException

- [ ] Create `PromptBuilder.java`
  - [ ] Method: buildAnalysisPrompt(events) → String
  - [ ] Template: Structure for LLM to generate patterns, insights, recommendations

- [ ] Create `LLMResponseParser.java`
  - [ ] Method: parseResponse(llmResponse) → AnalysisResult
  - [ ] Extract: patterns, insights, recommendations, confidence
  - [ ] Validate: JSON schema, required fields

### Configuration

- [ ] Create `AIAnalyzerProperties.java`
  - [ ] Fields: aiProvider, model, temperature, maxTokens, timeout

- [ ] Add to `application.yml`
  ```yaml
  ai.behavior.ai-provider:
    type: "gpt-4o"
    fallback: "local-onnx"
    timeout: 30000
  ```

**Code Location:** `ai-infrastructure-behavior/analyzer/`

### Testing

- [ ] Unit test: `AIAnalyzerTest.java`
  - [ ] Test: Valid LLM response parsing
  - [ ] Test: Error handling
  - [ ] Test: Timeout handling

---

## ⚙️ PHASE 4: Async Worker (3 days)

### Worker Service

- [ ] Create `BehaviorAnalysisWorker.java`
  - [ ] @Scheduled(fixedRate = 300000) // 5 minutes
  - [ ] Method: processEvents()
    - [ ] Query: unprocessed events (limit 1000)
    - [ ] For each user_id:
      - [ ] UPDATE: processing_status='processing'
      - [ ] Call: AIAnalyzer.analyze()
      - [ ] INSERT: ai_behavior_insights
      - [ ] UPDATE: processed=true
      - [ ] COMMIT
    - [ ] Handle errors: increment retry_count or move to failed

- [ ] Create `CrashRecoveryJob.java`
  - [ ] @Scheduled(cron = "0 0 * * * *") // Every hour
  - [ ] Method: recoverStuckProcessing()
    - [ ] Query: processing_status='processing' AND created_at < 1 hour ago
    - [ ] For each stuck record:
      - [ ] UPDATE: processing_status='pending'
      - [ ] Log: Recovery event

### Error Handling

- [ ] Implement retry logic
  - [ ] On error: increment retry_count
  - [ ] If retry_count >= 1: move to failed_events table
  - [ ] Log: error_reason, stack trace

### Monitoring

- [ ] Add metrics
  - [ ] Counter: events_processed, events_failed
  - [ ] Gauge: queue_size
  - [ ] Timer: processing_duration

**Code Location:** `ai-infrastructure-behavior/worker/`

### Testing

- [ ] Unit test: `BehaviorAnalysisWorkerTest.java`
  - [ ] Test: Normal processing
  - [ ] Test: Error handling
  - [ ] Test: Crash recovery
  - [ ] Test: Retry logic

---

## 🔍 PHASE 5: Embedding Integration (2 days)

### Embedding Service

- [ ] Adapt `BehaviorAnalyticsEmbedder.java` (from v1 BehaviorEmbeddingService)
  - [ ] Method: embedInsights(insights) → vector
  - [ ] Dependency: EmbeddingService (from ai-infrastructure-core)
  - [ ] Process:
    - [ ] Convert insights to text representation
    - [ ] Call embedding service
    - [ ] Store vector in ai_behavior_insights

- [ ] Create `EmbeddingStorageService.java`
  - [ ] Method: storeEmbedding(insightId, vector)
  - [ ] Query method: findSimilarUsers(vector, limit) → List<BehaviorInsights>

### Integration with Worker

- [ ] In BehaviorAnalysisWorker, after INSERT insights:
  - [ ] [ ] Call EmbeddingStorageService.storeEmbedding()
  - [ ] [ ] Handle embedding errors (don't block insights storage)

**Code Location:** `ai-infrastructure-behavior/embedding/`

### Testing

- [ ] Unit test: `BehaviorAnalyticsEmbedderTest.java`
  - [ ] Test: Embedding generation
  - [ ] Test: Vector storage
  - [ ] Test: Similarity search

---

## 🧹 PHASE 6: Cleanup Layer (2 days)

### Cleanup Service (Adapt from v1)

- [ ] Adapt `BehaviorRetentionService.java`
  - [ ] REUSE: Retention logic from v1
  - [ ] ADD: Three cleanup jobs

### Job 1: Delete Processed Events

- [ ] Create `ProcessedEventCleanupJob.java`
  - [ ] @Scheduled(cron = "0 0 * * * *") // Every hour
  - [ ] Method: deleteProcessedEvents()
    - [ ] Query: WHERE processed=true AND expires_at < NOW()
    - [ ] DELETE
    - [ ] Log: count of deleted events
    - [ ] Audit: Log deletion for compliance

### Job 2: Archive Old Insights

- [ ] Create `InsightArchivalJob.java`
  - [ ] @Scheduled(cron = "0 0 0 * * 0") // Weekly
  - [ ] Method: archiveOldInsights()
    - [ ] Query: WHERE retention_until < NOW()
    - [ ] Archive or DELETE (based on policy)
    - [ ] Log: archive operations
    - [ ] Audit: Compliance trail

### Job 3: Crash Recovery (already in Phase 4)

- [ ] Link: CrashRecoveryJob (from Phase 4)

### Audit & Logging

- [ ] Create `AuditLogger.java`
  - [ ] Method: logDeletion(event_id, reason)
  - [ ] Store: audit_log table (separate from normal logs)
  - [ ] Immutable: Never delete audit logs

**Code Location:** `ai-infrastructure-behavior/cleanup/`

### Configuration

- [ ] Create `CleanupProperties.java`
  - [ ] Fields: insightRetentionDays, failedEventsRetentionDays

- [ ] Add to `application.yml`
  ```yaml
  ai.behavior.cleanup:
    insights-retention-days: 90
    failed-events-retention-days: 30
    job1-schedule: "0 0 * * * *"
    job2-schedule: "0 0 0 * * 0"
    job3-schedule: "0 0 * * * *"
  ```

### Testing

- [ ] Unit test: `CleanupJobsTest.java`
  - [ ] Test: Event deletion
  - [ ] Test: Insight archival
  - [ ] Test: Crash recovery

---

## 🧪 PHASE 7: Testing & Integration (3 days)

### Unit Tests

- [ ] `BehaviorEventIngestionServiceTest.java`
  - [ ] Test: Single event storage
  - [ ] Test: Batch event storage
  - [ ] Test: Duplicate detection

- [ ] `BehaviorAnalyticsControllerTest.java`
  - [ ] Test: POST /events (202 response)
  - [ ] Test: POST /events/batch (202 response)
  - [ ] Test: GET /analytics/{userId} (200 response)

- [ ] `AIAnalyzerTest.java`
  - [ ] Test: LLM response parsing
  - [ ] Test: Error handling
  - [ ] Mock: AIProviderManager

- [ ] `BehaviorAnalysisWorkerTest.java`
  - [ ] Test: Batch processing
  - [ ] Test: Error + retry
  - [ ] Test: Crash recovery

### Integration Tests

- [ ] `EndToEndFlowTest.java`
  - [ ] Ingest events
  - [ ] Worker processes (skip scheduled, call directly)
  - [ ] Query analytics endpoint
  - [ ] Verify: insights in response

- [ ] `FailureScenarioTest.java`
  - [ ] Simulate LLM failure
  - [ ] Verify: retry mechanism
  - [ ] Verify: event moved to failed queue

- [ ] `CrashRecoveryTest.java`
  - [ ] Set events to processing_status='processing'
  - [ ] Call recovery job
  - [ ] Verify: events reset to pending
  - [ ] Verify: no data loss

### Performance Tests

- [ ] `LoadTest.java`
  - [ ] Ingest: 1000 events/second
  - [ ] Measure: response times
  - [ ] Target: < 5ms per event

- [ ] `ProcessingThroughputTest.java`
  - [ ] Process: 1M events/day scenario
  - [ ] Measure: worker throughput
  - [ ] Target: complete in 5 minutes

### Documentation

- [ ] API documentation (Swagger)
  - [ ] Endpoints with examples
  - [ ] Request/response schemas

- [ ] Architecture diagram
  - [ ] Update module diagram

- [ ] Deployment guide
  - [ ] Database setup
  - [ ] Configuration
  - [ ] Running workers

### Demo

- [ ] Create demo script
  - [ ] Ingest sample events
  - [ ] Query analytics
  - [ ] Show patterns/insights

---

## 📋 CODE FILE CHECKLIST

### New Files to Create

```
ai-infrastructure-behavior/
│
├─ api/
│  ├─ BehaviorAnalyticsController.java
│  ├─ dto/
│  │  ├─ BehaviorEventRequest.java
│  │  ├─ BehaviorEventBatchRequest.java
│  │  ├─ BehaviorAnalyticsResponse.java
│  │  └─ BehaviorEventIngestResponse.java
│  └─ exception/
│     └─ BehaviorAnalyticsExceptionHandler.java
│
├─ ingestion/
│  └─ BehaviorEventIngestionService.java
│
├─ analyzer/
│  ├─ AIAnalyzer.java
│  ├─ PromptBuilder.java
│  ├─ LLMResponseParser.java
│  └─ config/AIAnalyzerProperties.java
│
├─ worker/
│  ├─ BehaviorAnalysisWorker.java
│  ├─ CrashRecoveryJob.java
│  └─ WorkerHealthMonitor.java
│
├─ embedding/
│  ├─ BehaviorAnalyticsEmbedder.java
│  └─ EmbeddingStorageService.java
│
├─ cleanup/
│  ├─ BehaviorAnalyticsCleanupService.java
│  ├─ ProcessedEventCleanupJob.java
│  ├─ InsightArchivalJob.java
│  └─ AuditLogger.java
│
├─ storage/
│  ├─ BehaviorEventTemporaryRepository.java
│  ├─ BehaviorAnalyticsInsightsRepository.java
│  ├─ BehaviorEventFailedRepository.java
│  ├─ entity/
│  │  ├─ BehaviorEventTemp.java
│  │  ├─ BehaviorInsights.java (adapt from v1)
│  │  └─ FailedEvent.java
│
├─ config/
│  └─ BehaviorAnalyticsProperties.java
│
└─ test/
   ├─ BehaviorEventIngestionServiceTest.java
   ├─ BehaviorAnalyticsControllerTest.java
   ├─ AIAnalyzerTest.java
   ├─ BehaviorAnalysisWorkerTest.java
   ├─ EndToEndFlowTest.java
   ├─ FailureScenarioTest.java
   ├─ CrashRecoveryTest.java
   ├─ LoadTest.java
   └─ ProcessingThroughputTest.java
```

### Database Migrations

```
db/
└─ changelog/
   ├─ V001__create_behavior_analytics_tables.sql
   ├─ V002__create_indexes.sql
   └─ V003__create_audit_log_table.sql
```

### Configuration Files

```
resources/
├─ application.yml (update)
├─ application-dev.yml (add)
├─ application-prod.yml (add)
└─ application-test.yml (add)
```

---

## ⏱️ TIMELINE

| Phase | Tasks | Days | Start | End |
|-------|-------|------|-------|-----|
| 1 | Data Layer | 3 | Day 1 | Day 3 |
| 2 | API Layer | 2 | Day 4 | Day 5 |
| 3 | AI Integration | 3 | Day 6 | Day 8 |
| 4 | Async Worker | 3 | Day 9 | Day 11 |
| 5 | Embedding | 2 | Day 12 | Day 13 |
| 6 | Cleanup | 2 | Day 14 | Day 15 |
| 7 | Testing | 3 | Day 16 | Day 18 |
| - | Buffer/Review | 3 | Day 19 | Day 21 |

**Total: 21 days (3 weeks)**

---

## 👥 TEAM ASSIGNMENT (2 devs)

### Developer 1: Backend/Core
- Phase 1: Data Layer ✅
- Phase 3: AI Integration ✅
- Phase 6: Cleanup ✅
- Phase 7: Testing (unit) ✅

### Developer 2: API/Integration
- Phase 2: API Layer ✅
- Phase 4: Async Worker ✅
- Phase 5: Embedding ✅
- Phase 7: Testing (integration) ✅

### Parallel Work
- Days 1-3: Phase 1 (both can work on different tables)
- Days 4-5: Phase 2 (parallel: DTO + Controller)
- Days 6-8: Phase 3 (parallel: Analyzer + Config)

---

## ✅ DEFINITION OF DONE (Per Phase)

**Phase Complete when:**
- [ ] All code written and committed
- [ ] All unit tests passing
- [ ] Code review approved
- [ ] No linting errors
- [ ] Integration tests with next phase passing

**Project Complete when:**
- [ ] All 7 phases done
- [ ] Integration tests passing
- [ ] Performance tests met
- [ ] Documentation complete
- [ ] Demo executed successfully
- [ ] PR merged to main

---

## 🚨 RISKS & MITIGATIONS

| Risk | Impact | Mitigation |
|------|--------|-----------|
| LLM rate limits | Delayed processing | Queue overflow handling + retry |
| LLM cost overrun | Budget exceeded | Cost monitoring + limits |
| Worker bottleneck | Can't scale to 100M/day | Phased: partitioning in v2.1 |
| Events lost on failure | Data loss | Retry + manual review queue |
| Embedding failures | Search broken | Graceful fallback + retry |
| DB performance | Slow queries | Index optimization + monitoring |

---

## 🎉 SUCCESS CRITERIA

✅ **Functional:**
- Events ingested in < 5ms
- Analytics available 5 minutes after event
- LLM insights generated correctly
- Cleanup removes 95% of old data
- No data loss on worker crash

✅ **Performance:**
- Process 1M events/day in worker windows
- Average insight generation < 10 seconds
- Query analytics < 100ms

✅ **Reliability:**
- 99.5% uptime
- Zero data loss
- Crash recovery works

✅ **Compliance:**
- GDPR-compliant data deletion
- Audit trail complete
- No raw data after 24h

---

## 📞 SUPPORT

- Blockers: Flag immediately
- Code review: Daily
- Integration tests: After each phase
- Documentation: Continuous


