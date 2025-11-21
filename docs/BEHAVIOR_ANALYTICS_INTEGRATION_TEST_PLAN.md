## AI Behavior Analytics Integration Test Plan

### 1. Objectives
- Validate end‑to‑end behavior of the domain‑agnostic AI Behavior Analytics module across ingestion, processing, AI analysis, search/orchestration, and production hardening features (metrics, rate limiting, audit, retention).
- Ensure the module works with the shared AI Core infrastructure (RAG orchestrator, PII detection, AI indexing annotations) while remaining policy driven and tenant safe.
- Provide regression safety and living documentation for future customers embedding custom `BehaviorAnalysisPolicy` implementations.

### 2. Existing Coverage Snapshot
| Suite / Class | Scope Today | Notes |
| --- | --- | --- |
| `EventIngestionIntegrationTest` | single + batch ingestion, TTL, processed flag | Uses embedded Postgres and MockMvc |
| `BehaviorAnalysisIntegrationTest` | scheduled worker creating insights + retention cleanup | Validates default policy thresholds |
| `BehaviorSearchIntegrationTest` | Orchestrated queries, PII detection path | Mocks `PIIDetectionService`/`RAGOrchestrator` |

Gaps: no tests for failure handling/retries, metrics/audit emission, rate limiting toggles, policy override hooks, or multi‑tenant throttling.

### 3. Proposed Additions
1. **Ingestion Resilience Suite**
   - Simulate transient DB failures → ensure retries and escalation to failed table.
   - Validate audit + metrics increments for success/failure paths.
   - Cover retention TTL overrides (e.g., custom per‑event TTL property).

2. **Policy Hook Suite**
   - Boot with custom `BehaviorAnalysisPolicy` bean; assert policy outputs propagate to stored insights.
   - Verify `@ConditionalOnMissingBean` default policy still loads if no override provided.

3. **Worker Lifecycle Suite**
   - Force mixed user batches; ensure grouping per user and parallel analysis respecting `processing.worker.poolSize`.
   - Validate `maxRetries` behavior by injecting deterministic policy exceptions.

4. **Search & Orchestration Suite**
   - RAG enrichment success + failure (mock orchestrator returning error).
   - PII detection flow with redacted query and audit log entry.
   - Segment + pattern extraction parsing from orchestration metadata.

5. **Production Hardening Suite**
   - Rate limiting interceptor enabled with Bucket4j in tests using MockMvc.
   - Metrics registry verification (Micrometer test registry) for ingestion/search counters.
   - Structured audit log assertions (capture logger via `OutputCaptureExtension`).

### 4. Test Architecture Notes
- **Embedded Postgres:** Continue using `io.zonky.test:embedded-postgres` shared bootstrap (already centralized in `BehaviorAnalyticsIntegrationTest`).
- **Shared Mock Configuration:** `TestBehaviorApplication` now hosts mocks for AI Core dependencies; extend for metrics/audit verifications as needed.
- **Scheduling Control:** Keep scheduling disabled via `spring.task.scheduling.enabled=false` and invoke workers explicitly to avoid cross‑suite contention.
- **Testcontainers:** Remains optional; embedded Postgres keeps tests runnable locally and in CI without Docker.

### 5. Placement Recommendation
Reuse the existing `ai-infrastructure-behavior` module’s `src/test/java` tree rather than creating a separate integration-tests module. Rationale:
- Tests exercise module‑local configuration properties, schedulers, and components; colocating keeps visibility on package‑private utilities and avoids exporting internals.
- The parent repository already contains a global `integration-tests` project for cross-module flows; these scenarios are strictly intra-module.
- Maven build times stay reasonable because the embedded Postgres instance is shared per test class (single process) and no extra module graph management is required.

If a future requirement arises to orchestrate behavior analytics with other verticals (e.g., provider matrix, core AI search), those cross-cutting cases can live in `/integration-tests`. For now, keep AI Behavior integration tests local to maintain fast feedback cycles and direct access to module beans.

### 6. Next Steps Checklist
1. Implement suites listed in Section 3, wiring them into the current module.
2. Add documentation links from `AI_BEHAVIOR_ANALYTICS_IMPLEMENTATION_SEQUENCE.md` to this plan for discoverability.
3. Configure CI to run `mvn -pl ai-infrastructure-behavior test` (already viable with embedded Postgres).
