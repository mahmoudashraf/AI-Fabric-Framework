# Behavior Module Real API Test Plan (Manual Action Target)

Purpose: Define real API integration scenarios to validate the Behavior Module end-to-end in FULL and LIGHT modes, using external event providers and optional custom storage.

Scope
- Module: `ai-infrastructure-behavior`
- Modes: FULL (index + embeddings) and LIGHT (analysis only)
- Entry points: `BehaviorAnalysisService.analyzeUser`, `BehaviorAnalysisService.processNextUser`

Environment Inputs (from workflow dispatch)
- `llm_provider`: openai | azure-openai | cohere | anthropic | rest
- `embedding_provider`: onnx | openai | azure-openai
- `vector_database`: lucene | pinecone | weaviate | qdrant | milvus | memory
- `persistence_database`: h2 | postgresql
- `openai_api_key` (or equivalent provider key)

Test Scenarios (to implement in behavior-integration-tests)
1) Targeted analysis with existing insight (evolution)
   - Given an existing `BehaviorInsights` row with patterns and segment
   - When `analyzeUser(userId)` is called and new events arrive
   - Then returned insight updates segment/patterns, preserves id/createdAt, bumps updatedAt/analyzedAt, and confidence > 0

2) Targeted analysis with no events
   - When `analyzeUser(userId)` receives empty events
   - Then it returns the prior insight unchanged and does not index/store new data

3) Batch processing with userContext
   - Given `processNextUser()` returns a batch with `userContext`
   - When invoked
   - Then the generated insight reflects context in segment/recommendations and is saved; `analyzedAt` is set

4) JSON parsing hardening
   - Force LLM response with leading/trailing text around JSON
   - Expect extractor to parse JSON payload and populate fields; if malformed, service should return fallback with segment “unknown” and confidence 0.0 without throwing

5) Storage adapter routing
   - With custom `BehaviorInsightStore` bean present
   - Ensure `save`, `findByUserId`, `deleteByUserId` route to custom store; default JPA repo untouched

6) FULL mode indexing contract
   - Mode FULL preset applied
   - After `saveAndIndex`, AISearchableEntity is created/indexed (assert via repository or search API depending on infra fixtures)

7) LIGHT mode non-indexing contract
   - Mode LIGHT preset applied
   - After `saveAndIndex`, no AISearchableEntity is created; only persistence layer updated

8) Embedding provider override
   - Run with ONNX embedding provider (default) and at least one remote provider (openai/azure)
   - Ensure embeddings succeed when enabled (FULL mode) and are skipped in LIGHT mode

9) Vector database permutations (smoke)
   - For each configured `vector_database` choice, run the FULL-mode test suite to ensure index/write path succeeds

10) Persistence DB permutations
   - H2 and PostgreSQL profiles: ensure schema for `ai_behavior_insights` aligns with entity mapping and converters

11) Error resilience
   - Simulate LLM failure/timeout => service returns existing insight or fallback (no exception)
   - Simulate ExternalEventProvider throwing => service returns null and logs error; no persistence side effects

Execution Model
- Module: `integration-Testing/behavior-integration-tests`
- Use Spring Boot test slices with real providers based on dispatch inputs
- Failsafe profiles for real API tests; Surefire for fast local mocks if added later
- Tests should clean up created insights and search artifacts between runs

CI Hook
- Manual workflow job `behavior-tests` now runs `mvn -pl integration-Testing/behavior-integration-tests -am -B -V clean verify`
- Once real API tests are implemented, they will execute automatically via the manual action (behavior option)
