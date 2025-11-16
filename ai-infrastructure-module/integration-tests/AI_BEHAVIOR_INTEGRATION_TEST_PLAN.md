# AI Behavior Integration Test Plan

**Version:** 0.1  
**Date:** 2025-11-16  
**Owner:** AI Infrastructure Team  
**Applies to:** `ai-behavior-module` & `ai-infrastructure-module/integration-tests`

---

## 1. Purpose & Scope

- Guarantee the new `ai-behavior-module` works end-to-end when wired through the Spring Boot auto-configuration, Liquibase migrations, async workers, and REST APIs.
- Cover real infrastructure pieces (PostgreSQL via Testcontainers, Liquibase changelog, Spring events, caching) — no mocks, no stubs.
- Provide at least **10 high-value integration tests** to be implemented inside the existing `integration-tests` Maven module.

---

## 2. Environment & Tooling

| Component | Requirement |
|-----------|-------------|
| Runtime | Java 21, Maven 3.8+, Spring Boot 3.2+ |
| Database | PostgreSQL Testcontainer (server 15+) |
| Schema | Liquibase changelog `db/changelog/behavior/db.changelog-master.yaml` executed automatically via `SpringLiquibase` |
| Caching | Embedded Redis (if available) or Spring Simple cache (fallback) |
| Messaging | Spring application events (`BehaviorEventIngested`, `BehaviorEventBatchIngested`) |
| Observability | Micrometer metrics exposed via Actuator (verify counters via `MeterRegistry`) |

Test classes live under `integration-tests/src/test/java/com/ai/infrastructure/behavior/**` to keep scope isolated.

---

## 3. Data Seeding Strategy

1. Boot test context with the default auto-configuration so the module registers beans exactly like production.
2. Allow Liquibase to run against a clean PostgreSQL container per test class (use `@Testcontainers` + JUnit 5 lifecycle).
3. Seed domain-specific fixtures through the public REST endpoints or repositories (never insert raw SQL except where required for edge cases).
4. Clear caches between tests using `BehaviorInsightsService.evictUserInsights` or CacheManager utilities to avoid cross-test leakage.

---

## 4. Test Matrix

| ID | Scenario | Primary Components | Category |
|----|----------|--------------------|----------|
| IT-001 | Single event ingestion persists + publishes | `BehaviorIngestionController`, `DatabaseEventSink`, `BehaviorEventRepository` | Ingestion |
| IT-002 | Batch ingestion rejects invalid payload atomically | `BehaviorBatchIngestRequest`, `BehaviorEventValidator` | Validation |
| IT-003 | Default sink auto-detection respects `database` | `BehaviorProperties`, `BehaviorEventSink` | Configuration |
| IT-004 | Real-time aggregation updates metrics for purchases | `RealTimeAggregationWorker`, `BehaviorMetricsRepository` | Async metrics |
| IT-005 | Embedding worker generates vectors only for text events | `EmbeddingGenerationWorker`, `BehaviorEmbeddingRepository`, `AICoreService` (spy) | AI selective workload |
| IT-006 | Pattern detection schedules insights + cache TTL | `PatternDetectionWorker`, `BehaviorInsightsRepository`, `BehaviorInsightsService` | Analysis |
| IT-007 | Query controller filters by metadata JSONB & pagination | `BehaviorQueryController`, `BehaviorQueryService` | Query |
| IT-008 | Insights controller leverages cache + invalidation | `BehaviorInsightsController`, `BehaviorInsightsService` | API caching |
| IT-009 | Deletion service purges user footprint + cache | `BehaviorDeletionService`, repositories, cache | Privacy |
| IT-010 | Liquibase changelog applies cleanly & is idempotent | `SpringLiquibase`, PostgreSQL metadata | Schema |
| IT-011 | Monitoring controller reports counts + sink type | `BehaviorMonitoringController`, `MeterRegistry` | Observability |

---

## 5. Detailed Test Specifications

### IT-001 — Single Event Ingestion Persists and Publishes
- **Goal:** POST `/api/ai-behavior/ingest` stores a record and publishes `BehaviorEventIngested`.
- **Setup:** Autowire controller; spy `ApplicationEventPublisher`; use Postgres Testcontainer.
- **Steps:** Submit a valid `BehaviorEventRequest`. Await event via `Awaitility`.
- **Assertions:** HTTP 202 response, `behavior_events` row exists, application event captured once, `ingested_at` auto-populated.

### IT-002 — Batch Ingestion Validation is Atomic
- **Goal:** Ensure one invalid event rejects the entire batch and leaves DB empty.
- **Setup:** Use `/ingest/batch` endpoint with 2 valid + 1 invalid payload (missing `eventType`).
- **Assertions:** HTTP 400 with validation error details, zero rows inserted, no `BehaviorEventBatchIngested` published.

