# Relationship-Query: Relational Mode Returns IDs Only (No `AISearchableEntity` Dependency)

## Goal
Make the relationship-query module operate as a **pure relational planner + JPQL executor**:
- **Relational (JPQL) execution returns `ReturnMode.IDS` only** (IDs are the contract; apps fetch/materialize).
- Remove **all runtime + compile-time dependency** on `AISearchableEntity` / `AISearchableEntityStorageStrategy` from relationship-query.
- Keep vector reranking/search optional, but **do not use `AISearchableEntity` for hydration**.

This is an intentional framework-level design choice: relationship-query is about **finding IDs** via relationships, not producing a canonical “AI document” representation.

---

## Current Problem
Today, relationship-query still materializes `RAGDocument.content/metadata` by reading `AISearchableEntity`:
- `LLMDrivenJPAQueryService.materializeDocuments(...)` reads `AISearchableEntityStorageStrategy`.
- `ReliableRelationshipQueryService` uses `AISearchableEntityStorageStrategy` for “FULL” and for the “simple fallback”.
- Several realapi integration tests seed `AISearchableEntity` and assert content-based behavior.

Even with `VectorDatabaseService.scan(...)` + metadata filtering + stable timestamps implemented, **relationship-query remains coupled** to the SQL searchable-entity store.

---

## Target Behavior (After Change)
### Execution contract
- For **relational execution (JPQL traversal)**, the module returns:
  - `RAGResponse.documents = List<RAGDocument(id=<entityId>)>`
  - No attempt to populate `content`, `metadata`, etc.
- If a caller requests `ReturnMode.FULL` for a relational query:
  - The module **forces IDS** and adds a warning explaining that FULL materialization is application-owned.

### Fallbacks
To fully remove `AISearchableEntity` dependency:
- Remove the relationship-query “metadata traversal fallback” (it currently reads `AISearchableEntity`).
- Remove the “simple fallback” that enumerates `AISearchableEntity` rows.
- Vector fallback remains possible (uses vector search results), but should return **IDs only** unless an application provides its own hydration layer.

### Extension point (optional, future)
If later you want FULL responses without `AISearchableEntity`, add an application-owned SPI:
`RelationshipQueryDocumentMaterializer` (e.g. `entityType + ids -> List<RAGDocument>`), but **not in this change**.

---

## Scope
### In-scope
- Update relationship-query module services/config so it no longer references:
  - `com.ai.infrastructure.entity.AISearchableEntity`
  - `com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy`
  - `AISearchableEntityRepository`
- Enforce **relational IDs-only** behavior consistently.
- Update relationship-query integration tests to validate via **JPA repositories** (fetch entities by returned IDs), not via `AISearchableEntity`.

### Out-of-scope (separate changes)
- Removing `AISearchableEntity` from `ai-infrastructure-core`.
- Removing `SearchableEntityVectorDatabaseService` decorator (still syncs SQL store with vector ops).
- Building a general “SQL materialization” layer (mapper-based).

---

## Implementation Plan (Code Changes)
### 1) Enforce IDs-only in `LLMDrivenJPAQueryService`
- File: `ai-infrastructure-module/ai-infrastructure-relationship-query/src/main/java/com/ai/infrastructure/relationship/service/LLMDrivenJPAQueryService.java`
- Changes:
  - Remove `AISearchableEntityStorageStrategy storageStrategy` field + constructor parameter.
  - Replace `materializeDocuments(...)` with an IDs-only materializer.
  - If `ReturnMode.FULL` is requested, return IDs and add a `warnings` entry noting FULL is not supported for relational mode.

### 2) Remove AISearchable-based fallbacks in `ReliableRelationshipQueryService`
- File: `ai-infrastructure-module/ai-infrastructure-relationship-query/src/main/java/com/ai/infrastructure/relationship/service/ReliableRelationshipQueryService.java`
- Changes:
  - Remove imports/fields/constructor params for `AISearchableEntity` + `AISearchableEntityStorageStrategy`.
  - Remove `fallbackToMetadata` path if it requires `MetadataRelationshipTraversalService` backed by `AISearchableEntity`.
  - Remove `fallbackToSimpleSearch` path entirely (and the config toggle if it becomes meaningless).
  - Ensure any remaining fallback returns **IDs-only** documents.

### 3) Update relationship-query Spring configuration
- Files:
  - `ai-infrastructure-module/ai-infrastructure-relationship-query/src/main/java/com/ai/infrastructure/relationship/config/RelationshipQueryConfiguration.java`
  - Any related auto-config wiring
- Changes:
  - Remove beans conditional on `AISearchableEntityStorageStrategy` (metadata traversal service wiring).
  - Ensure the module starts without any AISearchable store present.

### 4) Update public configuration docs/comments
- File: `ai-infrastructure-module/ai-infrastructure-relationship-query/src/main/java/com/ai/infrastructure/relationship/config/RelationshipQueryProperties.java`
- Changes:
  - Remove or rewrite the `fallbackToSimpleSearch` comment (“replays cached AISearchableEntity rows…”).
  - Ensure properties reflect the new responsibility boundary.

---

## Test Plan
### Update relationship realapi integration tests (remove AISearchable dependency)
- File: `ai-infrastructure-module/integration-Testing/relationship-query-integration-tests/src/test/java/com/ai/infrastructure/relationship/it/realapi/ECommerceRealApiIntegrationTest.java`
- Changes:
  - Stop seeding `AISearchableEntityRepository`.
  - Use `ReturnMode.IDS`.
  - Validate results by fetching `ProductEntity` from `ProductRepository` using returned IDs and asserting brand/status/color/price constraints.

### Update any other tests that seed/require AISearchable for relationship-query
- Search and migrate:
  - `ai-infrastructure-module/integration-Testing/relationship-query-integration-tests/...`
  - `ai-infrastructure-module/ai-infrastructure-relationship-query/src/test/...`

### Commands
- Unit tests: `cd ai-infrastructure-module && mvn test`
- Relationship suite: `cd ai-infrastructure-module/integration-Testing/relationship-query-integration-tests && mvn verify`
- Parent verify (CI parity): `.github/workflows/parent-verify.yml`

---

## Acceptance Criteria
- Relationship-query module **compiles and runs** without `AISearchableEntityStorageStrategy` beans.
- Relational execution always returns **IDs-only documents**, even if `ReturnMode.FULL` is requested.
- No relationship-query class imports `AISearchableEntity` or `AISearchableEntityStorageStrategy`.
- Relationship realapi tests validate correctness via **SQL/JPA entities**, not via `AISearchableEntity`.

---

## Migration Notes (User Impact)
- This is a behavioral breaking change for anyone relying on `ReturnMode.FULL` in relationship queries.
- The recommended migration is:
  1) Switch relationship-query calls to `ReturnMode.IDS`
  2) Fetch/materialize from application domain repositories/services
  3) (Optional later) add an application-level “materializer” SPI if you want `RAGDocument.content` returned directly.

