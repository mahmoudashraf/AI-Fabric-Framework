# Intent Extraction Provider Shape-Drift Normalization — Change Plan

## Status
In Progress

## Implementation Notes (2026-01-20)
Implemented (core):
- Deterministic normalization in `IntentExtractionPostProcessor`:
  - `CANONICALIZE_ACTION_NAME` (ensures action aliases don’t break downstream action-specific logic).
  - `RELATIONSHIP_QUERY_DEFAULT_QUERY_PARAM` + `RELATIONSHIP_QUERY_STRIP_HINT_PREFIX` (protocol-level hint handling).
  - `RELATIONSHIP_QUERY_COERCE_POST_ACTION_INSTRUCTIONS` (nextStepRecommended.query → generationInstructions when vectorSpace is absent).
  - `NORMALIZE_ENTITY_TYPES` (coerce to `List<String>`, trim, lowercase, remove blanks).
- Normalization diagnostics surfaced via response metadata and progressive extraction attempt events.
- Parsing tolerance hardened in `IntentExtractionJsonSupport` (comments + trailing commas) to reduce provider-specific JSON failures without semantic heuristics.
- CI debug snapshots enriched with safe extraction diagnostics (path/issue codes/normalization rules) via `OrchestrationResultDebugSnapshotStore` when `ai.orchestration.result-normalization.debugSnapshotEnabled=true`.
- Prompt hardening (no heuristics): clarified relationship-query prefix handling and post-action generation field usage in extraction/completion prompts.

Still planned:
- Further prompt hardening to reduce the need for normalization across providers.

## Problem
Different LLM providers (and even the same provider across models/versions) can express the **same intent** using **different fields** or incomplete shapes. This causes:
- flaky RealAPI + matrix runs,
- avoidable runtime failures (missing required action params),
- “smart suggestion” used where the user clearly requested an inline follow-up (e.g., post-action generation),
- pressure to add brittle substring heuristics to compensate.

We need a **provider-agnostic, deterministic normalization layer** that repairs *shape drift* (schema/field placement), without attempting semantic interpretation of natural language.

## Goals
- Normalize extracted intents into a **stable internal contract** across providers.
- Prefer **structural repairs** (schema-level transformations) over substring heuristics.
- Keep the system **LLM-driven**:
  - the LLM decides intent classification and planning,
  - normalization enforces minimal contracts and bridges provider output variance.
- Ensure normalization remains **deterministic** and **does not call the LLM**.
- Add **diagnostics** so we can measure how often repairs happen (per provider/model) and why.

## Non-goals
- Building a natural-language parser to “guess” user intent.
- Rewriting or “improving” user queries beyond explicit protocol-level prefixes.
- Backward compatibility layers for removed concepts (greenfield mindset).

## Scope (Where this runs)
- `IntentExtractionPostProcessor` (primary): runs after intent extraction in orchestration.
- `IntentQueryExtractor` (legacy/compat): align behavior so both code paths produce the same normalized intent shape.

## Principles (Guardrails)
- **No substring heuristics** for semantic splitting (“then summarize…”) or language-specific parsing.
- Allow **protocol-level normalization** only when the user explicitly uses a framework “hint prefix”
  (e.g., `relationship_query:`), because that is an input protocol, not semantics.
- **Fail-closed** security remains unchanged (normalization must not relax access control).
- Normalize for **correctness first**, then add performance optimizations if needed.

## Provider Shape-Drift Taxonomy (Observed / Expected)
1) **Action naming drift**
   - `action="relationship query"` vs `action="relationship_query"` vs `intent="relationship-query"`
2) **Missing required action params**
   - `relationship_query` missing `actionParams.query`
3) **Field placement drift for follow-up steps**
   - “then summarize/explain …” sometimes encoded as:
     - `requiresGeneration=true` + `generationInstructions="..."`, OR
     - `nextStepRecommended.query="..."` (no vectorSpace), OR
     - `nextStepRecommended.intent="summarize"` etc.
4) **Misclassified intent type**
   - RAG requests mislabeled as `ACTION` with unregistered `action`
5) **Unstable OUT_OF_SCOPE shape**
   - missing `intent`/`action` name, but should still be accepted
6) **Collection typing drift**
   - `entityTypes` can be `String` or `List<?>`, with blanks/nulls

## Desired Normalized Contract (Internal)
### ACTION intents
- `action` canonicalized to a registered action name (via `AIActionRegistry` metadata), if possible
- `actionParams` contains required params for the action (or the pipeline fails deterministically)

### relationship_query ACTION (special handling)
- `actionParams.query` MUST exist and must not include the hint prefix.
- Post-action generation request can be expressed via:
  - `requiresGeneration=true` and/or `generationInstructions`, independent of provider.

## Proposed Architecture
### Option A (Recommended): “Normalization Rules” inside IntentExtractionPostProcessor
Add a small internal “rule set” that:
- identifies canonical action via `AIActionRegistry`,
- repairs action params (required fields, canonical formats),
- normalizes cross-field equivalents (e.g., nextStepRecommended.query → generationInstructions) **only for specific actions** where the follow-up is clearly post-action and does not require retrieval.

