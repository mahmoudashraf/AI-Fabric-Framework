# Unified Orchestration Stabilization Plan
## Progressive Intent Extraction + VectorSpace Resolution + Result Normalization

**Status:** Proposal (consolidates existing enhancement docs)  
**Audience:** Framework maintainers + contributors  
**Scope:** Provider-agnostic stabilization across LLMs and vector databases  

This document consolidates and reconciles the following enhancement proposals into a single coherent system design:
- `changes/PROGRESSIVE_INTENT_EXTRACTION_FALLBACK_PLAN.md`
- `changes/intent-vectorspace-fallback-options.md`
- `changes/vectorspace-inference-and-routing-recommendation.md`
- `changes/ORCHESTRATION_RESULT_NORMALIZATION.md`

Framework guidance referenced:
- `Final_Documentation/Development_Guides/AI_FABRIC_FRAMEWORK_PHILOSOPHY.md`
- `Final_Documentation/Development_Guides/CODE_REVIEW_PROMPT.md`

---

## 1) Problem Summary (One Problem, Three Failure Surfaces)

The linked documents all describe one reliability problem: **LLM/provider outputs are not stable enough to be treated as a deterministic contract**.

That instability shows up in three surfaces:

1) **Intent extraction instability**
   - Some providers return malformed/truncated JSON, or structurally valid JSON with missing required fields.
   - “Repair” flows may still produce incomplete payloads.

2) **Routing instability (`vectorSpace`)**
   - Missing/blank `vectorSpace` can crash retrieval downstream (some vector providers hard-fail with `entityType=null`).
   - Heuristic “largest-count” routing can silently misroute in large multi-domain knowledge bases.

3) **Result shape instability**
   - Different providers wrap identical outcomes differently (compound wrappers, wording differences).
   - Tests become flaky when they assert provider-dependent wrappers/messages rather than system facts.

The unified solution below addresses all three without introducing provider-specific logic into the orchestration core.

---

## 2) Goals / Non-Goals

### Goals
- **Provider-agnostic determinism** for the public result contract: stable `type`, stable `success`, stable `errorCode` for deterministically classifiable failures.
- **No retrieval hard-fails** due to `vectorSpace=null/blank` when `requiresRetrieval=true`.
- **No silent correctness regressions** for multi-domain KBs (avoid “largest-count routes everything” as a production default).
- **Bounded fallback behavior** (no unbounded retries/loops).
- **Observable behavior**: which fallback path was used and why (structured, non-PII diagnostics).

### Non-Goals
- Do not use repair loops to fix *semantic correctness* (“pick a better action”) via repeated reasoning attempts.
- Do not add provider-specific extraction/routing code to orchestration core.
- Do not weaken security: remain **fail-closed** where access/authorization is involved.

---

## 3) Design Principles (from framework philosophy)

1) **Treat LLM outputs as untrusted input**
   - Parse + validate + constrain deterministically.

2) **LLM decides, configuration constrains**
   - Config can cap cost/latency and disable features, but should not blindly override query-specific LLM analysis.

3) **No silent misrouting**
   - For multi-domain knowledge bases, “confident-but-wrong” is worse than clarification.

4) **System-fact-driven normalization**
   - Normalize based on system facts (registry existence, child error types), not on provider wrapper shapes or prose.

---

## 4) Unified Architecture: “Ladder + Resolver + Normalizer”

The unified solution is a 3-stage stabilization layer on the existing orchestration pipeline:

1) **Intent Extraction (Progressive Ladder)**: compound → bounded repair → multi-step.
2) **VectorSpace Resolution (Routing Resolver)**: guarantee a safe retrieval plan before calling any RAG provider.
3) **Orchestration Result Normalization (Contract Normalizer)**: enforce provider-agnostic final result invariants.

### Proposed step placement (pipeline-level)
Keep security gates unchanged. Insert a dedicated routing step between intent extraction and intent handling:

- Order 50: `IntentExtractionStep` (existing)
- **Order 55: `VectorSpaceResolutionStep` (new)**
- Order 60: `IntentHandlingStep` (existing)
- Order 65: `OrchestrationResultNormalizationStep` (existing)

Rationale:
- Intent extraction should not own retrieval policies.
- Retrieval must never run with `vectorSpace` missing.
- Normalization should operate on the final “raw” system outcome produced by intent handling.

---

## 5) Stage 1 — Progressive Intent Extraction (Compound → Repair → Multi-Step)

### Key idea
Prefer the simplest/cheapest structured extraction, then progressively increase determinism only when needed.

