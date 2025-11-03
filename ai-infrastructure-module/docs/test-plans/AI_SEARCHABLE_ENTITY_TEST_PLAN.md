# Integration Test Plan - AISearchableEntity Lifecycle

**Component**: `AISearchableEntity`, `AISearchableEntityRepository`, `AICapabilityService`, `VectorManagementService`

**Priority**: 🔴 CRITICAL  
**Estimated Effort**: 1.0 week  
**Status**: 🚧 In Progress

---

## 📋 Overview

This plan defines end-to-end integration coverage for the lifecycle of `AISearchableEntity` records and their relationship with vector storage. The goal is to guarantee that searchable documents remain consistent, queryable, and resilient across ingest, update, and removal workflows.

### Components Under Test
- `AISearchableEntity` JPA entity
- `AISearchableEntityRepository`
- `AICapabilityService` (document enrichment & orchestration)
- `VectorManagementService` & underlying `VectorDatabase`
- `AIEmbeddingService` (ONNX profile)
- Background processing pipelines (`@Async`, scheduler jobs)
- Error/compensation handlers for stale or missing vectors

### Scope
- ✅ Index creation & enrichment
- ✅ Update & reindex flows (content + metadata)
- ✅ Deletion & cleanup duties
- ✅ Consistency guarantees between database & vector store
- ✅ Resilience during errors & retries
- ✅ Concurrency, pagination, and searchability checks
- ✅ Observability hooks (metrics/logging/audit records)
- ❌ Out of scope: UI rendering, analytics dashboards, long-term retention policies

### Success Criteria
- Searchable entities always reflect the latest canonical content & metadata
- Vector store never contains dangling embeddings without matching entities
- Failures degrade gracefully and are recoverable via retry or repair jobs
- Concurrent operations maintain idempotency and transactional integrity
- Pagination and query filters behave predictably for downstream search services

---

## 🧪 Test Scenarios

### TEST-AISE-001: Initial Index Creation
**Priority**: Critical  
**Status**: ✅ AUTOMATED (`AISearchableEntityLifecycleIntegrationTest#searchableEntityCreatedAfterProductCreation`)

1. Persist an entity eligible for indexing (e.g., product) through the existing ingestion flow.  
2. Allow the `AICapabilityService` to process and create an `AISearchableEntity`.  
3. Assert that:  
   - The entity row exists with populated searchable content, metadata JSON, and vector id.  
   - A matching vector exists in the vector store with identical entity id + type.  
   - Optional AI analysis or highlights are recorded when enabled.  
4. Execute a semantic search via `VectorManagementService.search` to ensure the document is discoverable.

### TEST-AISE-002: Metadata Enrichment & Normalization
**Priority**: High  
**Status**: ✅ AUTOMATED (`AISearchableEntityLifecycleIntegrationTest#searchableEntityCreatedAfterProductCreation`)

1. Store an entity with rich metadata (categories, tags, locales).  
2. Verify the metadata JSON persisted to `AISearchableEntity` retains key casing, locale formatting, and deterministic ordering (price/category/brand).  
3. Ensure metadata is mirrored into vector document payload (when available) and matches the stored JSON.  
4. Confirm the metadata map survives serialization/deserialization boundaries (ObjectMapper, repository projection).

### TEST-AISE-003: Update & Reindex Consistency
**Priority**: Critical  
**Status**: ✅ AUTOMATED (`AISearchableEntityLifecycleIntegrationTest#searchableEntityUpdatedAfterProductUpdate`)

1. Create an indexed entity.  
2. Modify the source content (title/description/price) and trigger reindex.  
3. Ensure the `AISearchableEntity` row updates searchable content, metadata, timestamps, and vectorId.  
4. Validate the old vector is removed and replaced with the new embedding.  
5. Search for the updated keyword and confirm only fresh content is returned.

### TEST-AISE-004: Soft Delete & Cleanup
**Priority**: Critical  
**Status**: ✅ AUTOMATED (`AISearchableEntityLifecycleIntegrationTest#searchableEntityDeletedAfterProductDeletion`)

1. Index a record, then mark it deleted through the service layer.  
2. Verify cascade cleanup removes `AISearchableEntity` and associated vector(s).  
3. Confirm that subsequent searches omit the deleted document.  
4. Ensure audit logs/event trail record the removal.

### TEST-AISE-005: Stale Vector Repair Job
**Priority**: High  
**Status**: ✅ AUTOMATED (`AISearchableEntityLifecycleIntegrationTest#searchableEntityVectorRepairJobRehydratesMissingVector`)

1. Manually create a mismatch (delete vector but keep searchable entity).  
2. Run the scheduled repair job or service method designed to rehydrate missing vectors.  
3. Verify the job recreates embeddings and restores the vector id.  
4. Confirm metrics/log counts reflect the repaired record.

### TEST-AISE-006: Search Pagination & Sorting
**Priority**: High  
**Status**: ✅ AUTOMATED (`AISearchableEntityExtendedIntegrationTest#searchPaginationAndSorting`)

1. Index > 50 documents across multiple entity types.  
2. Execute paginated queries with different sort orders (score desc, updatedAt desc).  
3. Validate deterministic ordering, correct page boundaries, and accurate total counts.  
4. Ensure search latency stays within SLA (< 200 ms per page locally).

### TEST-AISE-007: Concurrent Ingest & Updates
**Priority**: High  
**Status**: ✅ AUTOMATED (`AISearchableEntityExtendedIntegrationTest#concurrentUpdatesRemainConsistent`)

