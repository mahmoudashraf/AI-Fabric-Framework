# RealAPI RAG → Grounded Generation Test — Change Plan

## Status
Proposed

## Problem
We have RealAPI coverage that the orchestrator *routes* to the generation path when `requiresGeneration=true`
(e.g., it calls `performRAGQuery(...)` instead of `performRag(...)`), but we do not have a RealAPI test that
asserts the **LLM-generated answer is grounded in the retrieved context**.

This gap matters because:
- Providers can “answer anyway” even when retrieval returns nothing.
- Regressions can cause the generation prompt to omit/lose retrieved context.
- Changes in orchestration can accidentally switch to generation-only behavior (ungrounded) while still producing a non-empty response.

## Goals
- Add a RealAPI integration test that validates:
  1) Retrieval returns at least one document for the target vectorSpace.
  2) The generated answer includes a **deterministic marker** that exists only in the retrieved content.
  3) The test remains stable across providers/models (OpenAI/Cohere/Gemini/Anthropic) and vector DB types.

## Non-goals
- Enforcing a strict “citation format” (e.g., `[doc1]`) across providers.
- Verifying semantic faithfulness beyond the deterministic grounding marker (that’s a separate evaluation layer).
- Changing production behavior (this is test-only coverage).

## Proposed Design

### 1) Add a dedicated RealAPI test
Create a new test class in the main integration RealAPI suite:
- `ai-infrastructure-module/integration-Testing/integration-tests/src/test/java/com/ai/infrastructure/it/RealAPIRagGroundedGenerationIntegrationTest.java`

### 2) Deterministic marker strategy (minimize flakiness)
Seed a single KB entity whose searchable content contains a unique, unmistakable token, e.g.:
- `KB_GROUNDING_TOKEN=RAG-GROUNDED-GEN-V1`

Then ask a query that forces synthesis and explicitly instructs the LLM to return that token verbatim, e.g.:
- “Search the knowledge base and answer: what is the KB grounding token? Return it exactly.”

**Assertion:**
- `result.getType() == INFORMATION_PROVIDED` and not `ERROR`
- `result.getData().get("requiresGeneration") == true`
- `((RAGResponse) result.getData().get("ragResponse")).getDocuments()` is non-empty
- `result.getMessage()` contains `KB_GROUNDING_TOKEN=RAG-GROUNDED-GEN-V1`

This is provider-agnostic and does not depend on phrasing quality.

### 3) Control the routing deterministically
To avoid provider variance in intent extraction (which can cause OUT_OF_SCOPE or generation-only):
- Mock `ProgressiveIntentExtractionEngine` to return a single `INFORMATION` intent with:
  - `vectorSpace` set to the seeded entity’s vectorSpace (e.g., `test-article` or `test-product`)
  - `requiresRetrieval=true`
  - `requiresGeneration=true`
  - optional `optimizedQuery` (simple, but not required)

The RealAPI call remains “real” for:
- embeddings (if configured as RealAPI)
- vector DB search
- LLM answer generation

### 4) Keep security properties intact
- No secrets should ever be logged by tests.
- The “grounding token” must be a non-secret constant to avoid accidental secret handling.

## Risks & Mitigations
- **LLM refuses to echo token** → Mitigate by phrasing the question as “Return the token exactly” and choosing a benign token format.
- **Retrieval misses seeded entity** → Mitigate by:
  - using a single vectorSpace with deterministic seeding
  - waiting for vector indexing (existing `RealAPITestSupport.awaitVectorExists(...)`)
  - using a query that matches the seeded content closely (e.g., include the token label “KB_GROUNDING_TOKEN”).
- **Provider variability** → Keep assertions minimal (contains token) and avoid strict formatting.

## Acceptance Criteria
- Running the RealAPI suite locally (and in CI matrix) includes a test that fails if:
  - retrieval is empty but generation still returns a message
  - the generation path does not include retrieved context
  - a regression drops the retrieved context before LLM generation
- Test is stable across providers and vector DB configurations used in the matrix.

## How to Run
The test should run under the existing RealAPI runner for integration tests, e.g.:
- `ai-infrastructure-module/integration-Testing/integration-tests/run-realapi-tests.sh "<llm>:<embedding>:<vector_db>"`

