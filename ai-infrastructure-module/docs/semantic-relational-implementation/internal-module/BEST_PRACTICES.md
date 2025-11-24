# Best Practices

Guidelines for operating the relationship query module in production.

## 1. Keep Queries Auditable
- Log user query, JPQL, and planner plan (already done in integration tests) to meet compliance requirements.
- Redact sensitive parameters before emitting logs when dealing with PII.

## 2. Validate Inputs
- Leave `enable-query-validation=true`.
- If routing queries from untrusted sources, run them through `RelationshipQueryValidator` before reaching the planner.

## 3. Seed Metadata
- Ingest entity metadata (`AISearchableEntity`) as part of data pipelines so metadata/vector fallbacks are immediately useful.
- Include business-specific keys (client type, risk score, etc.) to support filtering.

## 4. Control Traversal Depth
- Increase `max-traversal-depth` only when you have tests covering deeper graphs. Wider traversals can explode join cardinality.

## 5. Observe Fallbacks
- Monitor `QueryMetrics` counters (`fallbackMetadataCount`, `fallbackVectorCount`, etc.). A sudden spike indicates the primary JPA stage is failing or under-filtered.

## 6. Dependency Hygiene
- Use pinned versions of ONNX models/weights to avoid unexpected embedding dimension changes.
- Clear temporary Lucene directories on shutdown to prevent disk bloat.

## 7. Testing Strategy
- Keep the provided use-case tests as smoke tests in CI.
- Add domain-specific integration tests following the same pattern (seed data → mock planner → assert response).

## 8. Security
- Combine this module with the platform’s `AISecurityService` rate limiting and threat detection.
- Regularly audit log outputs for attempted prompt/SQL injections.

## 9. Documentation & Knowledge
- Update `USE_CASE_EXAMPLES.md` whenever you add a new domain scenario.
- Regenerate JavaDoc (`mvn -pl ai-infrastructure-relationship-query javadoc:javadoc`) after public API changes.