### Definitions
- **Structural failure**: invalid JSON, schema mismatch, wrong field types, missing required fields, truncated payload.
- **Semantic failure**: structurally valid but logically wrong (hallucinated action, wrong intent classification).

Repair is only for **structural failures**.

### Ladder behavior
1) **Compound fast-path (single request)**
   - Request a single structured JSON response for `MultiIntentResponse`.
   - Parse + validate deterministically.

2) **Repair loop (schema-corrector, bounded)**
   - Provide machine-generated validation errors + a bounded snippet of malformed output.
   - Require “JSON only” output.
   - Default: max attempts = 1 (allow 2 only if measured benefit is strong).

3) **Multi-step extraction (decomposed prompts)**
   - Step 1: classify intent type + retrieval/generation booleans.
   - Step 2 (if ACTION): select `action` from AVAILABLE ACTIONS (hard constraint).
   - Step 3 (if INFORMATION): produce retrieval requirements including `vectorSpace`.
   - Step 4 (optional): relationship-query planning for `relationship_query`.

### Deterministic validator (required)
A single validator should produce:
- `ok` vs `structural_error` vs `unsafe` (semantic/guardrail failures)
- machine-readable `errors[]` (field path + reason)

Minimal invariants:
- At least one intent exists.
- Each intent has a valid type.
- `ACTION` intents: action exists and is grounded in the registry (or deterministically coercible by guardrails).
- `INFORMATION` intents: if `requiresRetrieval=true`, required retrieval fields exist (final `vectorSpace` resolution is owned by Stage 2).

### Outputs (additive metadata only)
The extraction layer should attach **metadata**, not change public result types:
- `intentExtraction.path = compound|repair|multi_step`
- `intentExtraction.attemptCount`
- `intentExtraction.failureCategory` (only when fallback occurred)

---

## 6) Stage 2 — VectorSpace Resolution (Routing Resolver)

### Key idea
Guarantee: if an intent requires retrieval, the framework resolves a safe retrieval plan before calling any RAG provider.

This stage exists because:
- LLM output may omit `vectorSpace`.
- Heuristics can silently misroute at scale.
- Retrieval should never run with `vectorSpace` missing.

### Resolution policy (recommended default for multi-domain KBs)
1) If KB has exactly 1 space → **auto-assign** it.
2) Else → prefer **bounded fan-out** (coverage-first with bounded cost).
3) If fan-out confidence is weak → **ask for clarification** (correctness-first).
4) Keep heuristic-only routing (“mention match → largest-count → first”) as last-resort crash-prevention, not the production default.

### Candidate discovery (system facts)
Use `KnowledgeBaseOverview` when available:
- `entityTypes`
- `documentsByType`

If overview is unavailable:
- prefer “need clarification” (deterministic), or
- allow heuristic-only mode if explicitly configured for best-effort operation.

### Fan-out behavior (bounded)
When `vectorSpace` is missing or low-confidence:
- Select top `N` candidate spaces.
- Perform small retrieval per space (`topK=2..5`) with strict caps.
- Merge results and continue with generation using merged context.

Notes:
- Mixing vector DB/provider score scales may require normalization to compare across spaces.
- If score normalization is not available, preserve per-space scoring and use conservative selection.

### Clarification behavior (deterministic)
When the system cannot confidently select a space and fan-out is disabled/weak:
- Return a deterministic “need clarification” result including:
  - candidate spaces
  - a product-owned question asking the user to choose a domain
  - structured metadata for clients (no provider-prose dependency)

### Observability (non-PII)
Emit routing metadata:
- `routing.vectorSpace.policy = single|fanout|clarify|heuristic`
- `routing.vectorSpace.wasMissing = true|false`
- `routing.vectorSpace.candidates = [...]` (bounded list)
- `routing.vectorSpace.selected = <space>` (when single selected)
- `routing.vectorSpace.fanout.maxSpaces`, `routing.vectorSpace.fanout.topKPerSpace`

---

## 7) Stage 3 — Orchestration Result Normalization (Provider-Agnostic Contract)

### Key idea
Normalize final results using system facts only. Never depend on provider wrapper types or prose to be “correct”.

The canonical result contract:
- `type` (stable top-level outcome)
- `success` (derived from system facts)
- `errorCode` (stable identifiers for deterministic failures)
- `message` (product-owned where possible; avoid provider-dependent wording for correctness)

### Important reconciliation (doc vs code)
The current `OrchestrationResultNormalizer` implementation includes a deliberate exception:
- For `COMPOUND_HANDLED`, if a **primary** child succeeded and a **non-primary** child is a known “soft error” (e.g., `ACTION_NOT_FOUND` from misclassified “summarize/explain”), normalization promotes the primary success rather than sinking the whole request to `ERROR`.

