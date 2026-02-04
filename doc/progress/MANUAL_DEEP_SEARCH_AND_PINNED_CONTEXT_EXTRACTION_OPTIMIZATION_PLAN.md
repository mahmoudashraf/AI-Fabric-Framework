# Manual Deep Search + Pinned-Context Intent Extraction — Optimization Plan

## Status
Proposed (for review before implementation)

## Why this plan exists
We observed two persistent production issues:
1) **Unnecessary RAG calls** (cost/latency) even when the UI already pins the exact item(s) the user is referring to.
2) **Incorrect `requiresRetrieval` decisions** because the intent-extraction LLM is asked “do you have enough info?” while it is not given the **authoritative pinned context** (active attachments / resolved targets).

Additionally, “deep search / Advanced RAG” is valuable but too expensive to run implicitly on every turn. We want a **manual activation** mechanism that is deterministic and testable.

This plan defines:
- A **manual deep search mode** (UI/user-triggered).
- A **pinned-context section** injected into intent extraction prompts and treated as the *primary* grounding source for JSON fields.

This is a greenfield framework change: no backward-compat guarantees.

## Goals
1) Make “deep search” explicitly triggerable (by UI/button or requested mode), not accidentally enabled.
2) Reduce RAG calls in deterministic navigator flows when pinned targets already cover the request.
3) Improve correctness of `requiresRetrieval`/target resolution and action param filling by providing authoritative pinned context during intent extraction.
4) Keep the system **domain-agnostic** (no “items/products/orders” key matching in core behavior).
5) Make behavior observable and testable (realapi + unit tests).

## Non-goals
- No per-domain tuning or string matching in production code.
- No hidden “pro-only” behavior changes that alter correctness semantics without visibility.
- No large/unbounded context injection (token blow-ups, prompt injection risk).

---

## Optimization A — Manual Deep Search (Advanced RAG) Mode

### Problem
Advanced RAG (query expansion, reranking, etc.) improves quality but is higher cost/latency. Auto-enable heuristics are risky for production and hard to reason about.

### Proposed behavior
Introduce a deterministic “manual deep search” activation that can be driven by:
1) **Mode routing** (recommended): `navigator_deep` or similar.
2) **Request metadata flag** (optional): `useAdvancedRAG=true`.

Manual deep search should:
- override any auto-enable heuristics (explicit > implicit),
- be visible in debug metadata,
- be safe with deterministic fallbacks if the advanced provider is unavailable.

### Configuration (example)
```yaml
ai:
  orchestration:
    modes:
      navigator:
        information-mode: DETERMINISTIC_RAG_GENERATE
      navigator_deep:
        information-mode: DETERMINISTIC_RAG_GENERATE
    position-routing:
      landing: navigator
      cart: navigator
```

UI behavior (example):
- Default uses `mode=navigator`.
- “Deep search” button sends `mode=navigator_deep` (or sets `useAdvancedRAG=true`).

### Observability
Return in debug metadata:
- `rag.advanced.enabled=true/false`
- `rag.advanced.source=MODE|REQUEST_METADATA|AUTO|DISABLED`

### Testing
- Unit: deep search enabled only when requested (mode/flag), not by default.
- RealAPI: two runs of the same query show different `rag.advanced.*` metadata when toggled.

---

## Optimization B — Pinned Context Included in Intent Extraction (Authoritative Grounding)

### Problem
We currently ask the LLM to decide (directly or indirectly):
- whether retrieval is needed (`requiresRetrieval`),
- whether the request is target-dependent (`requiresTargetResolution`),
- what action parameters are present (e.g., sku/id, orderNumber),
but the LLM often does not see the authoritative pinned context (attachments / resolved targets) at extraction time.

This causes:
- `requiresRetrieval=true` almost always (because the model can’t know what’s pinned),
- wrong references (“IDs not found”, ignoring attachments),
- unnecessary vector DB calls and drift.

### Proposed behavior
Inject a bounded “PINNED TARGETS (authoritative)” section into the intent extraction prompt, before the user query, and explicitly instruct:
- Use pinned targets as **the primary source** for filling JSON fields.
- Only request retrieval when pinned targets do not contain enough info to answer.

### Prompt requirements (must be explicit)
Add rules for extraction:
1) **Pinned targets are authoritative**: if `activeAttachmentIds` exists, the model must treat those targets as “selected by the user”.
2) **Fill from pinned targets first**:
   - For actions that reference an entity (e.g., “buy it”, “add to cart”), prefer pinned target metadata for identifiers (id/sku/orderNumber) rather than hallucinating.
3) **Retrieval gating**:
   - If the pinned targets contain enough info to answer a request that is about them (summarize/compare/describe/price/availability), set `requiresRetrieval=false` and provide `directAnswer` (LLM-driven mode) or allow deterministic skip logic to take effect (deterministic mode).
   - If pinned targets are insufficient, set `requiresRetrieval=true` and provide an `optimizedQuery` for retrieval (do not fabricate missing facts).
