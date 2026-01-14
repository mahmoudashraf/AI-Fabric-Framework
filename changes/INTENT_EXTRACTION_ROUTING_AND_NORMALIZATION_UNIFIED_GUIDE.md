## Unified Provider Stabilization Guide (Intent Extraction + `vectorSpace` Routing + Result Normalization)

**Document Purpose:** Provide a single, provider-agnostic design and implementation plan that unifies:
- progressive intent extraction reliability,
- safe/scalable `vectorSpace` routing for retrieval (large multi-entity knowledge bases),
- deterministic orchestration result normalization.

**Version:** 1.0  
**Date:** January 2026  
**Status:** Living Document

---

### Why this exists (problem summary)

Across real providers, the pipeline can fail or become flaky when:
- **Intent JSON is incomplete** (often after a “repair” flow): `requiresRetrieval=true` but `vectorSpace` is missing/blank.
  - Downstream retrieval can receive `entityType=null` and hard-fail in some vector DB providers.
  - Heuristic “guessing” (largest-count, query mention match) can silently misroute at scale.
- **Provider output shapes differ** for the same system outcome (compound wrappers, messages, success booleans), which:
  - makes integration tests flaky,
  - forces clients to implement provider-specific handling.

This guide defines a unified, conservative stabilization strategy that keeps the framework:
- deterministic and provider-agnostic,
- bounded in cost/latency,
- observable (so we can prove it works),
- aligned with framework philosophy (LLM decides; config constrains; avoid silent correctness regressions).

---

## 1) Framework principles applied to this problem

1. **Determinism and provider-agnostic contracts**
   - The system returns a stable `OrchestrationResult` contract (`type`, `success`, `errorCode`) regardless of provider.
2. **Separation of concerns**
   - Intent extraction produces structured intent.
   - Retrieval routing resolves `vectorSpace` when needed.
   - Normalization enforces final contract based on system facts.
3. **Repair is structural only**
   - “Repair” fixes JSON/schema correctness, not semantic reasoning (“guess the best space”).
4. **Bounded fallback behavior**
   - No unbounded retry loops.
   - Any “search-all” behavior is bounded by configuration.
5. **No silent misrouting**
   - For multi-domain KBs, correctness is often worse when the system confidently chooses the wrong space.
   - Prefer bounded fan-out or clarification rather than “largest-count as final answer”.

---

## 2) Current state (what exists today)

This repo already includes strong building blocks:

- **Intent extraction + repair**
  - `IntentQueryExtractor` parses provider output and can attempt JSON repair when parsing fails.
  - It also contains a crash-prevention heuristic that infers a missing `vectorSpace` from `KnowledgeBaseOverview`.
- **Deterministic orchestration result normalization**
  - `OrchestrationResultNormalizer` + `OrchestrationResultNormalizationStep` already enforce a provider-agnostic contract.

The key gap is that “repair” and “vectorSpace inference” are currently intertwined, and routing is forced too early in the lifecycle (which can hide correctness regressions).

---

## 3) Unified target architecture (3 stabilization layers)

### Layer A — Progressive intent extraction engine

Goal: produce a structurally valid `MultiIntentResponse` across providers with bounded attempts.

**Progressive ladder (in order):**
1. **Compound fast-path**: single structured JSON response (normal case).
2. **Repair loop (structural/schema only)**: at most N attempts (default: 1).
3. **Multi-step extraction**: small prompts with step-level validation (hardest cases).

**Key rule:** repair never performs semantic corrections (e.g., “infer vectorSpace from query”). If a field cannot be deterministically derived, it remains unset and is handled by routing policy (Layer B).

**Output requirements (invariants):**
- JSON parses into `MultiIntentResponse`.
- `Intent.type` is valid.
- `Intent.intent` or `Intent.action` exists.
- Relationship-query special cases can still use deterministic parameter normalization (because those are system-known handler contracts).

**Observability (minimum):**
- `intent.extraction.path`: `compound|repair|multi_step`
- `intent.extraction.failureCategory`: `parse_error|schema_error|timeout|provider_error|unsafe_action|...`
- `intent.extraction.repairAttempts`: integer

### Layer B — `vectorSpace` routing policy (retrieval routing)

Goal: resolve missing `vectorSpace` safely for large, multi-entity KBs without silent correctness regressions.

**When this layer triggers**
- For any intent where `requiresRetrieval=true` and `vectorSpace` is missing/blank after Layer A.

**Routing policy (default recommendation)**
1. **Single-space auto-select**
   - If KB has exactly one entity type, assign it (safe).
2. **Bounded fan-out fallback (coverage-first, bounded cost)**
   - Choose top N candidate spaces (from `KnowledgeBaseOverview`) and run retrieval with small `topK` per space.
   - Merge results deterministically and continue generation with merged context.
   - Use deterministic merging that does not assume cross-space score comparability (rank-based merging is preferred).
3. **Clarification-required fallback**
   - If fan-out results are weak (below configured similarity thresholds / empty results), return a deterministic “need clarification” outcome.

