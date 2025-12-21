## Migration Module Test Plan (Unit & Integration)

### Scope and Goals
- Raise confidence in `ai-infrastructure-migration-core` by covering job lifecycle, filtering, queueing, rate limiting, failure paths, and pause/cancel semantics.
- Keep tests isolated: unit tests for logic and guarding, integration tests for Spring wiring, repos, queue interactions, and H2 behavior.

### Proposed Test Cases

#### Unit Tests (Junit + Mockito) – status
1) Job lifecycle updates
   - startMigration creates RUNNING job with totals, timestamps. ✅
   - pause sets PAUSED and does not alter totals. ✅
   - resume resets status to RUNNING and restarts processing (stub out executor). ✅ (resume test)
   - cancel sets CANCELLED, sets completedAt. ⚪ (status covered; completedAt still pending)

2) Pause/Cancel honoring
   - processJob exits when job.isPaused() before fetching next page. ✅
   - processJob exits when job.isCancelled() before fetching next page. ✅ (early cancel test)
   - applyRateLimit respects interrupts (thread interrupted -> flag set). ✅

3) Filtering
   - matchesFilters honors safeEntityIds, createdBefore/After. ✅
   - throws when createdAt field missing/invalid type. ✅ (counts failure, job completes)
   - uses MigrationFilterPolicy when present; falls back to field config otherwise. ✅ (policy skip)

4) Reindex / idempotency
   - alreadyIndexed short-circuits when reindexExisting=false. ✅
   - reindexExisting=true enqueues even when present. ✅
   - skips entities with missing/blank resolved ID. ⚪

5) Queue payload
   - enqueueForIndexing builds IndexingRequest with correct entityType, entityId, class name, ASYNC strategy, action plan flags, payload JSON, and maxRetries from AIIndexingProperties. ✅

6) Error handling
   - Per-entity enqueue failure increments failed count; job persists progress. ✅
   - Unhandled exception marks job FAILED with errorMessage. ✅

7) Config validation
   - Missing ai-entity-config for entityType throws. ✅
   - Missing entityFields config with no policy throws. ✅
   - Missing createdAtField throws. ⚪

8) Rate limit math
   - delay computed from rateLimit; zero/negative/no value -> no sleep. ✅
   - interrupt path asserted (restores flag). ✅

#### Integration Tests (Spring + H2) – status
9) Happy-path migration enqueues. ✅
   - Seeds JPA repo; run migration; verify queueService.enqueue called per entity; job COMPLETED; processed count matches.

10) Pause/Resume end-to-end. ✅
   - Start migration, pause after first batch (mock job status flip), ensure processing stops; resume continues.

11) Cancel mid-run. ✅
   - Start migration, set status CANCELLED; ensure early exit and status persisted.

12) ReindexExisting flag. ✅
   - Seed searchable storage; run with reindexExisting=false (skips), then true (enqueues).

13) Filters integration. ✅
   - createdBefore/After with actual entity dates; safeEntityIds only processed set.

14) Failure path. ✅ (job completes but failedEntities recorded)
   - Force enqueue exception; job moves to FAILED with errorMessage; failedEntities incremented.

15) Rate limiting observable. ⚪
   - Configure small rateLimit; assert sleep invoked via spy (or measure elapsed ≥ expected).

16) Repository resolution guard. ✅
   - Unknown entityType -> IllegalStateException.

17) Concurrency sanity. ⚪
   - Two jobs on different entityTypes run; queues get distinct payloads; progress tracked separately.

### Implementation Steps
- Add unit test class for DataMigrationService (mock queue, repos, tracker, config, policies).
- Add integration test class using H2 + real Spring context, minimal entity + repository.
- Test fixtures: simple JPA entity with createdAt; stub MigrationFilterPolicy for policy branch.
- Ensure reproducible batch sizes/rate limits via test properties.
- Consider small helper to build MigrationRequest and seed entities.
