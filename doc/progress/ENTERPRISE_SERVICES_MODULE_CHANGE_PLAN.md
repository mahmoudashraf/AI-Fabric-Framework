# AI Infrastructure Governance Module (Ops/Governance) — Change Plan

## Status
In Progress

## Progress (Implemented So Far)
- ✅ New optional module `ai-infrastructure-governance` created and wired via auto-configuration.
- ✅ `IndexCatalog` abstraction + `VECTOR/SQL/DISABLED/AUTO` modes implemented.
- ✅ `GovernanceVectorDatabaseServiceDecorator` implemented (stable index timestamps + optional SQL catalog sync).
- ✅ Governance toggles implemented:
  - `ai.governance.enabled`
  - `ai.governance.catalog.mode`
  - `ai.governance.deletion.enabled`
  - `ai.governance.privacy.enabled`
  - `ai.governance.compliance.enabled`
  - `ai.governance.content-filter.enabled`
- ✅ Governance APIs moved out of core into governance:
  - `AIComplianceService` + compliance policy SPI
  - `AIContentFilterService`
  - Deletion + privacy services (GDPR/CCPA-oriented entrypoints)
- ✅ Retention cleanup scheduling moved to governance:
  - `ai.governance.retention.enabled` + `ai.governance.retention.cron`
  - `ai.governance.retention.entity-types` and/or `ai.governance.retention.retention-days`
  - `RetentionCleanupScheduler` uses `IndexCatalog.scan(...)` and deletes via `VectorDatabaseService.removeVector(...)`
- ✅ Legacy `ai.cleanup.*` (AISearchableEntity-based cleanup) removed from indexing module.

## Remaining Work (Next)
- Add provider capability matrix + docs for governance modes (AUTO/VECTOR/SQL).
- Add minimal metrics/logging around catalog mode selection + fallback usage.

## Naming
**Chosen module name**: `ai-infrastructure-governance`

This name communicates the intent well: retention, deletion workflows, audit/catalog, and operational controls.

---

## Problem
We want the framework to support both:
- **Small apps** (local Lucene, minimal dependencies, “just works”), and
- **Enterprise apps** (Qdrant/Weaviate/Milvus/Pinecone, compliance/retention/GDPR expectations).

Retention cleanup and “right-to-delete” (GDPR/CCPA) need two hard capabilities:
- Enumerate indexed items (by entity type, age, metadata).
- Find user-linked indexed items when the application cannot enumerate all entityIds.

Today, those behaviors rely on `AISearchableEntity` and SQL queries (e.g., metadata snippet search). If we remove `AISearchableEntity` without replacement, we either:
- degrade to expensive O(N) scans in the vector DB, or
- force applications to implement full enumeration logic (hurts adoption).

---

## Goal (Product Outcome)
Make “enterprise ops” capabilities **optional** and **pluggable**:
- Core stays vector-first for search/RAG/indexing.
- Enterprise Services provides **catalog + retention + deletion discovery** as add-ons.
- At runtime, choose the best available backend:
  1) Vector-native catalog (when provider supports scan/filter/timestamps),
  2) SQL catalog (when vector provider cannot), or
  3) No fallback (require app-provided references).

---

## Non-goals
- Creating a full metadata query language across all vector vendors.
- Forcing all vector vendors to support scan/filter for MVP (we’ll do capability-based routing).
- Storing duplicate entity content in SQL by default.

---

## Architecture Overview

### 1) Introduce a single abstraction: `IndexCatalog`
New interface (in Enterprise Services module):
- `IndexCatalog`
  - `upsert(IndexCatalogEntry entry)`
  - `delete(entityType, entityId)`
  - `exists(entityType, entityId)`
  - `scan(IndexCatalogScanRequest req) -> IndexCatalogScanPage`
  - `findByUser(userId, entityTypes?, limit?)` (optional convenience)

Core idea: retention + deletion code uses `IndexCatalog`, not “AISearchableEntity vs vector DB” directly.

### 2) Provide two implementations (same interface)
**A) Vector-backed catalog** (no SQL mirror)
- Uses the vector capability plan:
  - metadata filtering
  - paged scans
  - stable timestamps
- Works best when vendor supports it.

**B) SQL-backed catalog** (minimal mirror)
- Stores minimal “inventory” rows:
  - `entityType`, `entityId`, `vectorId`
  - `indexedCreatedAt`, `indexedUpdatedAt`
  - selected metadata keys for deletion/retention (e.g. `userId`, `dataClassification`, `_softDeleted`)
- Does **not** store full `searchable_content` by default.
- Enables robust ops even for vendors without scan/filter.

