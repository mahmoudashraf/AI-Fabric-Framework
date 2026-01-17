# AI Governance Module — User Guide

Module: `ai-infrastructure-governance`

This module keeps `ai-fabric-core` clean by moving governance/enterprise-oriented features behind an optional dependency:
- Catalog/inventory of indexed items (`IndexCatalog`)
- GDPR/CCPA deletion orchestration
- Retention cleanup scheduling
- Compliance checks + content filtering (pipeline step + services)

## Enablement

```yaml
ai:
  governance:
    enabled: true
```

## Catalog Modes (AUTO/VECTOR/SQL/DISABLED)

Property: `ai.governance.catalog.mode`

- `AUTO` (recommended): selects the best available catalog at runtime
  - Uses `VECTOR` when the configured vector provider supports scan + metadata filtering
  - Else uses `SQL` when JPA is available
  - Else uses `DISABLED`
- `VECTOR`: derives catalog from the vector DB using `VectorDatabaseService.scan(...)`
- `SQL`: persists a minimal catalog (entityType/entityId/vectorId/timestamps/metadata JSON)
- `DISABLED`: disables catalog-driven governance workflows

## Provider Capability Matrix (Scan/Filter/Timestamps)

Governance `AUTO` prefers `VECTOR` only when:
- `VectorDatabaseService.supportsVectorScan() == true`
- `VectorDatabaseService.supportsMetadataFiltering() == true`

| Provider module | `supportsVectorScan` | `supportsMetadataFiltering` | Notes |
|---|---:|---:|---|
| `ai-infrastructure-vector-lucene` | ✅ | ✅ | Efficient scan via Lucene; filtering depends on indexed metadata fields. |
| `ai-infrastructure-vector-memory` | ✅ | ✅ | Intended for tests/dev; scan + filter in-memory. |
| `ai-infrastructure-vector-qdrant` | ✅ | ✅ | Uses Qdrant scroll + payload filters. |
| `ai-infrastructure-vector-weaviate` | ✅ | ✅ | Uses GraphQL paging; metadata keys materialized into schema properties for filtering. |
| `ai-infrastructure-vector-milvus` | ✅ | ✅ | Scan via query paging; metadata filtering uses `metadata like ...` (string-based). |
| `ai-infrastructure-vector-pinecone` | ✅ | ❌ | Scan via list+fetch paging; metadata filtering is client-side (AUTO will prefer SQL catalog when available). |

## Retention Cleanup (Scheduler)

Enable:
```yaml
ai:
  governance:
    enabled: true
    retention:
      enabled: true
```

Configure entity types + retention:
```yaml
ai:
  governance:
    retention:
      enabled: true
      cron: "0 30 3 * * *"
      entity-types: [ "product", "document" ]
      retention-days:
        product: 30
        document: 90
      scan-limit: 200
```

Notes:
- Retention cleanup scans via `IndexCatalog.scan(...)` and deletes using `VectorDatabaseService.removeVector(entityType, entityId)`.
- If you supply a `RetentionPolicyProvider` bean, it is used instead of `retention-days` for per-entry decisions.
- Stable retention timing is based on governance metadata (`_indexedCreatedAt` / `_indexedUpdatedAt`) when governance is enabled.

## PII Under Governance

PII detection remains configured by the PII module (`ai.pii-detection.*`). Governance can “own the toggle” by requiring PII to be enabled when governance PII is enabled.

Enable governance PII coordination:
```yaml
ai:
  governance:
    enabled: true
    pii:
      enabled: true
      require-detection-service: true
      require-pipeline-step: false
```

Then enable the detector itself:
```yaml
ai:
  pii-detection:
    enabled: true
    mode: REDACT
    detection-direction: INPUT_OUTPUT
```

