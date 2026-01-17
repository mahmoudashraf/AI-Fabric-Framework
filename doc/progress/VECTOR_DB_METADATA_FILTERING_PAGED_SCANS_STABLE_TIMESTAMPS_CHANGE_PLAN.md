# Vector DB Enhancements — Metadata Filtering + Paged Scans + Stable Timestamps (Change Plan)

## Status
In Progress

## Progress (Implemented So Far)
- ✅ Core API additions:
  - `VectorScanRequest`, `VectorScanPage`
  - `VectorDatabaseService.supportsVectorScan()`, `supportsMetadataFiltering()`, `scan(...)`
- ✅ Stable update semantics in `VectorManagementService` (prefer in-place `updateVector` when possible).
- ✅ Provider implementations (scan + metadata filtering + cursor paging):
  - Lucene (`ai-infrastructure-vector-lucene`)
  - In-memory (`ai-infrastructure-vector-memory`)
  - Qdrant (`ai-infrastructure-vector-qdrant`)
  - Weaviate (`ai-infrastructure-vector-weaviate`)
  - Milvus (`ai-infrastructure-vector-milvus`)
  - Pinecone (`ai-infrastructure-vector-pinecone`) — scan supported; metadata filtering is client-side
- ✅ Timestamp metadata contract wired via governance module (`_indexedCreatedAt`, `_indexedUpdatedAt`) and mapped back to `VectorRecord.createdAt/updatedAt` where supported.

## Remaining Work (Next)
- Add/verify provider-level docs for limitations (e.g., Pinecone server-side filtering limitations).
- Add focused provider tests for scan cursor semantics + filtering (where feasible).

## Problem
We want to remove (or minimize reliance on) `AISearchableEntity` while preserving:
- **Retention cleanup** (delete/archive/soft delete based on age and policy)
- **GDPR/CCPA deletion fallback** (delete indexed data for a user without requiring the app to enumerate every entityId)

Today, those features depend on SQL enumeration and/or SQL metadata search. The current `VectorDatabaseService` contract is missing:
- **Provider-native metadata filtering** in a portable way (beyond “scan everything then filter in memory”)
- **Paged scanning/scrolling** to avoid loading large vector sets into memory
- **Stable timestamp semantics**, because some providers reset `createdAt` on updates (and our current vector update flow can generate new vectorIds)

## Goals
- Add **portable, opt-in vector scanning** with:
  - paging via cursor/offset (cursor preferred)
  - optional metadata filters (exact match + basic predicates)
  - bounded payload options (include/exclude embeddings, content)
- Define and enforce **stable timestamp semantics**:
  - `VectorRecord.createdAt` = first successful index time for `(entityType, entityId)`
  - `VectorRecord.updatedAt` = last successful index/update time for `(entityType, entityId)`
- Implement the capabilities across providers where possible (Lucene, in-memory, Qdrant, Weaviate, Milvus, Pinecone), with clear **fallback behavior** when a provider cannot scan/filter efficiently.
- Keep backward compatibility: existing `VectorDatabaseService` methods keep working.

## Non-goals
- Building a full analytics/query language over vector metadata.
- Guaranteeing a “list all vectors” capability for every hosted vector DB (some providers may not support it without extra infrastructure).
- Solving cross-tenant access control here (that stays in access policy layers).

---

## Current State (Relevant Observations)
- `VectorDatabaseService` already supports:
  - `getVectorByEntity(entityType, entityId)`
  - `getVectorsByEntityType(entityType)` (unpaged)
  - `vectorExists(entityType, entityId)`
  - `removeVector(entityType, entityId)`
  - `getStatistics()` (often includes per-type counts)
  - `updateVector(vectorId, ...)` (provider-specific semantics)
- Some providers reset timestamps on update (e.g. Lucene writes `createdAt` as “now” on every re-add unless preserved).
- Current update flow may create new vectorIds on update (depends on how `VectorManagementService` is implemented).

---

## Proposed API Additions (Core)

