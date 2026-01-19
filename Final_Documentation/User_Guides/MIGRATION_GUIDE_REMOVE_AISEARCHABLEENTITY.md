# Migration Guide: Remove AISearchableEntity

This guide helps application teams migrate from `AISearchableEntity` to the current architecture where the **vector database is the single source of truth**.

Related ADR:
- `Final_Documentation/ADRs/ADR-0002-Remove-AISearchableEntity-Single-Source-of-Truth-VectorDB.md`

---

## 1) What Changed

### Before
- Indexing persisted duplicated “searchable entity” records in SQL (`AISearchableEntity`) in addition to storing vectors in a vector DB.
- Deletion and retention often required coordinating two systems.
- Synchronization jobs existed to reconcile drift.

### After
- Indexed content + metadata live in the configured `VectorDatabaseService`.
- Operational indexing failure tracking is handled via the indexing queue (`IndexingQueueEntry` / dead-letter), not by “vectorId is null” rows.
- Governance features that require enumeration (retention, deletion discovery) use `ai-infrastructure-governance` via `IndexCatalog`.

---

## 2) Migration Checklist

### Remove any AISearchableEntity dependencies
- Remove custom repositories and code that reads/writes `AISearchableEntity` or its storage strategy abstractions.
- Remove any cleanup/synchronization jobs that existed only to reconcile “SQL vs vector DB” drift.

### Update retention + deletion logic
- If your retention logic previously iterated over `AISearchableEntity`, migrate to governance:
  - enable `ai-infrastructure-governance`
  - use retention cleanup scheduler + `IndexCatalog`
- If your GDPR/CCPA deletion previously searched SQL for metadata, migrate to governance:
  - implement `UserDataDeletionProvider`
  - enable `ai.governance.deletion.enabled=true`

---

## 3) What To Use Instead

### “Is indexed?” checks
Use:
- `VectorDatabaseService.vectorExists(entityType, entityId)`

### Enumerating indexed items for retention/audit
Use:
- Governance `IndexCatalog` (prefer `catalog.mode=AUTO`)

### Tracking indexing failures
Use:
- indexing queue status (`DEAD_LETTER`, retry count, last error)

---

## 4) If You Previously Relied on SQL for Governance/Audit

If you need a relational inventory for audit/reporting, enable governance SQL catalog mode:

```yaml
ai:
  governance:
    enabled: true
    catalog:
      mode: SQL
```

Read the SQL catalog consistency semantics:
- `Final_Documentation/ADRs/ADR-0005-SQL-Catalog-Consistency-Semantics.md`

---

## 5) Notes on Backward Compatibility

The framework follows a greenfield philosophy: deprecated patterns are removed rather than kept indefinitely.

If your application needs a transition period:
- enable governance catalog mode (`AUTO`/`SQL`) for enumeration use cases
- rely on vector metadata for retention/deletion decisions

