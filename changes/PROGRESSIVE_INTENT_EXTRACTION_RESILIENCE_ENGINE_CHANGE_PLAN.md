# Progressive Intent Extraction — Resilience & “Smart” Upgrades (No Substring Heuristics) — Change Plan

## Status
In Progress

## Implementation Notes (2026-01-20)
Implemented:
- Typed, deterministic validator issues (`IssueCode`, `Severity`, `ErrorCategory`) and action required-parameter validation via `AIActionMetaData.requiredParameters`.
- Progressive gating updated to post-process first, then validate (avoids extra LLM calls when deterministic normalization can fix the shape).
- Multi-step extraction extended to optionally fill `actionParams` using registered action metadata (bounded by `maxTotalLlmCalls`).
- New completion step (`CompletionIntentExtractionStrategy`) for contract-incomplete / unsafe outputs, wired into `ProgressiveIntentExtractionEngine` with `ai.intent-extraction.progressive.completion*` properties.
- Diagnostics enriched with validation `issueCodes` and normalization rule IDs per attempt.
- CI debug snapshots enriched with safe extraction diagnostics when `ai.orchestration.result-normalization.debugSnapshotEnabled=true`.
- Prompt hardening (no heuristics): strengthened contract guidance for post-action generation (`generationInstructions`) and relationship-query hint handling.

Still planned:
- Further prompt hardening as more provider drift patterns are observed.
- Provider matrix scorecard aggregation for repair/completion usage rates beyond the last snapshot per run.

## Problem
Intent extraction currently depends on LLMs producing a perfectly shaped JSON response in a single shot. In practice, providers vary in:
- schema/field placement (“shape drift”),
- output structure validity (malformed JSON, missing fields),
- action naming and param completeness,
- encoding multi-part requests (“do X then Y”) inconsistently.

We already have `ProgressiveIntentExtractionEngine` (compound → repair → multi-step), but we can make it **more resilient and smarter** by:
- using validator-guided fallbacks,
- improving the multi-step decomposition to fill action params reliably,
- collecting diagnostics/metrics to learn which providers need which path,
- doing all of this **without substring heuristics** for semantic parsing.

## Goals
- Make intent extraction **provider-agnostic** and **stable** under real-world drift.
- Keep the system **LLM-driven** (LLM decides semantics; code enforces contracts).
- Prefer **deterministic contract normalization** and **structured LLM repair/completion** over string-based heuristics.
- Enforce **bounded cost** (maxTotalLlmCalls) and predictable latency.
- Improve observability: capture which fallback path was used and why.

## Non-goals
- Building a rule-based natural language parser to “understand” the query.
- Semantic substring parsing of user text (e.g., splitting on “then”, “and”, language-specific patterns).
- Adding action-specific “if query contains …” heuristics (except explicit protocol hints/prefixes like `relationship_query:`).

## Current Architecture (Baseline)
### Progressive ladder
`ProgressiveIntentExtractionEngine`:
1) `CompoundIntentExtractionStrategy` (1 call)
2) `RepairIntentExtractionStrategy` (≤ repairMaxAttempts, structural fix only)
3) `MultiStepIntentExtractionStrategy` (classification + optional action selection)

### Deterministic post-processing
`IntentExtractionPostProcessor` performs deterministic normalization (no extra LLM calls).

### Validator
`IntentExtractionValidator` validates basic structure + warns on unknown actions.

## Observed Failure Modes (Examples)
1) **Valid JSON but missing required params**
   - Action executes but fails later (e.g., missing `actionParams.query` for `relationship_query`).
2) **Follow-up instruction encoded inconsistently**
   - Provider emits “then summarize/explain …” as `nextStepRecommended` instead of `generationInstructions`.
3) **Misclassified action intent**
   - “summarize …” mis-labeled as ACTION with unknown action name.
4) **Structural JSON issues**
   - parse failures, partial JSON, code fences, trailing commentary.
5) **Budget/latency pressure**
   - fallback ladder uses more calls; needs hard limits and good early exits.

## Proposed Upgrades (No Substring Heuristics)

### Phase 1 — Strengthen Contract Validation (Typed Issues, Not String Scanning)
Upgrade `IntentExtractionValidator` to emit structured issues, e.g.:
- `ValidationIssue(code, severity, intentIndex, field, message)`
- `ErrorCategory` derived from codes (STRUCTURAL / UNSAFE / INCOMPLETE / NONE)

Key validations to add (contract-level, not semantic):
- ACTION: action name must resolve to a registered action (or be downgraded to INFORMATION where safe).
- ACTION: required params can be validated via action metadata (see Phase 3).
- INFORMATION: if `requiresRetrieval=true`, vectorSpace should be present OR explicitly defer to VectorSpaceResolution.

Deliverables:
- Add typed validation model + unit tests.
- Ensure validator output is used for deciding whether to go to repair/completion.

