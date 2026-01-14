# Proposed Change: Enable Behavior Module Real API Suite (CI + Local)

## Why
The behavior module relies on the LLM provider to generate user insights (trend, sentiment, churn signals, etc.). We want an **opt-in Real API suite** that validates:
- provider wiring (OpenAI/Cohere/Anthropic/Gemini/Azure)
- end-to-end behavior analysis flows using real completions
- resilience behavior under partial provider failures

This suite should be **off by default** in normal CI (cost/rate limits), and **on** in the “Matrix Suite” workflow when keys are present.

## Current State
### What already exists
- Real API tests in:
  - `ai-infrastructure-module/integration-Testing/behavior-integration-tests/src/test/java/com/ai/infrastructure/behavior/it/realapi/*RealApiIT.java`
- A dedicated runner script:
  - `ai-infrastructure-module/integration-Testing/behavior-integration-tests/run-behavior-realapi-tests.sh`
- A Maven profile that runs only realapi tests:
  - `ai-infrastructure-module/integration-Testing/behavior-integration-tests/pom.xml` (`profile id=realapi`)

### Gaps / friction
- The runner supports `LLM:EMBEDDING[:VECTOR_DB]` but currently **does not actually set** the vector-db selection for Spring (it exports `AI_INFRASTRUCTURE_VECTOR_DATABASE`, but does not pass `-Dai.vector-db.type=...`).
- Workflow wiring should consistently:
  - set profiles (e.g. `realapi,testcontainers` when vector DB uses Testcontainers)
  - pass the matrix vector DB into the behavior runner
  - export Pinecone configuration when Pinecone is selected

## Proposed Behavior (Target)
### 1) Make behavior Real API suite runnable via Matrix Suite
In `.github/workflows/provider-suite-keys-only.yml`:
- Use the same provider combination used for integration + relationship-query suites.
- Run behavior tests with:
  - `SPRING_PROFILES_ACTIVE="${{ matrix.realapi_spring_profiles }}"`
  - `run-behavior-realapi-tests.sh "${{ matrix.llm }}:${{ matrix.embedding }}:${{ matrix.vector_db }}"`
- If `vector_db == pinecone`, also export:
  - `AI_PROVIDERS_PINECONE_ENABLED=true`
  - `AI_PROVIDERS_PINECONE_API_KEY`, `AI_PROVIDERS_PINECONE_INDEX_NAME`, `AI_PROVIDERS_PINECONE_API_HOST`

### 2) Ensure runner script truly honors vector DB
Update `run-behavior-realapi-tests.sh` to pass vector DB selection into Spring configuration:
- `-Dai.vector-db.type=$AI_INFRASTRUCTURE_VECTOR_DATABASE`

Optionally, also allow a fallback mapping:
- `-Dai.vector-db.type=${VECTOR_DB_TYPE}` if a workflow prefers that env var.

### 3) Keep default CI safe (no real calls unless requested)
Keep the default `mvn verify` path excluding realapi tests (already true via Failsafe excludes in the behavior IT module).

## Local Usage (Expected)
Run OpenAI LLM + ONNX embeddings (Lucene default):
```bash
cd ai-infrastructure-module/integration-Testing/behavior-integration-tests
export OPENAI_API_KEY=...
./run-behavior-realapi-tests.sh openai:onnx
```

Run Cohere LLM + ONNX embeddings (Qdrant via testcontainers profile):
```bash
cd ai-infrastructure-module/integration-Testing/behavior-integration-tests
export COHERE_API_KEY=...
export SPRING_PROFILES_ACTIVE="realapi,testcontainers"
./run-behavior-realapi-tests.sh cohere:onnx:qdrant
```

Run OpenAI + ONNX + Pinecone:
```bash
cd ai-infrastructure-module/integration-Testing/behavior-integration-tests
export OPENAI_API_KEY=...
export PINECONE_API_KEY=...
export PINECONE_INDEX_NAME=...
export PINECONE_API_HOST=...
./run-behavior-realapi-tests.sh openai:onnx:pinecone
```

## Acceptance Criteria
- Behavior Real API tests can be executed locally via the runner with any supported provider (when the key exists).
- Matrix Suite runs the behavior Real API suite for each resolved provider combination.
- Vector DB selection is honored (e.g., qdrant/milvus/weaviate via testcontainers; pinecone via env vars; lucene locally).
- No realapi behavior tests run during default parent `mvn verify` unless explicitly invoked.

