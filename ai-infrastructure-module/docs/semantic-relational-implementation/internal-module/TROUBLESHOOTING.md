# Troubleshooting Guide

Common issues and fixes when running the relationship query module.

## 1. Lucene `IndexNotFoundException`
- **Symptom:** `DirectoryReader.open` fails on empty indexes.
- **Fix:** Ensure `LuceneVectorDatabaseService` is initialized once; in tests call `IntegrationTestSupport.registerCommonProperties` which creates the directory and dimension metadata. Production deployments should provision the directory before startup.

## 2. `NoSuchFileException: write.lock`
- **Symptom:** Occurs when clearing vectors between test runs.
- **Fix:** Wrap `vectorDatabaseService.clearVectors()` in try/catch (already done) or ensure no parallel test deletes the temp directory while another test is writing. The support helper now ignores this exception so the suite continues.

## 3. Planner Bean Missing
- **Symptom:** `NoSuchBeanDefinitionException` for `RelationshipQueryPlanner`.
- **Fix:** Include `ai-infrastructure-core` (provides `AICoreService` and ONNX auto-config) or define a mocked planner for tests. The integration test configuration registers a Mockito planner if none exists.

## 4. Auto-discovery Collisions
- **Symptom:** `IllegalStateException: Relationship X -> Y already mapped`.
- **Fix:** Disable schema auto-discovery when manually registering relationships by setting `ai.infrastructure.relationship.schema.auto-discover=false` (handled in tests). Alternatively ensure each relationship is only registered once per process.

## 5. Injection Guard False Positive
- **Symptom:** `RelationshipQueryValidationException: Potential injection attempt detected`.
- **Fix:** Check the end-user query for banned patterns (DROP TABLE, UNION SELECT, `' OR 1=1`, etc.). If the query is legitimate, adjust the wording or extend the validator deny-list to skip your specific pattern—but ensure you maintain security coverage.

## 6. ONNX Initialization Warnings
- **Symptom:** “Hugging Face tokenizers library not found… falling back.”
- **Fix:** Informational only. If you want faster tokenization, add `tokenizers` native dependency; otherwise ignore.

## 7. MemorySegment Warning
- **Symptom:** Lucene warns about memory segment index inputs on Java 21.
- **Fix:** This is expected. Disable via `-Dorg.apache.lucene.store.MMapDirectory.enableMemorySegments=false` if necessary.

## 8. H2 SQL Dialect Warning
- **Symptom:** “H2Dialect does not need to be specified explicitly...”
- **Fix:** Harmless; remove `spring.jpa.database-platform` if you prefer. In the shared support class we keep it for clarity.

## 9. Performance Regression
- **Symptom:** Queries exceed the 700ms SLA.
- **Fix Checklist:**
  1. Verify `QueryCache` is enabled and TTLs are not zero.
  2. Confirm embedding and vector services are not re-initialized per request.
  3. Add DB indexes on foreign keys used in `RelationshipPath`.
  4. Use `QueryOptions.limit` to control result size.

## 10. Vector Search Returns Nothing
- **Symptom:** Metadata fallback hits but Lucene vector search returns zero results even though embeddings exist.
- **Fix:** Confirm `ai.vector-db.lucene.vector-dimension` matches the embedding model (384 for MiniLM). Also ensure the metadata ingestion stored the same `entityType` you query for.
