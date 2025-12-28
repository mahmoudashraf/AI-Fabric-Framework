# Behavior Module Test Gaps and Proposed Scenarios

This document lists missing or thin test coverage areas for the behavior analysis/processing module (unit and integration), and proposes concrete scenarios to add.

## Current Coverage Snapshot
- **Unit:** converters (JSONB list/map), entity lifecycle/searchable content, analysis service happy-path and clamping, storage adapter routing, configuration loading, thin controllers (processing/analytics).
- **Integration (real API style via `BehaviorIntegrationTestApp`):** analytics endpoints (trend distribution, rapid-decline, user trend deltas); processing endpoints (analyze user, batch, continuous start/cancel, scheduled pause/resume). Uses H2 + in-memory providers with real HTTP via `TestRestTemplate`.

## Gaps & Proposed Tests
- **BehaviorAnalysisService robustness**
  - Invalid/malformed LLM response JSON should fall back gracefully and log; assert defaults (trend STABLE, confidence 0, etc.).
  - Trend recomputation when LLM returns STABLE but deltas indicate decline; ensure `BehaviorTrend.fromDeltas` is applied.
  - Large event batch size throttling (processing delay honored) – measurable via elapsed time tolerance or spy on delay strategy.
  - Carry-forward of previous sentiment/churn into new insight when parsing response; verify deltas computed.
- **BehaviorProcessingManager**
  - Continuous job status lifecycle: RUNNING → COMPLETED when iterations finish; CANCELLED when interrupted; FAILED on exception.
  - Metrics counters increment for successes/errors (with a simple `SimpleMeterRegistry`).
  - Batch processing stops on `maxDuration` breach and honors `delayBetweenUsersMs`.
- **BehaviorAnalysisWorker (scheduled)**
  - Respects `scheduled-enabled` property and `BehaviorProcessingState.paused`; no calls when paused.
  - Stops after `scheduledMaxDuration` or `scheduledBatchSize`.
  - Handles `processNextUser` returning null (no pending users) without error.
- **Controllers (edge cases)**
  - Processing API: invalid payloads (negative delays/maxUsers) return 400; missing jobId on cancel returns 404.
  - Analytics API: 404 for unknown user on `/users/{id}/trend`; empty distributions return empty maps.
- **Repository/Queries**
  - `findRapidlyDecliningUsers` ordering (by updatedAt desc) and filters; ensure mixed trends return only RAPIDLY_DECLINING.
  - Derived queries for sentiment/trend return correct subsets.
- **ExternalEventProvider integration**
  - When provider throws or returns empty events, `analyzeUser` returns existing insight (or null) without persisting new records.
  - User context passed through to prompt (mock provider supplying context).
- **Config properties binding**
  - `BehaviorProcessingProperties` binds defaults and overrides via `@ConfigurationProperties` with relaxed binding; assert cron, delays, intervals.
- **API contract/regression (integration)**
  - Content-Type negotiation: endpoints reject `text/plain` POST bodies with 415.
  - Continuous job already finished still returns latest status (idempotent cancel).
  - Scheduled pause/resume actually toggles state observed by worker (can be verified by invoking worker manually in test with short cron).
- **Data/DB layer**
  - JSONB converters round-trip lists/maps containing quotes or escaped content.
  - Unique `userId` constraint enforced; duplicate save should raise `DataIntegrityViolationException`.
- **Observability**
  - Trend alert logging when trend worsens vs improves (can assert via appender in unit test).

## Suggested Placement
- Unit: `ai-infrastructure-behavior/src/test/java/com/ai/infrastructure/behavior/service/*`, `.../worker`, `.../repository`, `.../config`.
- Integration: `integration-Testing/behavior-integration-tests/src/test/java/com/ai/infrastructure/behavior/it/api/*` plus a new worker-focused IT that runs with a very short cron and stubbed provider.

## Test Data/Fixtures
- Extend `TestEventProvider` to emit deterministic event batches and optional user context; add variants for empty and exception paths.
- Use `SimpleMeterRegistry` for metrics assertions; use `Awaitility` or `Thread.sleep` with tight bounds for delay/interval checks.

## Execution Notes
- Keep controller tests thin (MockMvc standalone for unit; `TestRestTemplate` for real API).
- For scheduler IT, prefer explicit method invocation with overridden props instead of real cron threads to keep tests fast/deterministic.
