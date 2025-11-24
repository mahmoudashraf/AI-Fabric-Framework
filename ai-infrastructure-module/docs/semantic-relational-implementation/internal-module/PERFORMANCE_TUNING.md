# Performance Tuning

Checklist for meeting the <700 ms per-query goal defined in the plan.

## 1. Cache Effectively
- Keep `ai.infrastructure.relationship.enable-query-caching=true`.
- Tune region TTLs: hot plans benefit from longer TTL, whereas embeddings can have longer TTL (default 24h).
- Consider distributed caches (Redis, Hazelcast) by replacing `QueryCache`.

## 2. Pre-register Schema
Running `RelationshipSchemaProvider.refreshSchema()` during startup avoids on-demand metamodel scans that could add 100+ ms to the first query.

## 3. Optimize Database Access
- Add indexes on columns used in `FilterCondition` and join columns referenced in `RelationshipPath`.
- Use connection pooling (HikariCP already configured) and monitor pool saturation.
- For read-heavy workloads, enable second-level cache if your JPA provider supports it.

## 4. Limit Result Sets
- Set `QueryOptions.limit` per request.
- Apply precise filters rather than relying on vector fallback for filtering.

## 5. Vector Search
- Store embeddings once at write time rather than regenerating on every query.
- Use ONNX CPU inference; if you need throughput, switch to GPU-backed providers via a custom `AIEmbeddingService`.
- Adjust `ai.vector-db.lucene.similarity-threshold` to balance recall/precision and avoid large candidate sets.

## 6. Planner Latency
- `min-confidence-to-execute` can be raised to reduce retries.
- Enable `planner.log-plans` temporarily to identify expensive plan structures.
- Cache LLM outputs externally if you have deterministic queries (e.g., run-time template plans).

## 7. Parallelizing Traversal
`ReliableRelationshipQueryService` executes fallbacks sequentially to preserve determinism. If you need aggressive latency, derive a custom service that fires metadata/vector searches in parallel and merges the first successful response.

## 8. Profiling
- Use `QueryMetrics` snapshots (already recorded in tests) to capture per-stage timing.
- Attach Flight Recorder or async profiler when running integration tests under load.

## 9. CI Performance Suite
- Re-enable `PerformanceTest` (currently skipped by user request) when you want automated regression detection. The test stresses 150+ query executions and reports average latency.
