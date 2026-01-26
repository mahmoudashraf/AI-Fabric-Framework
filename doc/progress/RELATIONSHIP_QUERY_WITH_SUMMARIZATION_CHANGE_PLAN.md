# Relationship Query + LLM Summarization (Chained Execution) — Change Plan

## Status
Proposed (new change request; separate from `UNIFIED_INTENT_EXTRACTION_AND_VECTORIZATION_SOLUTION.md`)

## Problem
Users ask for “relational query first, then summarize the results”. Today we support the relational part via the `relationship_query` action, but we do **not** reliably perform the second step (LLM summarization) using the action results.

Additionally, current normalization for relationship queries intentionally strips trailing non‑relational directives (e.g., “then summarize/explain”) from `actionParams.query`, which prevents a single request from producing both:
1) relational results, and
2) a narrative summary based on those results.

## Goals
- Support single-turn queries that require:
  - **Step 1:** execute `relationship_query` against allowed entity types, and
  - **Step 2:** pass the resulting relational data to the LLM for **generation** (summary / explanation / recommendation).
- Use **purpose-based routing**:
  - Orchestration LLM for intent extraction/planning.
  - Generation LLM for summarization (`LlmPurpose.GENERATION`).
- Keep output deterministic where possible:
  - Stable result typing (`OrchestrationResultType`) and error codes.
  - Clear metadata/diagnostics to debug which step failed.
- Preserve security contracts:
  - Access control remains fail-closed.
  - PII detection/sanitization continues to apply to generated summaries.

## Non-goals
- Multi-turn conversational memory beyond existing intent history.
- Arbitrary post-processing pipelines (only relational→summary chain).
- Building a full SQL/DSL planner (we reuse existing `relationship_query`).

## Target User Stories / Example Queries
1) “Get 10 products from brand Nike and give me a summary of them.”
2) “Relationship query: show all customers who bought product X in last 30 days, then summarize insights.”
3) “Find orders with refunds and chargebacks, then explain likely causes.”

## Desired Behavior
### Success path
- System executes `relationship_query`, obtains structured results (documents/rows/ids + metadata), then generates a summary grounded in those results.
- Response includes:
  - The raw/structured relational results (or a bounded subset),
  - The generated summary,
  - Diagnostics: which providers were used, how many LLM calls, row/doc counts.

### No-results path
- `relationship_query` returns zero results:
  - Return a truthful response: “No results found”, and (optionally) a short explanation of what was searched.
  - Summarization step must not hallucinate missing data.

### Access-denied path
- If policy denies requested entity types:
  - Fail-closed as today.
  - No summarization step is run.

## Architecture Options
### Option A (Recommended): ACTION intent supports “post-action generation”
1) Intent extraction returns an ACTION intent:
   - `action = "relationship_query"`
   - `actionParams.query`, `actionParams.entityTypes`
   - `requiresGeneration = true`
   - `optimizedQuery` / `generationInstructions` (new optional field) guides the summary.
2) Orchestrator executes the action handler.
3) If `requiresGeneration=true`, orchestrator builds a bounded “generation context” from the action result and calls LLM generation.

Pros:
- Single intent covers both steps; less reliance on compound intent correctness.
- Deterministic chaining: always summarize the action output if requested.
Cons:
- Requires schema/prompt extension to carry “generation instructions” for ACTION.

### Option B: COMPOUND intent with two children (relationship_query + summarize)
- Extractor returns two intents:
  1) ACTION relationship_query
  2) INFORMATION “summarize_results” referencing prior output

Pros:
- Keeps action query text “pure”.
Cons:
- Requires cross-intent dependency modeling (how does intent 2 reference intent 1 results?).
- More LLM flakiness in extraction.

## Proposed Implementation (Option A)

### Phase 1 — Contract & Prompt Updates
- Extend intent extraction schema to carry summary instructions for post-action generation.
  - Add optional `generationInstructions` (string) OR `postActionSummary` (boolean + optional style).
  - Allow `requiresGeneration=true` for ACTION intents (currently used primarily for INFORMATION).
- Update:
  - System prompt schema used by orchestration extraction.
  - Validation/post-processing to accept the new field and keep it bounded (max length).
- Update relationship query hint handling:
  - Continue stripping “then summarize/explain” from `actionParams.query` (to keep the relational query clean),
  - But preserve the stripped suffix into `generationInstructions` (so generation still happens).

### Phase 2 — Orchestrator Chaining Logic
- In `IntentHandlingStep` (or a new dedicated pipeline step immediately after action execution):
  - Detect `intent.type == ACTION && intent.action == "relationship_query" && requiresGeneration == true`.
  - Execute the action handler as today.
  - Build a **generation context payload**:
    - Use returned documents/rows/ids; keep it bounded:
      - `maxItemsForPrompt` (config)
      - `maxCharsForPrompt` (config)
    - Include an explicit JSON “facts” block with the bounded results.
  - Call `AICoreService.generateContent(..., LlmPurpose.GENERATION)` with:
    - system prompt: “Summarize only from provided facts; if insufficient say so.”
    - user prompt: `generationInstructions` + “facts” payload.
  - Return a result that includes both:
    - `actionResult` data (structured), and
    - `summary` (string), plus optional `citations`/ids.

### Phase 3 — Result Normalization & Metadata
- Ensure `OrchestrationResultNormalizationStep` can normalize this composite shape deterministically.
- Add stable metadata keys:
  - `relationshipQuery.executed=true`
  - `relationshipQuery.returnedResults`
  - `postActionGeneration.used=true`
  - `postActionGeneration.llmProvider`, `model`, `purpose=GENERATION`
  - `postActionGeneration.truncated=true/false`

### Phase 4 — Configuration Flags
Add a dedicated config block (names tentative):
- `ai.relationship-query.post-action-generation.enabled` (default `false` initially)
- `ai.relationship-query.post-action-generation.max-items` (default `10`)
- `ai.relationship-query.post-action-generation.max-chars` (default `12000`)
- `ai.relationship-query.post-action-generation.temperature` (default `0.2`)
- `ai.relationship-query.post-action-generation.timeout-seconds`

### Phase 5 — Test Coverage (Integration + RealAPI)
#### Unit tests (core module)
- Prompt/context builder boundedness (max items/chars).
- “No results” handling (no hallucination; summary explains no results).
- Error propagation (generation failure → deterministic error code).

#### Integration tests (relationship-query-integration-tests)
- Mock relationship query service returning structured results → verify summarization call happens and uses provided results.
- Mock access control denying entity types → verify generation not called.

#### RealAPI tests (integration-tests + manual matrix)
- Add a RealAPI test that seeds relational data and exercises:
  - relationship query execution,
  - summarization output grounded in returned records,
  - purpose routing (`GENERATION`).
- Add this test to the provider-matrix suite (manual action) as a separate chunk, because it’s costlier than basic tests.

## Rollout Plan
- Ship behind feature flag (`enabled=false`).
- Enable in dev/staging with strict caps on rows/chars.
- Add telemetry dashboards/alerts based on:
  - generation failure rate,
  - truncation rate,
  - latency p95 for chained requests.

## Open Questions
- Output contract: should this be `ACTION_EXECUTED` with a `summary`, or `INFORMATION_PROVIDED` with embedded relational results?
- Should we store the bounded relational “facts payload” in history (sanitized) for debuggability?
- How should “top 10” requests map to relationship query limits across providers (strict vs best-effort)?

