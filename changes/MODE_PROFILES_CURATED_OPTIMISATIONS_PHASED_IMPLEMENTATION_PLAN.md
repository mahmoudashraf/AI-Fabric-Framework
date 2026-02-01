# Mode/Profile + Curated Optimisations — Phased Implementation Plan

## Status
Proposed

## Scope
This plan sequences implementation of the optimisations listed in `changes/Optimisations.txt` (items 1–14), using:
- **Profiles** (`ai.orchestration.profile`) for coarse presets
- **Modes** (`ai.orchestration.modes` + curated packs) for coherent bundles
- **Policy** (server-authoritative effective policy resolved per request) to gate behavior inside pipeline steps

Deferred items are kept at the end.

Related documents:
- `changes/MODE_PROFILES_CURATED_OPTIMISATIONS_REVIEW.md`
- `Final_Documentation/Development_Guides/CURATED_MODES_PACKS_GUIDE.md`
- `changes/OPTIMIZATION_PROFILES_AND_DETERMINISTIC_RAG_INTEGRATION_PLAN.md`

## Principles (must hold in every phase)
- **No brittle heuristics**: avoid “if query contains …” string matching.
- **Stable pipeline**: do not reorder steps ad-hoc at runtime; let policy affect decisions inside steps and allow optional step enablement via configuration.
- **Authoritative-first grounding**:
  - Active attachments + stored pinned targets are authoritative.
  - “Working set” (this-turn retrieval docs) is helpful but non-authoritative.
- **No deceptive behavior**: avoid “silent replace” of action attempts; keep debug/response visibility.
- **Bounded context**: all injected context (attachments, pins, action summaries) must be size-limited and sanitized.
- **Mode/policy gated**: each optimisation is enabled by a coherent mode/profile bundle, not by user knowledge of internal flags.

## Mode/Policy Matrix (v1)
This matrix is the “one place to understand behavior”. It should be implemented as both:
1) Documentation table (for users)
2) Canonical curated pack YAML defaults (for the framework)

> Note: “Deep search control” means whether Advanced RAG can be enabled, and how.

| Mode | Intended UX | Action priority | Retrieval rules | Working set window | Suggestion style | Deep search control |
|---|---|---|---|---|---|---|
| `navigator` | Catalog / browse / compare / summarize | RAG-first (actions optional) | Deterministic info behavior (reliable answers); skip retrieval when pinned targets cover request | On; bounded | Query refinements + clarifications | Off by default |
| `navigator_deep` | Same as navigator, but allow deeper retrieval | RAG-first | Same as `navigator`, but may use Advanced RAG | On; bounded (slightly larger) | Query refinements + facets | On (mode forces `useAdvancedRag=true`) |
| `cart_assistant` | Cart/checkout assistant | Action-first for safe read actions; write actions require confirmation | Read-probe → RAG fallback allowed when read result is empty; retrieval constrained by pinned targets when present | On; bounded | Action-oriented (missing params, next steps) | Off by default (can be opt-in) |
| `support_resolver` | Support flows (subscriptions, cancellations, returns) | Action-first with interceptors | Same as cart, plus stronger confirmation/resolution handling | On; bounded | Action + guided troubleshooting | Off by default (can be opt-in) |

### Required debug metadata (observability)
Per request, include:
- `metadata.orchestrationPolicy.profile`
- `metadata.orchestrationPolicy.mode`
- `metadata.orchestrationPolicy.position`
- `metadata.orchestrationPolicy.informationModeEffective`
- `metadata.rag.executed` (boolean) and `metadata.rag.strategy` (enum: `NONE | STANDARD | ADVANCED`)
- `metadata.targetResolution.source` (`ACTIVE_ATTACHMENTS | STORED_PINNED | BOTH | NONE`)
- `metadata.workingSet.count`

## Phase 0 — Policy surface + curated defaults (foundation)
**Goal:** make optimisations “real” by giving them a stable, server-authoritative place to live.

Deliverables
- Extend the effective orchestration policy so pipeline steps don’t read scattered flags.
- Curated pack YAML defaults updated to publish the mode matrix above.
- A single “effective policy snapshot” is emitted per request (debug metadata) and once at startup (log).

Mode/policy gating
- Policy is resolved from: profile defaults → curated pack defaults → app overrides → request mode/position (only if allowlisted).
- Do **not** expose micro-flags as public knobs unless needed; prefer coherent mode bundles.

Optimisations covered
- #3 (mode-driven prioritization via policy decisions inside steps; not runtime pipeline reordering)
- Part of #1 (deep search control is mode/policy driven via curated packs)
- Enables all subsequent phases by defining where they are configured and how they are observed.

Validation
- Unit tests for mode allowlisting + precedence rules.
- A small “policy snapshot” test asserting stable output for each mode.

## Phase 1 — Authoritative pinned context (attachments + stored pins) in extraction
**Goal:** stop “attachments not considered” and make follow-ups work without forcing retrieval.

Deliverables
- Ensure the intent extractor receives **authoritative pinned context** every turn:
  - Active attachments (authoritative)
  - Stored pinned targets (authoritative; short window)
  - Mark clearly in the prompt as the primary source of truth.
- Best-effort vectorSpace handling (do not fail just because UI sent an unknown/missing vectorSpace).
- Strict bounds + sanitization for attachment content and metadata.

Mode/policy gating
- Enabled in all modes that support attachments/pinned targets.
- In `navigator`/`navigator_deep`, allow “answer from pinned context without retrieval” when sufficient.