1. Spawn N (e.g., 25) parallel upserts against the same logical entity id.  
2. Confirm final `AISearchableEntity` state reflects the latest payload.  
3. Ensure no duplicate rows or orphan vectors exist post-run.  
4. Validate locking/transactional semantics keep operations idempotent.

### TEST-AISE-008: Bulk Backfill Import
**Priority**: Medium  
**Status**: ✅ AUTOMATED (`AISearchableEntityExtendedIntegrationTest#bulkBackfillProcessesAllDocuments`)

1. Feed a bulk ingestion job with hundreds of documents.  
2. Monitor memory footprint and transaction batching.  
3. Spot-check randomly selected ids for accurate content, metadata, and vector presence.  
4. Confirm job metrics (processed, succeeded, failed) match repository counts.

### TEST-AISE-009: Error Handling & Retries
**Priority**: Critical  
**Status**: ✅ AUTOMATED (`AISearchableEntityExtendedIntegrationTest#embeddingFailureCanBeRetried`)

1. Force the embedding provider to throw (e.g., temporary IO error).  
2. Ensure the source entity is still persisted while indexing remains pending.  
3. Validate retry or dead-letter queue captures the failed job.  
4. After recovery, verify the entity eventually becomes searchable and no duplicate vectors are created.

### TEST-AISE-010: Observability & Audit Trail
**Priority**: Medium  
**Status**: ✅ AUTOMATED (`AISearchableEntityExtendedIntegrationTest#observabilityLogsContainEntityContext`)

1. Trigger create/update/delete flows.  
2. Confirm structured logs contain correlation ids, entity references, and timing.  
3. Validate Micrometer metrics increment for processed documents, failures, retries.  
4. Ensure audit tables (if enabled) capture the lifecycle events.

### TEST-AISE-011: Multi-Tenant Isolation (Optional)
**Priority**: Medium  
**Status**: ✅ AUTOMATED (`AISearchableEntityExtendedIntegrationTest#multiTenantIsolation`)

1. Configure two tenants/workspaces with isolated prefixes or DB schemas.  
2. Index data for each tenant and ensure cross-tenant queries return zero results.  
3. Validate cleanup only affects data within the targeted tenant scope.

### TEST-AISE-012: Large Document Handling
**Priority**: Medium  
**Status**: ✅ AUTOMATED (`AISearchableEntityExtendedIntegrationTest#largeDocumentHandlingMaintainsSearchability`)

1. Index documents near maximum allowed size (e.g., 16 KB textual payload).  
2. Confirm chunking/preprocessing runs before generating embeddings.  
3. Ensure `AISearchableEntity` content truncation rules (if any) still provide relevant snippets.  
4. Search for a tail-end phrase and validate retrieval accuracy.

---

## ⚙️ Test Infrastructure Setup

- Activate `dev` or `onnx-test` profile to use deterministic ONNX embeddings.  
- Use dedicated H2/ Postgres schema to avoid polluting prod data.  
- Provide seeded fixtures for products, knowledge articles, or behaviors.  
- Ensure Lucene index paths point to temp directories and are wiped before each test class.  
- Leverage Awaitility or polling helpers for asynchronous processing validations.  
- Mock external providers (OpenAI) to avoid real API calls while preserving embedding pipeline.

### Required Beans
- `AISearchableEntityRepository`  
- `VectorManagementService` & `VectorDatabaseService`  
- `AIEmbeddingService` (ONNX provider)  
- `AICapabilityService` / pipeline orchestrators  
- Optional: `AuditService`, metrics registry, scheduler triggers

---

## 🛠️ Tooling & Utilities

- **Awaitility** for asynchronous waits  
- **TestClock / Time manipulation** for timestamp assertions  
- **DataBuilder utilities** to generate sample content/metadata  
- **Vector inspection helpers** to fetch vectors by entity id  
- **Log captors** to verify structured logging

---

## 📈 Metrics & Benchmarks

| Scenario | Target | Description |
|----------|--------|-------------|
| Initial index | < 750 ms | Time from entity save to searchable availability |
| Reindex update | < 1 s | Including vector removal + recreation |
| Delete cleanup | < 300 ms | From delete call to vector purge |
| Bulk ingest (500 docs) | < 5 min | End-to-end completion |
| Repair job | < 60 s | To reconcile 50 stale vectors |

Collect metrics via Micrometer and assert within tolerances where feasible in CI; otherwise log for manual review.

---

## ✅ Exit Criteria

- All critical/high tests automated and passing in CI.  
- Recovery jobs validated against synthetic failures.  
- Documentation updated with operational playbooks for repair tasks.  
- Observability dashboards reflect indexing throughput & failure rates.  
- Stakeholders sign off on readiness for downstream RAG & search integrations.

---

## 📎 Related Artifacts
- [`ENTITY_PROCESSING_TEST_PLAN.md`](./ENTITY_PROCESSING_TEST_PLAN.md)  
- [`VECTOR_DATABASE_TEST_PLAN.md`](./VECTOR_DATABASE_TEST_PLAN.md)  
- [`SEARCH_SERVICES_TEST_PLAN.md`](./SEARCH_SERVICES_TEST_PLAN.md)  
- [`AI_MODULE_INTEGRATION_TEST_ANALYSIS.md`](../AI_MODULE_INTEGRATION_TEST_ANALYSIS.md)

---

**Next Steps**
1. Implement automated test suite per scenarios above.  
2. Integrate repair & monitoring jobs into CI validation.  
3. Align with platform observability team for metric dashboards.