### 3) Keep the catalog synchronized via a single integration point
Governance module supplies a `VectorDatabaseService` decorator:
- `GovernanceVectorDatabaseServiceDecorator implements VectorDatabaseService`
  - enriches vector metadata with stable index timestamps (`_indexedCreatedAt`, `_indexedUpdatedAt`)
  - delegates to the real provider
  - on `storeVector/updateVector/removeVector/removeVectorById/clear*` updates `IndexCatalog` when using the **SQL-backed** catalog
  - ensures “single write path” for catalog sync (without forcing SQL mirroring for vector-derived catalogs)

This avoids sprinkling catalog updates across `AICapabilityService`, migration, web, etc.

---

## Proposed Module Layout

### New Maven module
- Folder: `ai-infrastructure-module/ai-infrastructure-governance`
- ArtifactId: `ai-infrastructure-governance` (or `ai-fabric-governance` if you want to align with “ai-fabric-*” naming)
- Packaging: `jar`

### Auto-configuration
Provide Spring Boot auto-config with capability-based selection:
- Property: `ai.governance.enabled` (default `false`)
- Property: `ai.governance.catalog.mode` = `AUTO | VECTOR | SQL | DISABLED`
  - `AUTO`: pick `VECTOR` if provider supports scan/filter + stable timestamps; else `SQL` if SQL catalog available; else `DISABLED`
- Property: `ai.governance.catalog.required-metadata-keys` (default: `userId,ownerId,createdBy,dataClassification`)
- Property: `ai.governance.deletion.enabled` (default `false`)
- Property: `ai.governance.privacy.enabled` (default `false`)
- Property: `ai.governance.compliance.enabled` (default `false`)
- Property: `ai.governance.content-filter.enabled` (default `false`)

---

## Changes Required (High Level)

### Phase 1 — Add the Enterprise Services module skeleton
- Add the new module to `ai-infrastructure-module/pom.xml` `<modules>`.
- Create `pom.xml` with dependency on `ai-fabric-core`.
- Add `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### Phase 2 — Implement `IndexCatalog` + SQL-backed catalog
- Create minimal entity (separate from current `AISearchableEntity`), e.g. `IndexCatalogEntity`.
- Repository + service implementing `IndexCatalog`.
- Migration SQL for the new catalog table (or reuse existing tables if you decide to keep them).

### Phase 3 — Implement vector-backed catalog adapter
- Implement `IndexCatalog` using the vector scan/filter APIs.
- This depends on the separate change plan:
  - `changes/VECTOR_DB_METADATA_FILTERING_PAGED_SCANS_STABLE_TIMESTAMPS_CHANGE_PLAN.md`

### Phase 4 — Add the catalog-syncing decorator
- Implement `GovernanceVectorDatabaseServiceDecorator`.
- Auto-config wraps the user’s `VectorDatabaseService` bean when `ai.governance.enabled=true`.

### Phase 5 — Move retention + deletion “discovery” to Enterprise Services
Goal: keep core lean; enterprise-services owns the ops extras.
- Relocate or re-implement scheduled retention cleanup:
  - operate via `IndexCatalog.scan(...)`
  - enforce retention using stable timestamps/metadata
- Relocate or re-implement GDPR deletion discovery fallback:
  - primary: `UserDataDeletionProvider.findIndexedEntities(userId)`
  - fallback: `IndexCatalog.findByUser(userId, ...)`

Core can keep the orchestration entrypoint (if you prefer), but the “how do we find indexed items?” logic should live behind `IndexCatalog`.

### Phase 6 — Hardening & UX
- Logging: warn when falling back to expensive scan paths.
- Metrics: counters for deletions, retention removals, scan duration, fallback usage.
- Docs: “Small app path” (no enterprise-services) vs “Enterprise path”.

---

## Compatibility Strategy
- If a project doesn’t include the module, nothing changes.
- If included but `enabled=false`, nothing changes.
- If enabled:
  - prefer vector-catalog when supported
  - else SQL catalog if configured
  - else disable fallback discovery with clear warnings (“GDPR deletion requires app-provided references”)

---

## Tests

### Unit tests (enterprise-services)
- `IndexCatalog` contract tests (exists/upsert/delete/scan paging semantics).
- `GovernanceVectorDatabaseServiceDecorator` ensures SQL catalog is updated on vector operations.

### Provider tests (vector-catalog path)
- Use in-memory vector provider to validate:
  - metadata filtering works for user deletion
  - paged scans work for retention cleanup

### Integration tests
- Minimal Spring context tests:
  - AUTO mode chooses VECTOR when capabilities exist
  - AUTO mode chooses SQL when vector capabilities absent

---

## Open Decisions (You should choose now)
1) **Do you want to keep any full content in SQL?**
   - Recommendation: **no** by default; keep only identifiers + timestamps + key metadata.
2) **What is the “user linkage” metadata contract?**
   - Recommendation: standardize a few keys (`userId`, `ownerId`, `createdBy`) and document them.
3) **Soft delete semantics**
   - If a provider cannot filter on `_softDeleted`, treat SOFT_DELETE as HARD_DELETE or rely on SQL catalog only.
