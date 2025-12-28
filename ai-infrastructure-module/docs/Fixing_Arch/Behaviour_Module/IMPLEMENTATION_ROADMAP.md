# Behavior Analytics Module - Complete Implementation Roadmap

**Version:** 1.0.0  
**Status:** Implementation Guide  
**Purpose:** Navigation document for all implementation guides

---

## 📚 DOCUMENT INDEX

This folder contains **2 main implementation documents** that should be implemented in order:

### 1️⃣ BEHAVIOR_SENTIMENT_CHURN_IMPLEMENTATION.md (v3.1.0)
**Focus:** Core entity model, domain enums, and analysis service  
**Time:** ~4-5 hours  
**Status:** ✅ Complete and ready

**What it contains:**
- ✅ Domain enums (`SentimentLabel`, `BehaviorTrend`)
- ✅ Complete `BehaviorInsights` entity with sentiment & churn analytics
- ✅ Enhanced `BehaviorAnalysisService` with LLM integration
- ✅ Repository with trend-based queries
- ✅ Complete testing strategy
- ✅ REST API for querying insights
- ✅ Monitoring setup

---

### 2️⃣ BEHAVIOR_PROCESSING_SCHEDULER_IMPLEMENTATION.md (v2.0.0)
**Focus:** Processing modes with job management & cancellation  
**Time:** ~3.5 hours  
**Status:** ✅ Complete and ready

**What it contains:**
- ✅ Configuration properties (`BehaviorProcessingProperties`)
- ✅ Scheduled worker with pause check (`BehaviorAnalysisWorker`)
- ✅ API controller with 9 endpoints (`BehaviorProcessingController`)
- ✅ Flexible batch processing
- ✅ Continuous background jobs with tracking
- ✅ **Job cancellation** for continuous jobs
- ✅ **Pause/resume** for scheduled processing
- ✅ Complete testing strategy
- ✅ Usage examples for all modes

---

## 🗺️ IMPLEMENTATION SEQUENCE

### Session 1: Core Analytics (4-5 hours)

**Document:** `BEHAVIOR_SENTIMENT_CHURN_IMPLEMENTATION.md`

```
Phase 1: Domain Model (1 hour)
├─ Create SentimentLabel enum
├─ Create BehaviorTrend enum
└─ User generates Liquibase diff

Phase 2: Entity & Repository (1 hour)
├─ Update BehaviorInsights entity
│  ├─ Add sentiment fields
│  ├─ Add churn fields
│  ├─ Add trend tracking
│  └─ Add @Transient delta methods
└─ Add repository queries

Phase 3: Service Enhancement (1.5 hours)
├─ Update BehaviorAnalysisService
│  ├─ Enhanced LLM prompts
│  ├─ Parse sentiment/churn from LLM
│  ├─ Validate and clamp values
│  └─ Manage previous values for trends
└─ Add trend alert logging

Phase 4: Testing (1 hour)
├─ Unit tests (new user, rapid decline, validation)
└─ Integration tests

Phase 5: Documentation (30 minutes)
└─ Update README with new analytics
```

**Output:** Enriched `BehaviorInsights` entity with sentiment & churn analytics

---

### Session 2: Processing & Scheduling (3.5 hours)

**Document:** `BEHAVIOR_PROCESSING_SCHEDULER_IMPLEMENTATION.md` (v2.0.0)

```
Phase 1: Configuration (30 minutes)
├─ Create BehaviorProcessingProperties
├─ Create application-behavior-processing-example.yml
└─ Update BehaviorAIAutoConfiguration (@EnableScheduling)

Phase 2: Scheduled Worker (45 minutes)
├─ Create BehaviorAnalysisWorker
├─ Implement @Scheduled method
├─ Add pause check (controller.isScheduledProcessingPaused())
├─ Add batch loop with limits
└─ Add error handling

Phase 3: API Controller (90 minutes)
├─ Create BehaviorProcessingController
├─ Add job tracking (ConcurrentHashMap)
├─ Implement POST /users/{userId}
├─ Implement POST /batch (flexible)
├─ Implement POST /continuous (with tracking)
├─ Implement DELETE /continuous/{jobId} (cancel)
├─ Implement GET /continuous/{jobId}/status
├─ Implement GET /continuous/jobs
├─ Implement POST /scheduled/pause
├─ Implement POST /scheduled/resume
├─ Implement GET /scheduled/status
└─ Create all DTOs

Phase 4: Testing (60 minutes)
├─ Unit tests for worker (with pause check)
├─ Unit tests for controller
├─ Tests for job cancellation
├─ Tests for pause/resume
└─ Integration tests
```

**Output:** Flexible processing system with job management & cancellation

---

## 📋 FINAL FILE STRUCTURE

After both sessions, you'll have:

