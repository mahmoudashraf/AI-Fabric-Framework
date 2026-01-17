# Relationship-Query: SQL Materialization via Pluggable Mapper (Entity → `RAGDocument`)

## Goal
Add an **optional** materialization layer for relationship-query so callers can request `ReturnMode.FULL` and receive:
- `RAGDocument.content`
- `RAGDocument.metadata` (and optionally other fields)

Materialization must be:
- **Provider-agnostic** (no vector DB dependency)
- **Entity-type agnostic** via an SPI
- **Not dependent on `AISearchableEntity`**
- Efficient (avoid N+1, allow batching)

This is complementary to (not required for) the “IDs-only relational” mode.

---

## Motivation
Relationship-query currently finds IDs via JPQL and then hydrates documents via `AISearchableEntity`.
If we remove `AISearchableEntity`, we need a way to produce FULL responses for apps that want it.

The best framework boundary:
- Framework: plan + execute relationship traversal → produce IDs
- Application: define how an entity becomes an AI “document”

This change formalizes that boundary with a simple SPI.

---

## Target Behavior
### 1) Default behavior (no mapper configured)
- `ReturnMode.IDS`: unchanged (works everywhere).
- `ReturnMode.FULL`: framework returns IDs only + a warning:
  - `"FULL materialization requires a RelationshipQueryDocumentMaterializer; falling back to IDS"`

### 2) With mapper configured
- For relational traversal results:
  - IDs are produced by JPQL traversal
  - Documents are produced by calling the mapper with `(entityType, ids, options)`

---

## Proposed SPI
### 1) Materializer interface
Add to `ai-infrastructure-relationship-query` (or core if you want cross-module reuse):

`RelationshipQueryDocumentMaterializer`
- `boolean supports(String entityType)`
- `List<RAGResponse.RAGDocument> materialize(String entityType, List<String> ids, MaterializationRequest request)`

`MaterializationRequest` should include:
- `ReturnMode returnMode` (FULL vs IDS)
- `Integer limit`
- optional `Set<String> fields` (future)

### 2) Default implementation
`NoopRelationshipQueryDocumentMaterializer`:
- supports nothing
- returns empty list (or throws) so the service can fallback to IDS

### 3) Spring wiring
Auto-config:
- `@ConditionalOnMissingBean(RelationshipQueryDocumentMaterializer.class)` provides Noop
- Allow multiple materializers and resolve by `supports(entityType)` (or a registry)

---

## Built-in Option: Annotation-Based JPA Materializer (No `AISearchableEntity`)
You already have enough annotation metadata to auto-map many entities:
- `@AICapable(entityType=...)` identifies the entityType (`EntityRelationshipMapper` already uses this).
- `@AISearchable` fields can generate `RAGDocument.content`.
- `@AIContext` fields can generate `RAGDocument.metadata` (and respect `includeInResponse`).

The core module already ships a reusable extractor:
- `com.ai.infrastructure.processor.AnnotationFieldScanner` (extracts searchable content + context metadata).

### Proposed built-in materializer
Add a default implementation in relationship-query:
`JpaAnnotationDocumentMaterializer`:
- Uses `EntityRelationshipMapper.getEntityClass(entityType)` to resolve the JPA class.
- Uses JPA to load entities for the requested IDs (batch where possible).
- Uses `AnnotationFieldScanner` to build:
  - `RAGDocument.content` from `@AISearchable` fields (prefer `includeInRAG=true` fields)
  - `RAGDocument.metadata` from `@AIContext` fields where `includeInResponse=true`

### Behavior when annotations are missing
Some entities may only be `@AICapable` without field annotations (this is true in the relationship-query integration test fixtures today).
Pick one of these strategies (decide up front):
1) **Strict**: if no `@AISearchable` fields exist → return IDs + warning (recommended to keep framework honest).
2) **Fallback**: if no `@AISearchable` fields exist → use `toString()` or a configured “title field” from `ai-entity-config.yml` (less breaking but less deterministic).

This change plan assumes **(1) Strict**, unless you explicitly want the fallback.

---

## Implementation Plan (Code)
### 1) Add SPI + request DTO
- New package: `com.ai.infrastructure.relationship.materialization`
  - `RelationshipQueryDocumentMaterializer`
  - `MaterializationRequest`
  - `NoopRelationshipQueryDocumentMaterializer`
  - Optional: `MaterializerRegistry` (select best materializer for entityType)

### 1b) Add built-in annotation-based materializer (optional but recommended)
- New class in relationship-query module:
  - `JpaAnnotationDocumentMaterializer` (wired only when enabled by property, or when it detects annotated fields)
- Dependencies:
  - `EntityRelationshipMapper`
  - `EntityManager` (or `EntityManagerFactory`)
  - `AnnotationFieldScanner`

### 2) Update `LLMDrivenJPAQueryService` (or the facade)
When returnMode == FULL:
- If materializer exists and supports entityType:
  - Call materializer with the limited ID list
  - Return those documents
- Else:
  - Return IDS-only + warning

### 3) Update `ReliableRelationshipQueryService`
Ensure every execution path uses the same materialization mechanism:
- primary relational traversal → IDs → materializer (optional)
- vector fallback → IDs → materializer (optional) OR keep vector metadata results (configurable)

Add a property to control whether vector fallback returns provider metadata vs materialized SQL docs:
- `ai.infrastructure.relationship.vector-fallback.materialize=true|false` (default false)

### 4) Avoid N+1 in JPA materializers
Provide guidance and example implementation:
- Use repository batch methods (`findAllById`)
- Avoid lazy-loading pitfalls (fetch joins or projections)

---

## Example: Product Materializer (in app code)
Apps can implement:
`ProductEntity → RAGDocument`
- `id = product.id`
- `content = "%s (%s) - $%s".formatted(product.name, product.color, product.price)`
- `metadata = {brand: product.brand.name, status: product.status}`

This lives in the **application**, not the framework.

If you adopt `JpaAnnotationDocumentMaterializer`, this example becomes optional—apps only need to add `@AISearchable` / `@AIContext` to the entity fields they want surfaced.

---

## Tests
### Unit tests (relationship-query module)
- When `ReturnMode.FULL` and no materializer → returns IDS + warning
- When materializer present → returns FULL docs and IDs match
- Registry resolution works for multiple entity types

### Integration tests (relationship-query integration suite)
Add a test configuration that registers a `ProductMaterializer` bean and verifies:
- FULL responses contain expected content/metadata
- No dependency on `AISearchableEntity` seeding

---

## Compatibility / Rollout
This is additive:
- Existing apps using IDS-only keep working.
- Apps wanting FULL can adopt the SPI.
- Encourages clean separation: traversal vs presentation.

---

## Acceptance Criteria
- Relationship-query can return FULL without `AISearchableEntity`
- No framework code reads `AISearchableEntity` for materialization
- Materialization is pluggable and test-covered
- Default behavior remains stable (IDS-only works everywhere)
