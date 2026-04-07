# Governance: Optional Single-Table Catalog (Audit/Retention/Deletion Enumeration)

## Goal
Introduce an **optional** relational “governance catalog” in `ai-infrastructure-governance` that enables:
- SQL-native **retention enumeration**, **deletion receipts**, and **audit/reporting**.
- A predictable fallback when some vector vendors cannot efficiently support **paged scans**, **metadata filtering**, or **stable timestamps**.

This is **not** a return to `AISearchableEntity`. The vector DB remains the **single source of truth for search**.

---

## Problem / Why Now
With `AISearchableEntity` removed from core, governance workflows that need *enumeration* (retention jobs, legal hold reporting, GDPR audit logs) must rely on:
- Vendor-specific vector scans/filters (not always available/efficient), or
- A dedicated SQL catalog that is purpose-built for governance.

We want to keep core clean while still enabling “enterprise/governance” use cases without reintroducing dual-source-of-truth complexity.

---

## Principles
- **Clean core**: no dual-write/search cache in core.
- **Optional module**: enabled only when `ai.governance.*` is configured.
- **No content duplication**: do not store embeddings or large content blobs in SQL.
- **Governance-only**: catalog is for audit/retention/deletion enumeration, not query hydration.
- **Fail behavior is explicit**: “best-effort” vs “strict” is a conscious choice.

---

## Scope
### In-scope
- Add a **single table** catalog in `ai-infrastructure-governance`.
- Provide a small API for:
  - upsert on index/update
  - mark deleted / deletion receipt
  - query by `entityType`, timestamps, flags (retention/legal hold), and small metadata keys
- Wire catalog updates from indexing/vector lifecycle (governance module only).

### Out-of-scope
- Reintroducing `AISearchableEntity` or any SQL “searchable entity store”.
- Multiple storage strategies (`SINGLE_TABLE` / `PER_TYPE_TABLE` / `CUSTOM`) at launch.
- Using SQL catalog for search/hydration/materialization.
- Vendor-specific advanced reporting UI/APIs.

---

## Proposed Data Model (Single Table)
Table name (proposed): `ai_governance_catalog`

Minimum columns:
- `id` (UUID) or composite PK (`tenant_id?`, `entity_type`, `entity_id`)
- `tenant_id` (optional; if multi-tenant is supported)
- `entity_type` (string, indexed)
- `entity_id` (string/uuid-as-string, indexed)
- `vector_id` (string, nullable)
- `source_created_at` (timestamp, nullable)  
  - Stable “domain created time” if provided by app/entity metadata
- `indexed_at` (timestamp)  
  - First time we indexed
- `last_indexed_at` (timestamp)  
  - Last successful index/update
- `deleted_at` (timestamp, nullable)
- `deletion_mode` (enum/string, nullable)  
  - `HARD_DELETE`, `SOFT_DELETE` (governance semantics)
- `legal_hold` (boolean, default false)
- `retention_until` (timestamp, nullable)
- `status` (string, optional; e.g., `ACTIVE`, `DELETED`)
- `metadata_json` (text/json, optional; keep small and curated)

Indexes (minimum):
- (`tenant_id`, `entity_type`, `entity_id`) unique
- (`entity_type`, `retention_until`)
- (`entity_type`, `deleted_at`)
- (`entity_type`, `last_indexed_at`)

---

## API / SPI Design
### Public service (governance module)
`AIGovernanceCatalogService`:
- `upsertIndexed(AIGovernanceCatalogUpsertRequest req)`
- `markDeleted(AIGovernanceDeletionRequest req)`
- `findCandidatesForRetention(entityType, now, pageRequest)`
- `findDeletions(entityType, from, to, pageRequest)`

### Enablement + behavior flags
Configuration (proposed):
- `ai.governance.catalog.enabled` (default: false)
- `ai.governance.catalog.mode` (default: `BEST_EFFORT`)
  - `BEST_EFFORT`: failures logged; indexing continues
  - `STRICT`: catalog write failure fails the operation (for regulated environments)

---

## Wiring / Lifecycle
When enabled, governance listens to vector lifecycle operations:
- On vector upsert/store/update: write/merge catalog entry (`indexed_at`, `last_indexed_at`, `vector_id`, curated metadata, stable timestamps).
- On vector delete: mark deletion and persist a “receipt” (`deleted_at`, `deletion_mode`).

Implementation options:
1) **Decorator** in governance module that wraps `VectorDatabaseService` (governance-only wrapper).
2) Hook into `VectorManagementService` lifecycle events (if an internal event bus exists).

Preference: governance-only **decorator** because it keeps core untouched and is easy to toggle.

---

## Retention & GDPR Support (How It Helps)
- Retention jobs can enumerate candidates via SQL without requiring vendor scans.
- GDPR reporting can produce “what was deleted + when” receipts even if vectors are hard-deleted.
- Legal hold can be enforced by checking catalog flags before allowing deletion (STRICT mode).

---

## Test Plan
- Unit tests for repository queries and upsert/merge semantics.
- Integration test (H2/Postgres container if available) verifying:
  - upsert on store
  - update preserves `indexed_at` while changing `last_indexed_at`
  - delete writes a deletion receipt row
- Verify parent CI workflow:
  - `.github/workflows/parent-verify.yml`

---

## Acceptance Criteria
- Core modules compile/run with governance disabled (default).
- Enabling `ai.governance.catalog.enabled=true` creates/writes the catalog table and produces audit rows for vector lifecycle operations.
- No reintroduction of `AISearchableEntity` or storage-strategy menu in core.
- Clear docs describing governance catalog’s purpose and boundaries (audit/retention only).