```
ai-infrastructure-behavior/
├── src/main/java/com/ai/infrastructure/behavior/
│   ├── model/
│   │   ├── SentimentLabel.java                    [Session 1]
│   │   ├── BehaviorTrend.java                     [Session 1]
│   │   ├── ExternalEvent.java                     [Existing]
│   │   └── UserEventBatch.java                    [Existing]
│   │
│   ├── entity/
│   │   └── BehaviorInsights.java                  [Session 1 - Enhanced]
│   │
│   ├── repository/
│   │   └── BehaviorInsightsRepository.java        [Session 1 - Enhanced]
│   │
│   ├── service/
│   │   ├── BehaviorAnalysisService.java           [Session 1 - Enhanced]
│   │   └── BehaviorStorageAdapter.java            [Existing]
│   │
│   ├── worker/
│   │   └── BehaviorAnalysisWorker.java            [Session 2 - NEW]
│   │
│   ├── api/
│   │   └── BehaviorProcessingController.java      [Session 2 - NEW]
│   │
│   ├── config/
│   │   ├── BehaviorProcessingProperties.java      [Session 2 - NEW]
│   │   ├── BehaviorAIAutoConfiguration.java       [Session 2 - Update]
│   │   └── BehaviorRelationshipRegistration.java  [Existing]
│   │
│   └── spi/
│       ├── ExternalEventProvider.java             [Existing]
│       └── BehaviorInsightStore.java              [Existing]
│
└── src/main/resources/
    └── application-behavior-processing-example.yml [Session 2 - NEW]
```

---

## 🎯 CONFIGURATION DECISION MATRIX

After implementation, choose your processing mode:

| Scenario | Scheduled | API | Configuration |
|----------|-----------|-----|---------------|
| **Production (steady-state)** | ✅ Enabled | ✅ Enabled | Every 15 min, batch=100 |
| **Development/Testing** | ❌ Disabled | ✅ Enabled | Manual triggering only |
| **Initial Migration** | ❌ Disabled | ✅ Enabled | Large batches via API |
| **Low-volume System** | ✅ Enabled | ❌ Disabled | Hourly, batch=50 |
| **Rate-limited LLM** | ✅ Enabled | ❌ Disabled | Every 30 min, slow delay |

---

## 📊 PROCESSING MODES REFERENCE

### Mode 1: Scheduled Only
```yaml
ai.behavior.processing:
  scheduled-enabled: true
  schedule-cron: "0 */15 * * * *"
  api-enabled: false
```
**Use when:** Production, fully automated

---

### Mode 2: API Only
```yaml
ai.behavior.processing:
  scheduled-enabled: false
  api-enabled: true
```
**Use when:** Development, testing, manual control

---

### Mode 3: Hybrid (Recommended)
```yaml
ai.behavior.processing:
  scheduled-enabled: true
  schedule-cron: "0 0 * * * *"
  api-enabled: true
```
**Use when:** Production with manual override capability

---

## 🚀 QUICK IMPLEMENTATION STEPS

### For a New Cursor Session:

1. **Open the document:**
   ```
   BEHAVIOR_SENTIMENT_CHURN_IMPLEMENTATION.md (for Session 1)
   or
   BEHAVIOR_PROCESSING_SCHEDULER_IMPLEMENTATION.md (for Session 2)
   ```

2. **Tell Cursor:**
   ```
   "Implement the components described in this document.
   Follow the implementation checklist.
   Create all files with the code provided."
   ```

3. **Cursor will:**
   - Create all necessary files
   - Copy the provided code
   - Run tests
   - Report completion

4. **You verify:**
   - Check linter errors
   - Run tests manually if needed
   - Add to version control

---

## ✅ COMPLETION CRITERIA

### After Session 1 (Analytics):
- [ ] `SentimentLabel` enum exists
- [ ] `BehaviorTrend` enum exists
- [ ] `BehaviorInsights` has sentiment/churn fields
- [ ] `BehaviorAnalysisService` populates new fields
- [ ] Repository queries for trends work
- [ ] All tests pass

### After Session 2 (Processing):
- [ ] `BehaviorProcessingProperties` exists
- [ ] `BehaviorAnalysisWorker` exists (if scheduled enabled)
- [ ] `BehaviorProcessingController` exists (if API enabled)
- [ ] Can trigger processing via API
- [ ] Scheduled worker runs (if enabled)
- [ ] All tests pass

### Final Integration:
- [ ] Configure processing mode in `application.yml`
- [ ] Start application
- [ ] Verify scheduled processing or trigger via API
- [ ] Check logs for successful analysis
- [ ] Query for insights via API
- [ ] Verify trend detection works

---

## 📞 SUPPORT DOCUMENTS

### Additional Reference:
- `PROCESSING_MODES_USAGE.md` - Detailed usage examples for all modes
- `DATABASE_COMPATIBILITY_ADDENDUM.md` - Query compatibility details

---

**Document Version:** 1.1.0  
**Last Updated:** 2025-12-27  
**Status:** ✅ Complete Navigation Guide  
**Total Implementation Time:** 7.5-8.5 hours (both sessions)

---

## 🆕 NEW IN v1.1.0

### Enhanced Job Management (Session 2)

1. **Continuous Job Control**
   - ✅ Cancel running background jobs
   - ✅ Track job progress in real-time
   - ✅ List all active/completed jobs

2. **Scheduled Processing Control**
   - ✅ Pause scheduled worker (for maintenance)
   - ✅ Resume scheduled worker
   - ✅ Check pause/resume status

3. **Additional API Endpoints**
   - `DELETE /continuous/{jobId}` - Cancel job
   - `GET /continuous/{jobId}/status` - Job status
   - `GET /continuous/jobs` - List jobs
   - `POST /scheduled/pause` - Pause worker
   - `POST /scheduled/resume` - Resume worker
   - `GET /scheduled/status` - Check status

**Documents Updated:**
- ✅ `BEHAVIOR_PROCESSING_SCHEDULER_IMPLEMENTATION.md` → v2.0.0
- ✅ `API_QUICK_REFERENCE.md` → Created (quick reference card)

