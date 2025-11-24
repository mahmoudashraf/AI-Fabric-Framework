# Architecture Overview

This document outlines the internal topology of the relationship query module and how it integrates with the broader AI infrastructure stack.

## 1. Layers
1. **Entry Points**
   - `ReliableRelationshipQueryService`
   - `LLMDrivenJPAQueryService`
2. **Planning**
   - `RelationshipQueryPlanner` (LLM-backed)
   - `RelationshipQueryValidator` (guards)
3. **Execution**
   - `DynamicJPAQueryBuilder`
   - `JpaRelationshipTraversalService`
   - `MetadataRelationshipTraversalService`
   - `VectorDatabaseService` (Lucene)
4. **Persistence / Metadata**
   - `AISearchableEntityRepository`
   - Domain repositories annotated with `@AICapable`
5. **Observability**
   - `QueryCache`
   - `QueryMetrics`

## 2. Data Flow
```
User Query
   ↓
RelationshipQueryPlanner (LLM)
   ↓ plan
RelationshipQueryValidator → injection guard
   ↓
DynamicJPAQueryBuilder → JPQL + parameters
   ↓
JpaRelationshipTraversalService
   ↓ (if empty)
MetadataRelationshipTraversalService
   ↓ (if empty & allowed)
VectorDatabaseService (Lucene + ONNX embeddings)
   ↓
RAGResponse (documents + metadata + execution stage)
```

## 3. Entity Discovery
- Entities annotated with `@AICapable` are registered automatically by the `RelationshipSchemaProvider` (unless you disable auto-discovery in tests).
- Relationships are inferred from JPA metamodel attributes; manual registration is available via `EntityRelationshipMapper`.

## 4. Auto-Configuration
`RelationshipQueryAutoConfiguration` registers all services conditionally. Downstream applications can override any bean by defining their own implementations (e.g., a different `VectorDatabaseService`).

## 5. Caching & Metrics
- `QueryCache` provides plan/result/embedding caches keyed by SHA-256 hash.
- `QueryMetrics` tracks latency per stage, cache hits, and fallback counters.
- Both services respect `ai.infrastructure.relationship.cache.*` and `metrics.*` properties.

## 6. Security Considerations
- Injection guard at the validator layer (Phase 4.5).
- Rate limiting and threat detection (AISecurityService) live in the parent platform but can wrap this module.
- Logs intentionally capture user query, JPQL, and planner plan for auditability (requested by stakeholder).

## 7. Extensibility Points
- Replace `RelationshipQueryPlanner` to plug in a different LLM or deterministic planner.
- Register additional traversal services implementing `RelationshipTraversalService`.
- Provide custom caches or metrics reporters by overriding the corresponding beans.

## 8. Dependencies
- **Core:** `spring-boot-starter-data-jpa`, `spring-boot-starter-json`
- **AI:** `ai-infrastructure-core` (AICoreService, ONNX embedding provider)
- **Vector:** `ai-infrastructure-vector-lucene`
- **Testing:** H2, Mockito, AssertJ, Spring Boot test starter