This behavior should be documented as part of the contract because it impacts test assertions and client expectations.

### Normalization rules (deterministic)
1) **Missing action handler → canonical error**
   - `type=ERROR`, `success=false`, `errorCode=ACTION_NOT_FOUND`

2) **Compound normalization**
   - Choose a primary child deterministically (actions > information > scope).
   - If primary is non-ERROR:
     - If there is a classifiable “soft” child ERROR:
       - keep primary outcome
       - attach `metadata.softChildErrorCode` to preserve the detail
     - If there is a hard child ERROR:
       - bubble to top-level `ERROR` with canonical `errorCode`

3) **Non-compound with child ERROR**
   - Bubble the first child `ERROR` deterministically.

4) **ERROR without errorCode**
   - Derive `errorCode` where deterministically possible (system facts, known patterns).

### Testing guidance (contract assertions)
Integration tests should:
- Assert `type`, `success`, and `errorCode` invariants.
- Avoid asserting provider prose or provider wrapper types.
- For compound “soft error” scenarios:
  - assert the promoted primary success outcome
  - optionally assert `metadata.softChildErrorCode` only if deterministic

---

## 8) Configuration & Feature Flags (Proposed)

### Intent extraction flags
- `ai.intent-extraction.progressive.enabled` (default: `false` initially)
- `ai.intent-extraction.repair.enabled` (default: `true` when progressive enabled)
- `ai.intent-extraction.repair.max-attempts` (default: `1`)
- `ai.intent-extraction.multi-step.enabled` (default: `true` when progressive enabled)
- `ai.intent-extraction.force-mode` (`compound|repair|multi_step`, optional debugging)

### VectorSpace resolution flags
- `ai.routing.vector-space.enabled` (default: `true`)
- `ai.routing.vector-space.policy` (`single_then_fanout_then_clarify` recommended)
- `ai.routing.vector-space.fanout.enabled` (default: `true` for multi-domain KBs)
- `ai.routing.vector-space.fanout.max-spaces` (default: small, e.g., `3`)
- `ai.routing.vector-space.fanout.top-k-per-space` (default: small, e.g., `3`)
- `ai.routing.vector-space.clarification.enabled` (default: `true`)
- `ai.routing.vector-space.heuristic-last-resort.enabled` (default: `true` as crash-prevention, but not recommended as the final answer when multi-domain)

### Result normalization flags (already exist)
- `ai.orchestration.result-normalization.enabled` (default: `true`)
- `ai.orchestration.result-normalization.debug-snapshot-enabled` (default: `false`)

---

## 9) Rollout Plan (Safe, Measurable)

1) **Stage A (docs + invariants)**
   - Adopt the canonical contract for tests and integration guidance.
   - Reconcile normalization docs with the implemented “compound soft error” behavior.

2) **Stage B (progressive extraction behind flags)**
   - Implement progressive ladder and validator; keep default path as compound-only.
   - Enable repair in real-api CI first; measure structural failure reduction.

3) **Stage C (vectorSpace resolution stage)**
   - Introduce resolver with policy flags.
   - Enable “single-space auto-assign” and bounded fan-out with conservative defaults.

4) **Stage D (default enablement)**
   - Enable progressive extraction + routing resolver by default in real-api contexts (or profile-based).
   - Keep strict attempt and cost caps.

---

## 10) Metrics (What to Measure)

### Extraction
- % of structural failures on compound path
- `intentExtraction.path` distribution (compound vs repair vs multi-step)
- structural failure rate per provider/model
- p50/p95 latency per path and attempt count

### Routing
- % of intents missing `vectorSpace` prior to resolver
- fallback usage distribution (fan-out vs clarify vs heuristic)
- fan-out vector query cost per request
- fan-out similarity distributions / weak-results rate

### Normalization
- % of results normalized vs unchanged
- top error codes in real-api CI
- frequency of compound `metadata.softChildErrorCode`

---

## 11) Definition of Done (Unified)

- Intent extraction uses progressive ladder behind flags and emits deterministic diagnostics.
- Retrieval never runs with `vectorSpace` missing when `requiresRetrieval=true`.
- Multi-domain KB routing avoids “largest-count as default final answer”; bounded fan-out or clarification is available and observable.
- Final `OrchestrationResult` contract is provider-agnostic and tests assert invariants (`type/success/errorCode`).
- Documentation matches implemented normalization behavior (including compound “soft child error” handling).