### 1) New DTOs
Add DTOs in core (suggested package: `com.ai.infrastructure.dto`):
- `VectorScanRequest`
  - `String entityType` (required)
  - `Map<String, Object> metadataEquals` (optional, exact match)
  - `Map<String, Object> metadataContains` (optional, substring match; provider-dependent)
  - `Integer limit` (default e.g. 200)
  - `String cursor` (nullable)
  - `boolean includeContent` (default true)
  - `boolean includeEmbedding` (default false)
  - `boolean includeMetadata` (default true)
  - `String sort` (optional; e.g. `createdAt`/`updatedAt`, provider-dependent)
- `VectorScanPage`
  - `List<VectorRecord> vectors`
  - `String nextCursor` (nullable)
  - `boolean hasMore`

Notes:
- Cursor is preferred; offset paging can be supported in provider-specific implementations but is not required.
- Filtering is intentionally minimal (covers retention + user deletion use cases).

### 2) New VectorDatabaseService methods (defaulted)
Add **default** methods (non-breaking) to:
`ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/VectorDatabaseService.java`

- `default boolean supportsVectorScan() { return false; }`
- `default boolean supportsMetadataFiltering() { return false; }`
- `default VectorScanPage scan(VectorScanRequest request) { ... }`

Default `scan(...)` fallback (portable but expensive):
- Use `getVectorsByEntityType(entityType)` then filter in-memory, then page using a synthetic cursor.
- Emit a WARN log when fallback scanning is used (so operators know it’s O(N)).

### 3) Timestamp Contract (Portable)
Standardize timestamp fields:
- `VectorRecord.createdAt` and `VectorRecord.updatedAt` must be populated by providers whenever possible.
- Governance module may additionally persist stable index timestamps in vector metadata (opt-in):
  - `_indexedCreatedAt` (ISO-8601 string)
  - `_indexedUpdatedAt` (ISO-8601 string)

Providers should:
- On first insert: set both to now.
- On update: preserve `_indexedCreatedAt`, update `_indexedUpdatedAt`.

---

## Provider Implementation Plan

### InMemoryVectorDatabaseService
Files:
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-memory/.../InMemoryVectorDatabaseService.java`

Changes:
- Implement `supportsVectorScan=true`, `supportsMetadataFiltering=true`.
- Implement `scan(...)` with cursor paging:
  - deterministic ordering (e.g., by `updatedAt` then `vectorId`)
  - cursor = last `(updatedAt, vectorId)` encoded as string
- Enforce timestamp contract:
  - On update, preserve createdAt (already done); ensure metadata carries `_indexedCreatedAt/_indexedUpdatedAt`.

### LuceneVectorDatabaseService
Files:
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-lucene/.../LuceneVectorDatabaseService.java`

Changes:
- Preserve `createdAt` on `updateVector(...)`:
  - when updating, read existing doc’s `createdAt` and reuse it when rebuilding document
  - set `updatedAt` to now
- Add `scan(...)` via `searchAfter(...)` to support paging without loading all docs.
- Metadata filtering options:
  - Short-term: support reserved metadata keys via dedicated indexed fields (StringField).
  - Config proposal: `ai.vector.lucene.indexed-metadata-keys` (list) for keys like `userId`, `ownerId`, `_softDeleted`, `dataClassification`.
  - Store full metadata JSON as today, but also index selected keys for filtering.

Tradeoff:
- Full generic metadata filtering is hard if metadata is only stored as JSON; indexing selected keys is the pragmatic approach.

### QdrantVectorDatabaseService
Files:
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-qdrant/.../QdrantVectorDatabaseService.java`

Changes:
- Implement `scan(...)` using Qdrant scroll + payload filter.
- Implement metadata equals filtering via payload filters.
- Ensure timestamps persisted in payload and mapped back into `VectorRecord.createdAt/updatedAt`.

### WeaviateVectorDatabaseService
Files:
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-weaviate/.../WeaviateVectorDatabaseService.java`