4) **Multiple pinned targets (LLM-decided, no string rules)**:
   - If the user request clearly references **multiple** targets (plural, explicit IDs/SKUs, “both”, or the UI pins multiple active targets) → proceed using those targets.
   - If the user request appears to reference **a single** target but multiple targets are pinned and the request is ambiguous → return `CLARIFICATION_REQUIRED` and ask which pinned target to use (or ask the user to select one in the UI).

### Data contract to include (bounded but sufficient for full answers)
Provide to the extractor (for each pinned target):
- `id` (string)
- `vectorSpace` (string, may be empty/unknown)
- `contentText` (bounded length; may be truncated, but large enough for “summarize/extract specs”)
- `contentTextTruncated` (boolean)
- `metadata` (bounded scalar-only: sku, category, price, currency, etc.)
- Optional `title` / `url` (bounded)

Do not include:
- unbounded metadata maps,
- large RAG context dumps,
- entire history (history remains separate and windowed).

### Security / robustness
- Sanitize/PII-scan pinned context before prompt inclusion.
- Enforce strict size limits:
  - max pinned targets (e.g., 1–5)
  - max chars per `contentText`
  - max total pinned-context chars
- If vectorSpace is missing, treat it as “unknown” (best-effort); do not fail the request solely for missing vectorSpace.

### Observability
Return in debug metadata:
- `intentExtraction.pinnedTargetsProvided=true/false`
- `intentExtraction.activeAttachmentIds=[...]` (bounded)
- `intentExtraction.pinnedTargetsCount=N`

### Testing
- Unit: pinned context is injected (snapshot test for prompt assembly, with strict size assertions).
- RealAPI: with attachments pinned, “summarize this” sets `requiresRetrieval=false` (LLM-driven) or triggers deterministic retrieval-skip behavior (deterministic).

---

## Optimization C — Deterministic Navigator “Less RAG” Skip When Pinned Targets Cover the Request

### Problem
In deterministic navigator mode, INFORMATION historically “always RAG”. This is reliable but can be wasteful when the request is clearly about pinned targets.

### Proposed behavior
In `DETERMINISTIC_RAG_GENERATE`:
- If pinned targets exist and the request is target-dependent, skip vector DB retrieval and generate from pinned targets.

This should be:
- domain-agnostic,
- deterministic,
- observable (`retrievalSkipped=true`, `reason=PINNED_TARGETS`).

This optimization composes with Optimization B:
- better extraction (B) helps the system correctly recognize target-dependent requests,
- deterministic skip (C) reduces unnecessary vector calls even if the extractor remains conservative.

---

## Optimization D — Deterministic Vector Space Selection (Reduce Fan-Out)

### Problem
If vector space is missing, naive “search all spaces” is expensive and can drift.

### Proposed behavior
In deterministic flows:
- Prefer routing (vectorSpace router) when intent doesn’t specify a vectorSpace.
- If routing yields candidates, use a **bounded** fan-out (e.g., top 3 spaces, configurable).

Observability:
- `rag.vectorSpaceSelection.source=INTENT|ROUTER|FAN_OUT`
- `rag.vectorSpaces=[...]`

---

## Optimization E — Single “Navigator Mode” Enablement for the Demo App (Temporary)

If needed for `Real_Apps/chat-capabilities-demo` during stabilization:
- force `mode=navigator` server-side and temporarily disable other modes (commented out with TODO).

This is an app-level simplification, not a framework behavior change.

---

## Implementation sequence (high-level)
1) Add manual deep search activation (mode + optional request flag) + metadata observability.
2) Add pinned-context prompt injection to intent extraction + strict size/PII constraints.
3) Add deterministic “skip retrieval when pinned targets cover” gating + metadata.
4) Ensure deterministic vectorSpace routing uses router + bounded fan-out (configurable).
5) Add/expand realapi tests to cover:
   - pinned summarization without retrieval,
   - deep search toggle behavior,
   - deterministic navigator retrieval skip.

---

## References (existing plans/docs)
- `Final_Documentation/System_Archtecture_Guides/PLAN_DETERMINISTIC_RAG_ALWAYS_GENERATE.md`
- `Final_Documentation/System_Archtecture_Guides/ORCHESTRATION_OPTIMIZATION_GUIDE.md`
- `changes/OPTIMIZATION_PROFILES_AND_DETERMINISTIC_RAG_INTEGRATION_PLAN.md`
- `changes/ATTACHMENTS_METADATA_AND_RAG_OPTIMIZATION_MASTER_PLAN.md`
- `changes/ATTACHMENT_GROUNDING_END_TO_END_FIX_PLAN.md`