### Phase 2 — Reorder: Post-Process First, Then Validate
Today, each strategy validates before the orchestrator post-processing step runs. For resilience:
- Parse → **post-process (deterministic normalization)** → validate → decide “success”.

This reduces unnecessary repair/multi-step calls when deterministic normalization can fix the shape.

Deliverables:
- Update strategy/engine flow so post-processing participates before validation gating.
- Keep a strict “no LLM calls” guarantee for post-processing.

### Phase 3 — Make Multi-step “Smart” Using Action Metadata (Still LLM-Driven)
Enhance `MultiStepIntentExtractionStrategy` so it doesn’t just classify/select actions, but can also produce:
- canonical action name (from allowed list),
- complete `actionParams` for that action, using the action’s parameter spec.

Approach:
- Generate a per-action “parameter spec” from `ActionHandlerRegistry.getAllMetadata()`:
  - param names, descriptions, and required flags (where available).
- Add an additional multi-step call only when needed (and budget allows):
  - `intent_extraction_multi_step_fill_params`
  - Input: original query + selected action + parameter spec
  - Output: `actionParams` JSON only

Design constraints:
- No semantic substring parsing in code.
- LLM does the extraction; code only provides constraints/schema and validates.
- Hard budget: respect `maxTotalLlmCalls` (cost guardrail).

Deliverables:
- Extend multi-step schema to support `generationInstructions` for ACTION when requested.
- Add “fill params” step with strict JSON schema and provider-agnostic JSON-only enforcement.
- Add tests with mocked providers returning partial results.

### Phase 4 — Add a “Completion” Step (Not Structural Repair)
Add a new progressive step between repair and multi-step (or after multi-step):
- **Completion** is used when output is structurally valid but contract-incomplete.

Completion prompt characteristics:
- Provide original query + partial JSON + validator issues + allowed actions metadata.
- Ask LLM to fill *missing required fields*.
- If it cannot fill safely, it must return:
  - `CLARIFICATION_REQUIRED` style intent (or OUT_OF_SCOPE) with a safe follow-up question.

Key: completion is LLM-based, not heuristic. It is gated by validator signals and budget.

Deliverables:
- New strategy: `CompletionIntentExtractionStrategy`
- New property toggles:
  - `ai.intent-extraction.progressive.completionEnabled`
  - `ai.intent-extraction.progressive.completionMaxAttempts`
- Tests for:
  - “missing required params” fixed by completion,
  - “cannot infer” → clarification intent.

### Phase 5 — Canonical Diagnostics (Learn Which Providers Need Which Path)
Improve extraction diagnostics:
- Per attempt:
  - strategy name, llmCalls, validation issue codes, provider/model (if available)
- Summaries:
  - chosen path, total llmCalls, “repairs applied”, “completions applied”

Integrate into existing scorecard outputs used by provider matrix suite.

Deliverables:
- Extend diagnostics model emitted by `ProgressiveIntentExtractionEngine`.
- Ensure RealAPI matrix suite can report: “% of runs using repair/completion/multi-step”.

### Phase 6 — Prompt Hardening (Reduce Repairs Without Heuristics)
Update `EnrichedPromptBuilder` and multi-step prompts to:
- prefer `generationInstructions` for post-action follow-ups (vs. `nextStepRecommended`) when no retrieval is needed,
- require `actionParams` completeness for selected actions,
- explicitly forbid inventing actions and vector spaces,
- include a “do not wrap JSON in markdown” rule and JSON-only response parameters (already supported).

Deliverables:
- Prompt updates with test coverage (assert prompt contains key constraints).

## Configuration Additions (Proposed)
Under `ai.intent-extraction.progressive.*`:
- `completionEnabled` (default true)
- `completionMaxAttempts` (default 1)
- `minConfidenceForDirectAction` (optional; default keep current behavior)

Under `ai.intent-extraction.diagnostics.*`:
- `emitNormalizationRules=true`
- `emitValidationIssueCodes=true`

## Testing Plan
### Unit tests (core)
- Table-driven tests covering:
  - compound success
  - structural failure → repair → success
  - contract-incomplete → completion → success
  - multi-step param filling
  - budget enforcement: ensure total calls never exceed `maxTotalLlmCalls`

### Integration / RealAPI tests
- Add at least one RealAPI IT per suite that:
  - triggers completion path deterministically (provider output missing required params),
  - asserts the final result is successful and diagnostics show the path used.

## Success Criteria
- Reduced “hard failures” due to contract-incomplete intents (missing required params).
- Lower repair rate over time as prompts improve.
- Stable behavior across providers without introducing semantic substring parsing.
- Scorecards show:
  - extraction success rate ↑
  - average llmCalls bounded and predictable
  - completion usage primarily for providers with known drift.