Pros:
- minimal moving parts
- keeps determinism and avoids new SPIs
- easy to test

Cons:
- rules live in core; grows over time (mitigate with structure + diagnostics)

### Option B: Pluggable “IntentNormalizationRule” SPI
Introduce an SPI (core) where modules can contribute rules:
- `IntentNormalizationRule.applies(intent, context)`
- `IntentNormalizationRule.apply(intent, diagnostics)`

Pros:
- extensible as new actions/modules are added

Cons:
- introduces API surface area and ordering concerns
- requires careful governance to avoid semantic parsing creeping in

## Recommended Implementation (Option A)
### Phase 1 — Formalize a Normalization Rule Matrix
Document (in code + docs) the supported normalization rules:
- Rule ID
- Trigger condition
- Transformation applied
- Safety notes
- Diagnostics emitted

### Phase 2 — Implement Deterministic Rules (Core)
Add/update rules in `IntentExtractionPostProcessor`:
1) `CANONICALIZE_ACTION_NAME`
   - Use `AIActionRegistry.findMetadata(actionName)` to map aliases to canonical action names.
2) `RELATIONSHIP_QUERY_DEFAULT_QUERY_PARAM`
   - If missing/blank `actionParams.query`, default from original user message *after stripping hint prefix*.
3) `RELATIONSHIP_QUERY_STRIP_HINT_PREFIX`
   - Strip `relationship_query:` / `relationship query:` / `relationship-query:` from `actionParams.query`.
4) `RELATIONSHIP_QUERY_COERCE_POST_ACTION_INSTRUCTIONS`
   - If `generationInstructions` already present → set `requiresGeneration=true`.
   - Else if `nextStepRecommended.query` present AND `nextStepRecommended.vectorSpace` is empty
     → set `requiresGeneration=true`, set `generationInstructions=nextStepRecommended.query`.
   - If `nextStepRecommended.vectorSpace` is present → keep as a smart suggestion (do not coerce).
5) `COERCE_MISCLASSIFIED_ACTION_TO_INFORMATION` (already present pattern)
   - If `IntentType.ACTION` but action unregistered AND looks like RAG request → convert to INFORMATION.
6) `NORMALIZE_ENTITY_TYPES`
   - Normalize entityTypes to `List<String>` lowercased, blanks removed.

### Phase 3 — Ensure IntentQueryExtractor stays consistent
Align any mirrored logic (where applicable) so:
- direct extractor callers and orchestrator pipeline callers get the same normalized output.

### Phase 4 — Diagnostics + Metrics
Add structured diagnostics (no PII, no raw user text) to orchestration metadata:
- `normalization.appliedRules=[...]`
- `normalization.ruleCount`
- `normalization.provider=<llmProvider>`, `normalization.model=<model>` (when available)

This enables provider scorecards to capture “how much repair was needed” per provider run.

### Phase 5 — Update Prompts (Reduce Need for Repairs)
Strengthen prompt contract in `EnrichedPromptBuilder` to:
- explicitly instruct the LLM to prefer `generationInstructions` for post-action follow-ups,
- avoid using `nextStepRecommended` for “then explain/summarize the results” (unless it truly needs a follow-up retrieval).

### Phase 6 — Test Coverage
#### Unit tests (core)
- “shape drift” test suite per rule (table-driven):
  - canonicalization, missing required params, nextStep → generation, vectorSpace gate.
- Ensure no rule calls the LLM.
- Ensure rules do not change access control behavior.

#### Integration tests (RealAPI modules)
- Add/adjust a small number of deterministic RealAPI tests where:
  - action runs + post-action generation happens even if provider uses nextStepRecommended.
  - post-action generation is skipped when nextStepRecommended includes vectorSpace.

## Configuration (Optional)
Default behavior should be ON (framework reliability), but allow targeted switches for experimentation:
- `ai.intent-extraction.normalization.enabled=true`
- `ai.intent-extraction.normalization.relationship-query.coerce-next-step-to-post-action=true`
- `ai.intent-extraction.normalization.relationship-query.strip-hint-prefix=true`
- `ai.intent-extraction.normalization.emit-diagnostics=true`

## Rollout Plan
1) Ship normalization + diagnostics enabled by default.
2) Observe provider scorecards:
   - “repairs per run” trend
   - “post-action generation success rate” trend
3) Tighten prompts once real-world drift patterns are confirmed.

## Risks & Mitigations
- **Risk:** Over-coercing nextStepRecommended that was intended as retrieval.
  - **Mitigation:** Only coerce when `nextStepRecommended.vectorSpace` is absent (no retrieval target).
- **Risk:** Masking real extraction failures.
  - **Mitigation:** Emit diagnostics (applied rules + reasons) and keep fail-fast for truly missing required params after normalization.
- **Risk:** Action-specific logic grows.
  - **Mitigation:** Keep rules small, documented, and gated per action.

## Success Criteria
- Matrix suite instability decreases without increasing substring heuristics.
- `relationship_query` chained follow-ups succeed across providers when expressed via either:
  - `generationInstructions`, or
  - `nextStepRecommended.query` (no vectorSpace).
- Diagnostics show a declining rate of repairs as prompts improve.