Changes:
- Implement `scan(...)` with cursor paging if supported (or GraphQL paging).
- Implement metadata filtering via where filters (exact match).
- Store/return stable timestamps via metadata fields.

### MilvusVectorDatabaseService
Files:
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-milvus/.../MilvusVectorDatabaseService.java`

Changes:
- Implement scan via query/iterator if supported; otherwise partial support:
  - `supportsVectorScan=false` when not feasible.
- Implement metadata filtering using scalar fields / partitions when available.
- Ensure timestamps included and not reset on update.

### PineconeVectorDatabaseService
Files:
- `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-pinecone/.../PineconeVectorDatabaseService.java`

Changes:
- Implement metadata filtering for query-time deletes where possible.
- Scanning “all vectors by entityType” may not be feasible in a generic way depending on Pinecone API capabilities. If not feasible:
  - `supportsVectorScan=false`
  - Retention/GDPR fallback must use either:
    - provider-native metadata-filtered delete (best), or
    - an optional SQL catalog module (recommended for enterprise use), or
    - application-provided enumeration (UserDataDeletionProvider supplies entity refs)

---

## Core Semantics Fix: Stable Updates (VectorManagementService)

Files:
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/service/VectorManagementService.java`

Goal:
- Avoid creating a brand-new vectorId on update when provider supports `updateVector(vectorId, ...)`.

Plan:
- When vector exists for `(entityType, entityId)`:
  - Fetch existing via `getVectorByEntity(...)`
  - If it has `vectorId`, call `updateVector(existingVectorId, ...)`
  - Only fall back to “store new + remove old by id” when provider cannot update in place.

Outcome:
- Stable vectorId and stable `createdAt` across updates (provider permitting).

---

## Consumer Updates (to use the new APIs)

### Retention cleanup
Update the retention cleanup path to prefer `scan(...)` rather than `getVectorsByEntityType(...)`, and to base age on stable timestamps (prefer `_indexedCreatedAt` or a stable field).

### GDPR deletion fallback
Update to prefer provider-native metadata filtering:
- scan with `metadataEquals={"userId": userId}` (or configured keys)
- avoid full O(N) scans where possible

---

## Backward Compatibility & Rollout

### Compatibility
- All new `VectorDatabaseService` methods must be `default` methods.
- Existing providers compile unchanged; they just won’t support scan/filter until implemented.

### Rollout steps
1. Add DTOs + default methods + timestamp contract docs.
2. Implement in-memory + Lucene first (reference implementations).
3. Implement Qdrant/Weaviate/Milvus/Pinecone next (as supported).
4. Update retention + deletion to prefer scan/filter and log when falling back to full scans.
5. Add a feature flag to force fallback disablement in large deployments:
   - `ai.vector.scan.allow-fallback=false` (fail fast if scan not supported)

---

## Testing Plan

### Unit tests (core)
- `VectorManagementService`:
  - update keeps `vectorId` stable when provider supports update
  - `createdAt` preserved and `updatedAt` changes
- Default `scan(...)` fallback:
  - filtering correctness
  - cursor paging correctness

### Provider tests
- InMemory:
  - scan + cursor paging + metadata filtering
  - timestamp persistence
- Lucene:
  - update preserves createdAt
  - scan uses paging (no “load all”)
  - indexed metadata key filtering works

### Integration tests (optional)
- Retention cleanup deletes expected vectors using scan API.
- GDPR deletion removes vectors for a user via metadata filtering.

---

## Open Questions / Decisions Needed
- Which metadata keys are required/standard for GDPR fallback? (`userId` vs `ownerId` vs `createdBy`)
- Should retention use “indexed createdAt” or “entity createdAt” (domain timestamp)? If domain timestamp, ensure it is included in metadata and standardized.
- How should “soft delete” be enforced at query time if a provider cannot filter by metadata?
- For providers without scan support (or expensive scans), do we:
  - keep a minimal optional SQL catalog module, or
  - require application-provided enumeration for retention/GDPR?
