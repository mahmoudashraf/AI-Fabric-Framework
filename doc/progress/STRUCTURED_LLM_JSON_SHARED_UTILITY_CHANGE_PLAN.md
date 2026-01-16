# Shared Structured LLM JSON Utility (Core) — Change Plan

## Status
Proposed (new change request; complements but is separate from `UNIFIED_INTENT_EXTRACTION_AND_VECTORIZATION_SOLUTION.md`)

## Problem
Multiple modules call LLMs to produce **structured JSON** that we then parse and validate:
- Orchestrator intent extraction (`IntentQueryExtractor`)
- Relationship planning (`RelationshipQueryPlanner`)
- Future modules (security/compliance policies, enrichment pipelines, etc.)

Failures we have already observed in CI/RealAPI:
- **Truncated JSON** (`finishReason=MAX_TOKENS`) → parse errors → fallback paths → empty results / flaky tests.
- **Missing optional fields** from providers → overly strict validators throw (e.g., OUT_OF_SCOPE without `intent`/`action`).
- Provider-specific formatting quirks → brittle one-off “fixes” per module.

Today each module handles these concerns independently with different behaviors and limits.

## Goals
- Provide a **single, production-ready** core utility for “LLM → structured JSON → validated object” flows.
- Make structured output parsing **provider-agnostic** and consistent:
  - JSON extraction (strip wrappers)
  - bounded repair/retry with feedback
  - clear error typing for callers
- Ensure each module can configure:
  - `maxTokens`, `timeout`, bounded retries, and “JSON-only” parameters
  - strictness level (fail-closed vs best-effort fallback)
- Improve test reliability and observability:
  - consistent logs/metrics for parse errors, truncation, retries
  - unit tests for edge cases

## Non-goals
- Building a general agent framework or chain-of-thought planner.
- Eliminating all module-specific prompts (modules still own their domain prompts and schemas).
- Making every structured call share the same model/provider (call sites choose).

## Proposed Solution
Add a shared core component (names tentative):
- `StructuredJsonExtractor` — robustly extracts the first JSON object from provider responses.
- `StructuredJsonCallExecutor` — runs a bounded “attempt → parse → validate → feedback retry” loop.
- `StructuredJsonResult<T>` — normalized output with diagnostics (attempt count, truncated/parse errors, raw snippet hashes).
- Optional: `JsonSchemaLikeValidator` (not JSON Schema itself) to express “required fields by type” patterns.

### API Sketch
```java
public interface StructuredJsonCallExecutor {
  <T> StructuredJsonResult<T> execute(StructuredJsonCallSpec<T> spec);
}

@Builder
public class StructuredJsonCallSpec<T> {
  private final Supplier<AIGenerationResponse> call;   // calls AICoreService / provider
  private final Class<T> targetType;                   // e.g., MultiIntentResponse, RelationshipQueryPlan
  private final Consumer<T> validator;                 // domain validation (throws on invalid)
  private final StructuredJsonRetryPolicy retryPolicy; // maxRetries + feedback builder
  private final StructuredJsonExtractor extractor;     // JSON extraction strategy
}
```

### Retry Policy (bounded)
- Retry only when:
  - parse failure (invalid JSON)
  - validation failure with a known “repairable” reason (missing required field, wrong type)
- Do **not** retry on:
  - explicit refusal / safety block
  - timeouts or transient provider errors beyond existing provider retry logic
- Feedback prompt should be caller-supplied, e.g.:
  - “Your previous output was invalid JSON: <reason>. Return JSON only that matches schema…”

## Phase Plan

### Phase 1 — Core Utility (ai-infrastructure-core)
Add new package (example):
- `com.ai.infrastructure.llm.structured`

Implement:
- JSON extraction (existing patterns reused from `IntentQueryExtractor.extractJsonFromText`)
- parse + bounded retry loop with caller-defined feedback strategy
- normalized diagnostics:
  - `attempts`, `lastFailureType`, `truncatedLikely` (heuristic if response ends abruptly or provider reports MAX_TOKENS when available)
  - sanitized raw payload hashes (not storing full raw by default)

Unit tests:
- extracts JSON from text with leading/trailing prose
- handles truncated JSON (missing closing braces)
- retries once with feedback, then returns a deterministic failure result

### Phase 2 — Migrate Intent Extraction (orchestrator)
Refactor `IntentQueryExtractor` to use the shared utility:
- Keep orchestration-specific prompt building in `EnrichedPromptBuilder`
- Domain validator remains `validateResponse(...)` but moved into the spec `validator`
- Keep existing behavior improvements:
  - tolerate OUT_OF_SCOPE without name fields
  - vectorSpace remains resolvable downstream (VectorSpaceResolutionStep)

Add/adjust unit tests to assert:
- JSON-only parameters are still requested
- OUT_OF_SCOPE tolerance remains
- repair attempt behavior remains bounded

### Phase 3 — Migrate Relationship Planner
Refactor `RelationshipQueryPlanner` to use the shared utility:
- Keep domain prompt and plan examples local
- Move parsing/validation into shared executor steps
- Make `maxTokens`/`timeout` config consistent (already started via `ai.infrastructure.relationship.llm.max-tokens`)
- Add feedback-based retry when parse fails due to truncation/invalid JSON:
  - “Your JSON was truncated/invalid. Return a complete JSON object including required fields…”

Unit/integration tests:
- truncated plan response triggers retry
- configured `maxTokens` is applied to request

### Phase 4 — Adopt in Other Modules (incremental)
As modules add structured LLM output:
- they use the same executor and standard diagnostics
- they decide whether to fail-closed or degrade to fallback

## Configuration
Core defaults (global):
- `ai.llm.structured.max-retries` (default 1)
- `ai.llm.structured.fail-on-parse` (default true/false depending on call site)
- `ai.llm.structured.max-json-chars` (guardrail)

Module overrides (examples):
- `ai.providers.orchestration.*` / `ai.providers.generation.*` (already in unified intent plan)
- `ai.infrastructure.relationship.llm.max-tokens` (already implemented)

## Observability
Add metrics (per call site tags):
- `structured_json.parse_failures`
- `structured_json.validation_failures`
- `structured_json.repair_attempts`
- `structured_json.truncation_suspected`

Log guidelines:
- Never log secrets or full raw provider payload by default.
- Log request id, provider, model, attempt count, and a short sanitized snippet.

## Risks / Tradeoffs
- Shared executor can unintentionally “standardize away” domain-specific requirements if validators are too lax.
  - Mitigation: keep domain validators strict and explicit; shared executor only handles transport/JSON mechanics.
- Increasing `maxTokens` increases cost/latency.
  - Mitigation: per-module config; measure truncation and tune.

## Open Questions
- Should the executor support a “schema envelope” validator (e.g., required keys + type constraints) in addition to domain validator?
- Should we persist structured parse diagnostics to intent history / query metrics for debugging (sanitized)?
- Should retry feedback be standardized (templated) or entirely caller-controlled?