### IT-003 — Database Sink Selection
- **Goal:** Confirm `ai.behavior.sink.type=database` (default) registers `DatabaseEventSink`.
- **Setup:** Bootstrap context with default properties.
- **Assertions:** `BehaviorEventSink` bean instance of `DatabaseEventSink`, `BehaviorProperties.getSink().getType()` equals `database`.

### IT-004 — Real-Time Aggregation Worker
- **Goal:** Worker updates daily metrics when a purchase event is ingested.
- **Setup:** Publish `BehaviorEventIngested` for purchase with metadata `amount=99.5`.
- **Assertions:** `BehaviorMetrics` row created with `purchaseCount=1`, `totalRevenue=99.5`, executes on async executor (verify via `TaskExecutor` spy).

### IT-005 — Embedding Worker Selectivity
- **Goal:** Only feedback/search events trigger embedding generation.
- **Setup:** Spy `AICoreService` bean, emit `BehaviorEventIngested` events for `feedback` and `click`.
- **Assertions:** Embedding saved for `feedback` with `float[]` length > 0; zero insert for `click`; `AICoreService.generateEmbedding` invoked once.

### IT-006 — Pattern Detection & Cache TTL
- **Goal:** Scheduled worker aggregates events -> insights -> cached response.
- **Setup:** Seed 20 events for user, invoke `PatternDetectionWorker.detectPatterns()` manually.
- **Assertions:** `BehaviorInsights` stored with `validUntil` ~+5 minutes, `BehaviorInsightsService.getUserInsights` hits cache on second call, `@CacheEvict` tested via `evictUserInsights`.

### IT-007 — Metadata Filtering & Pagination
- **Goal:** JSONB filtering works for metadata fields and pagination honors `limit/offset`.
- **Setup:** Insert events containing `metadata.price`.
- **Assertions:** POST `/api/ai-behavior/query` with `metadataFilters.price > 100` returns correct subset, includes pagination headers/body size.

### IT-008 — Insights Controller Cache Behavior
- **Goal:** `GET /users/{id}/insights` returns cached payload; updates after cache eviction.
- **Setup:** Mock analyzer to return deterministic results, call endpoint twice, evict cache, call again.
- **Assertions:** Second call uses cache (verify analyzer invocation count), after eviction analyzer invoked again and response timestamp updated.

### IT-009 — GDPR Deletion Flow
- **Goal:** `BehaviorDeletionService.deleteUserBehaviors(UUID)` nukes all related data.
- **Setup:** Persist events, insights, metrics, embeddings for a user, prime cache, then call deletion service.
- **Assertions:** All repositories return zero rows for user, caches cleared, `BehaviorMetricsRepository.count()` unaffected for other users.

### IT-010 — Liquibase Changelog Integrity
- **Goal:** Ensure the module changelog creates schema and re-running is idempotent.
- **Setup:** Start Postgres container, run `SpringLiquibase` bean twice.
- **Assertions:** All tables/indexes exist (query `information_schema`), second execution performs zero statements, no exceptions thrown.

### IT-011 — Monitoring Endpoint Summary
- **Goal:** `/api/ai-behavior/monitoring/summary` reports counts & sink type after ingesting data.
- **Setup:** Seed events/insights/metrics, configure sink type to `database`.
- **Assertions:** Response JSON fields `eventCount`, `insightCount`, `metricCount`, and `activeSinkType` match repositories, response time < 200ms (measure via `StopWatch`).

---

## 6. Automation & Pipeline Hooks

- Wire tests into `integration-tests` Maven profile so `mvn verify -Pintegration` runs the suite on CI.
- Add GitHub Actions step (or Jenkins stage) that boots Docker to host dependencies required by Testcontainers.
- Publish `integration-tests/target/site/serenity` (or JUnit XML) artifacts for reporting and trend analysis.

---

## 7. Next Steps

1. Scaffold package `com.ai.infrastructure.behavior` inside `integration-tests`.
2. Add shared test fixtures (`BehaviorTestDataFactory`, `AwaitilityConfig`).
3. Implement IT-001 … IT-011 sequentially, prioritizing ingestion, deletion, and Liquibase coverage.
4. Update CI pipeline to run the new suite nightly and on PRs touching the behavior module.

---

**Document History**

| Version | Date | Author | Notes |
|---------|------|--------|-------|
| 0.1 | 2025-11-16 | GPT-5.1 Codex | Initial draft covering 11 integration tests |