Optimisations covered
- #4 (send resolved attachments into extraction)
- #11 (empty action results don’t override pins — enforce as invariant alongside this work)
- Part of #1 (attachments constrain retrieval scope, but not via raw concatenation)

Validation
- Real-api / integration tests:
  - “summarize this” with a pinned attachment produces a grounded answer without “id not found” deflection.
  - Follow-up “price?” without re-sending attachments still resolves the pinned target.

## Phase 2 — Working set memory + “re-open conversation” rehydration
**Goal:** keep context stable across turns and across reopening the conversation.

Deliverables
- Store bounded document references in chat history (id + vectorSpace + minimal metadata).
- Store bounded UI metadata per turn for rendering (UI-only; never injected into LLM/tool execution).
- Best-effort rehydrate pinned refs on conversation load/open.
- Working-set policy: bounded merge/decay strategy (last K docs or last T turns).

Mode/policy gating
- Enabled in modes that expect multi-turn follow-ups (`navigator*`, `cart_assistant`, `support_resolver`).
- Ensure working set is non-authoritative and never overrides active attachments/pins.

Optimisations covered
- #5 (store document ids in history to re-fetch)
- #10 (accumulate/replace working set docs)
- #2 (store UI custom metadata for message rendering; UI-only)

Validation
- Integration test: after N turns, ensure working set stays bounded and stable.
- Reload conversation: pinned refs rehydrate best-effort (missing refs do not fail the request).

## Phase 3 — Action results as first-class context + “read probe → RAG” fallback
**Goal:** make action follow-ups (“cancel it”, “change address”) reliable and reduce useless multi-LLM retries.

Deliverables
- Action results included in context in a bounded, structured way (no raw payload dumps).
- Action result list items contribute pinned targets (authoritative window), without overriding active attachments.
- Read-only empty result handling:
  - Execute the read probe (if configured)
  - If empty and success: proceed to RAG/generation for the user answer
  - Preserve action attempt visibility in debug/result structure (no silent replacement)

Mode/policy gating
- Enabled mainly in `cart_assistant` and `support_resolver`.
- `navigator` may use read probes only if explicitly enabled by curated pack.

Optimisations covered
- #7 (generation decision when user query is about action result)
- #8 (read-only empty result fallback, but “probe → RAG”, not “replace”)
- #12 (action result as part of context)
- #11 reaffirmed (empty action result must not override pinned targets)

Validation
- Real-api tests:
  - Missing required params → CLARIFICATION_REQUIRED (not CONFIRMATION_REQUIRED).
  - After providing params, confirmation occurs once and executes once.
  - Read-only action empty → answer comes from RAG (and debug shows the empty read probe occurred).

## Phase 4 — Suggestions per mode/position
**Goal:** make “what next” coherent and predictable per mode.

Deliverables
- Suggestion templates selected by effective mode/position.
- Hard caps for number/size of suggestions.

Mode/policy gating
- All modes, but suggestion strategy differs (navigator = query refinements; cart/support = action next steps).

Optimisations covered
- #6 (suggestions divided by mode/position)

Validation
- Snapshot tests per mode (suggestion style + max count).

## Phase 5 — Cross-vector-space relation (v0): “product ↔ reviews”
**Goal:** support “macbook → reviews about macbook” without requiring a full join engine.

Deliverables
- Retrieval planning that can scope “reviews” retrieval to pinned product identifiers:
  - Prefer backend-native metadata filtering when available.
  - Otherwise, use identifier-only hinting (id/sku/name tokens), not full attachment text.
- Response honesty: when relation cannot be verified, state uncertainty explicitly.

Mode/policy gating
- Enable in `navigator*` and `cart_assistant` when “reviews” space is present.
- Keep opt-in initially via curated pack to avoid surprising behavior in generic apps.

Optimisations covered
- #9 v0

Validation
- Integration test with seeded products+reviews:
  - Pinned product + “show reviews” returns reviews scoped to that product.
  - If reviews cannot be verified as related, response includes an explicit uncertainty statement.

## Phase 6 — Confirmation “yes/no” when no pending action
**Goal:** prevent wasted calls and weird behavior when user replies “yes/confirm” with nothing pending.

Deliverables
- Deterministic short reply when no pending action exists.
- Must not trigger retrieval, generation, or actions.

Mode/policy gating
- All modes (this is a global safety behavior).

Optimisations covered
- #13

Validation
- Unit/integration: “yes” with no pending action returns a short reply and logs no RAG/action execution.

## Phase 7 — Deferred (end)
These require bigger design work and should be done after the above phases are stable.

### 9 v1 — Explicit cross-space link schema + backend-native filtering
- Define link fields (“foreign keys”) in entity/vector-space config.
- Implement backend adapters for metadata filters.
- Provide explainable join diagnostics (what matched, what filtered).

### 14 — Generation output contract for referenced ids
- Add a strict generation JSON contract (answer + `referencedTargetIds[]`).
- Parsing and validation (subset of known ids only).
- UI contract for citations/pins derived from generation.

## Implementation notes (how to keep it mode/policy driven)
- Prefer **curated pack YAML defaults** for enabling an optimisation.
- Keep the public configuration surface small:
  - profile selection
  - allowlisted mode routing (mode/position)
  - a few coarse toggles (e.g., “enable working set”, “enable deep search mode”)
- Everything else should be derived as **effective policy** and visible in debug metadata.