**Optional mid-term enhancement (recommended for multi-domain production)**
- Add an explicit **router stage** that outputs `{vectorSpace, confidence, rationale}`.
  - If confidence is low: fallback to bounded fan-out or clarification.

**Routing must be observable**
- `routing.vectorSpace.path`: `single|fanout|router|clarify`
- `routing.vectorSpace.candidates`: list (bounded)
- `routing.vectorSpace.selected`: string (when selected)
- `routing.vectorSpace.fanout.n`: integer
- `routing.vectorSpace.fanout.topK`: integer

### Layer C — Orchestration result normalization (final contract)

Goal: enforce a stable, provider-agnostic `OrchestrationResult` contract based on deterministic system facts.

Normalization remains conservative and must not “re-interpret” user intent.

**Contract invariants**
- `type`: canonical top-level outcome
- `success`: deterministic boolean derived from system outcome
- `errorCode`: stable identifier (especially when `type=ERROR`)
- `message`: product-owned message (not dependent on provider phrasing for correctness)

**Clarification representation (two viable options)**
1. **Preferred (greenfield-friendly):** introduce a new `OrchestrationResultType` value (e.g., `CLARIFICATION_REQUIRED`)
   - Clear semantics for clients and tests.
2. **Minimal change:** return `OUT_OF_SCOPE` with structured `data` or `metadata` indicating `reason=CLARIFICATION_REQUIRED`
   - Avoids public enum changes but is less explicit.

---

## 4) Configuration (feature flags + bounded defaults)

Recommended YAML shape (names are suggestions; align with existing config conventions):

```yaml
ai:
  intent-extraction:
    progressive:
      enabled: false
      force-mode: compound # compound|repair|multi_step (debug)
    repair:
      enabled: true
      max-attempts: 1
    multi-step:
      enabled: true

  routing:
    vector-space:
      enabled: true
      policy: fanout_then_clarify # heuristic|fanout_then_clarify|clarify_only|router_then_fanout
      fanout:
        max-spaces: 3
        top-k-per-space: 3
        min-acceptable-results: 1

  orchestration:
    result-normalization:
      enabled: true
      debug-snapshot-enabled: false
```

**Default behaviors**
- Small KBs (1 space): auto-select is safe and fast.
- Large KBs (multiple spaces): use bounded fan-out or clarification, not “largest-count as final routing answer”.

---

## 5) Testing strategy (provider-stable assertions)

### Unit tests (fast, deterministic)
- Extraction engine selection logic (compound → repair → multi-step).
- Repair is structural-only: it should not “invent” missing routing fields when inference is not deterministic.
- Routing policy decision:
  - single-space auto-select,
  - fan-out bounded behavior,
  - clarification trigger conditions.
- Normalization invariants:
  - missing action handler → `ERROR` + `ACTION_NOT_FOUND`,
  - compound handling stability,
  - child error bubbling rules.

### Integration tests (real providers)
- Assert only canonical invariants:
  - `type`, `success`, `errorCode`, sanitization invariants.
- Avoid asserting provider prose or wrapper types.
- Add targeted scenarios:
  - provider returns malformed/truncated JSON,
  - provider omits `vectorSpace`,
  - multi-intent compound where a non-primary child fails.

---

## 6) Rollout plan (safe increments)

1. **Stage 0 (docs + telemetry):** add observability fields (no behavior change).
2. **Stage 1 (engine scaffolding):** introduce extraction engine interfaces while keeping current behavior as the only strategy.
3. **Stage 2 (structural-only repair):** adjust repair prompt to schema-only corrections; keep routing separate.
4. **Stage 3 (routing policy):** implement bounded fan-out + clarification behind flags.
5. **Stage 4 (defaults):** enable progressive extraction and routing policy by default in real-api/production profiles after metrics show stability.

---

## 7) Open decisions (capture before implementing)

1. **Clarification outcome surface**
   - New `OrchestrationResultType` vs `OUT_OF_SCOPE + reason`?
2. **Fan-out merge strategy**
   - Prefer deterministic rank-based merging to avoid cross-provider score normalization complexity.
3. **Router stage**
   - LLM-based router vs rules-only vs hybrid; which telemetry defines “low confidence”?
4. **Provider selection for orchestration vs generation**
   - If enabling multi-step extraction, consider separate provider selection for orchestration tasks (structure-first).

---

## 8) Related documents

- `changes/PROGRESSIVE_INTENT_EXTRACTION_FALLBACK_PLAN.md` (source plan for Layer A)
- `changes/intent-vectorspace-fallback-options.md` (routing options tradeoffs)
- `changes/vectorspace-inference-and-routing-recommendation.md` (routing recommendation)
- `changes/ORCHESTRATION_RESULT_NORMALIZATION.md` (contract for Layer C)
- `Final_Documentation/System_Archtecture_Guides/NORMALIZATION_AND_ORCHESTRATION_GUIDE.md` (pipeline + normalization overview)

