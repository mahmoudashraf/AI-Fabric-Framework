# API Reference

This document summarizes the public-facing classes and DTOs that consumers interact with when using the relationship query module.

## 1. Services

### `ReliableRelationshipQueryService`
Primary entry point that wraps planning, execution, and fallbacks.

```java
RAGResponse execute(String query, List<String> primaryEntityTypes);
RAGResponse execute(String query, List<String> primaryEntityTypes, QueryOptions options);
```
- **Parameters**
  - `query` — end-user natural language text.
  - `primaryEntityTypes` — entity type hints (e.g., `["document"]`).
  - `options` — optional `QueryOptions` where you can set `ReturnMode`, `limit`, etc.

### `LLMDrivenJPAQueryService`
Executes a single-plan strategy (no fallbacks). Useful if you need finer control.

```java
RAGResponse executeRelationshipQuery(String query, List<String> types, QueryOptions options);
```

## 2. DTOs

### `RelationshipQueryPlan`
- `originalQuery`
- `primaryEntityType`
- `candidateEntityTypes`
- `relationshipPaths` — list of `RelationshipPath`
- `directFilters` — map of entity → filters
- `queryStrategy` — `RELATIONSHIP`, `SEMANTIC`, etc.
- `returnMode` — `IDS`, `FULL`

Build via Lombok builder:
```java
RelationshipQueryPlan.builder()
    .originalQuery(query)
    .primaryEntityType("document")
    .relationshipPaths(List.of(path))
    .build();
```

### `RelationshipPath`
- `fromEntityType`
- `relationshipType` (the field name such as `author`)
- `toEntityType`
- `direction` — `FORWARD`, `REVERSE`, `BIDIRECTIONAL`
- `optional` — generates `LEFT JOIN` when `true`
- `conditions` — list of `FilterCondition`

### `FilterCondition`
- `field`
- `operator` — `EQUALS`, `ILIKE`, `GREATER_THAN`, etc.
- `value`
- `secondaryValue` — used for `BETWEEN`
- `caseSensitive` — defaults to `true`

### `QueryOptions`
Configure optional behavior per query:
- `returnMode`
- `limit`
- `queryMode`
- `includeMetadata` (future expansion)

## 3. Configuration Properties Exposure
All `RelationshipQueryProperties` fields are exposed via Spring `@ConfigurationProperties` binding. Inject them wherever you need to inspect defaults:
```java
@Autowired
private RelationshipQueryProperties relationshipQueryProperties;
```

## 4. Repositories
Integration tests expose repositories such as `DocumentRepository`, `ProductRepository`, etc., strictly for test seeding. Production usage should rely on your domain repositories marked with `@AICapable`.

## 5. Entity Metadata
`AISearchableEntityRepository` stores vector-ready metadata. Insert records here if you want the metadata fallback to consider non-JPA sources.

```java
AISearchableEntity entity = AISearchableEntity.builder()
    .entityType("document")
    .entityId(documentId)
    .searchableContent(documentContent)
    .metadata("{\"status\":\"ACTIVE\"}")
    .build();
repository.save(entity);
```

## 6. Planner Interface
`RelationshipQueryPlanner#planQuery(String query, List<String> types)` returns a `RelationshipQueryPlan`. Provide custom implementations if you have bespoke LLM orchestration while keeping the downstream execution stack unchanged.
