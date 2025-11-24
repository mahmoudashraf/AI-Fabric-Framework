# Extension Guide

How to customize and extend the relationship query module without forking core code.

## 1. Custom Planner
Implement `RelationshipQueryPlanner` to plug in a different LLM, rules engine, or precomputed plan source.
```java
@Component
public class DeterministicPlanner implements RelationshipQueryPlanner {
    @Override
    public RelationshipQueryPlan planQuery(String query, List<String> entityTypes) {
        // Build plan from DSL, knowledge base, etc.
    }
}
```
Register it as a bean; Spring will pick it up instead of the default due to `@ConditionalOnMissingBean`.

## 2. Alternative Vector Store
Provide your own `VectorDatabaseService` bean (e.g., Pinecone, pgvector). Ensure it implements:
- `indexEmbedding`
- `searchByEntityType`
- `clearVectors`

The integration tests set `spring.main.allow-bean-definition-overriding=true` so you can do the same if multiple configs exist.

## 3. Additional Traversal Strategies
- Implement `RelationshipTraversalService`
- Qualify it with a bean name (e.g., `@Qualifier("graphTraversalService")`)
- Inject it into a custom orchestrator or extend `ReliableRelationshipQueryService` to include your stage.

## 4. Metadata Enrichment
Use `AISearchableEntityRepository` to persist additional metadata (tags, risk scores, etc.). Update the vector indexing helper to include those keys so metadata fallback can filter on them.

## 5. Entity Registration
- Use `EntityRelationshipMapper#registerEntityType(Class<?>)` to manually register types that might not be auto-discovered (e.g., DTO projections, read replicas).
- Call `registerRelationship` when the JPA metamodel doesn’t expose the association (such as dynamic joins).

## 6. Builder Hooks
`DynamicJPAQueryBuilder` consumes `RelationshipQueryPlan`. If you need special syntax (CTEs, native queries), wrap the builder and override `LLMDrivenJPAQueryService` to call your version.

## 7. Observability
Plug a different cache or metrics backend by supplying beans for:
- `QueryCache`
- `QueryMetrics`

Example: use Caffeine or Redis for plan caching if you need distributed behavior.

## 8. Security
- Replace `RelationshipQueryValidator` with a subclass that adds extra detection heuristics or ties into an external DLP system.
- Integrate `AISecurityService` rate limiting by adding a request interceptor before invoking `ReliableRelationshipQueryService`.
