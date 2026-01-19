# Governance PR #120 Review Follow-up (Catalog Consistency + Docs + Tests)

This document captures the follow-up work for PR #120 (“Remove AISearchableEntity, introduce governance module”), focused on governance reliability and operability.

## Goals

- Address the **IndexCatalog consistency** concern raised in review (especially in `SQL` mode).
- Ensure governance behavior is **documented** (what’s guaranteed vs best-effort).
- Add **targeted tests** for the governance decorator + catalog interactions.
- Keep `ai-fabric-core` clean (no governance logic reintroduced to core).

## Background

Governance introduces an `IndexCatalog` abstraction to support:
- retention (enumeration + stable timestamps),
- deletion discovery (GDPR/CCPA),
- audit-like inventory of indexed items.

`IndexCatalog` can be backed by:
- vector-native scan/filter (`VECTOR` mode), or
- a relational catalog (`SQL` mode via `JpaIndexCatalog`).

Because vector storage is usually a **remote system**, we cannot provide true transactional atomicity between “vector DB write” and “SQL catalog write”.

## Critical Item: SQL Catalog Consistency

### Problem Statement

In `SQL` mode, the governance decorator writes to two independent systems:
1. the configured vector DB provider
2. the SQL catalog (`JpaIndexCatalog`)

If the vector write succeeds and catalog write fails (or vice versa), the catalog may temporarily diverge from the vector store.

### Proposed Framework Semantics (Recommended)

- Treat catalog updates as part of the vector lifecycle call.
- Retry transient SQL catalog failures.
- If still failing, **fail the operation** (exception propagates).
  - Rationale: indexing pipelines are already designed around retries (queue + backoff).
  - This is safer than silently swallowing catalog failures (which would break retention/deletion enumeration).

### Implementation Work

- Add bounded retry/backoff for SQL catalog writes from `GovernanceVectorDatabaseServiceDecorator`:
  - `catalog.upsert(...)`
  - `catalog.delete(...)` (including batch deletes)
- Keep `VECTOR` mode untouched (no double-accounting; vector catalog is derived directly).
- Document these semantics explicitly in the governance user guide.

## Tests

Add unit tests in `ai-infrastructure-governance` that validate:

- metadata enrichment always adds stable timestamp keys (`_indexedCreatedAt`, `_indexedUpdatedAt`).
- `SQL` catalog `upsert(...)` is invoked after successful store/update.
- catalog writes are retried on transient failures and propagated after retries are exhausted.
- delete paths correctly update the catalog when removal succeeds.

## Documentation

Add a dedicated “SQL Catalog Consistency Notes” section describing:

- what is guaranteed
- why atomicity across systems is not possible
- why failures propagate (so callers retry and converge)
- recommendation to use `AUTO`/`VECTOR` mode when provider supports scan + metadata filtering

## Non-goals (for this follow-up)

- Two-phase commit / distributed transaction coordination between SQL and vector DB.
- Automatic reconciliation jobs for `SQL` mode (this can be added later if real-world needs emerge).
- Reintroducing AISearchableEntity or any “dual-source-of-truth” core entity store.

## Verification Checklist

- `cd ai-infrastructure-module && mvn verify -Dai.vector-db.lucene.cleanup-on-close=true`
- Run RealAPI IT suites with available keys using `scripts/run-single-test.sh` (optional, but recommended).

