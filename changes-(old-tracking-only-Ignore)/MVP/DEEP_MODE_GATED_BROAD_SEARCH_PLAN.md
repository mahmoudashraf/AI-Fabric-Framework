# Deep-Mode Gating for “Broad Search Beyond Pinned Targets”

## Problem
When a request includes pinned targets (active attachments / resolved targets), the assistant should:

- **Answer from pinned targets** when the user is asking about *those items* (compare, price, summarize, etc.).
- Still be able to do **RAG for related knowledge** (e.g., *reviews*, *policies*, *warranty*), even when pinned targets exist.
- Avoid doing “broad search beyond pinned set” (e.g., *more options*, *alternatives*, *other products like these*) unless the user/UI explicitly opts into a deeper, more expensive search.

Today, `requiresRetrieval=true` is overloaded and does not differentiate:

1) **Grounding retrieval**: fetch related/secondary info about the pinned items (reviews, policies).
2) **Expansion retrieval**: search for *additional candidates* beyond pinned items (alternatives, more options).

This causes cost/latency spikes and “pinned pollution” style failures.

## Goals
- Keep the system **domain-agnostic** and avoid backend string matching.
- Make the gate **explicit and deterministic**:
  - User/UI chooses a “deep mode” when they want broad candidate expansion.
  - “Different-space grounding” retrieval remains allowed in normal mode.
- Provide clear behavior that is easy to debug and test.

## Non-goals
- No semantic “focus changed” heuristics in backend based on user text.
- No hard disabling of RAG whenever multiple attachments exist (breaks reviews/policies).

## Proposed Contract Changes (LLM Output)
Extend the intent JSON contract for `INFORMATION` intents:

### New field (per intent)
`searchBeyondPinned: boolean`

Meaning:
- `false` (default): retrieval is for **grounding** (e.g., reviews/policies/specs) OR retrieval is unnecessary.
- `true`: user is asking to **expand** beyond pinned targets (alternatives/more options/other items).

Notes:
- This is independent from `requiresRetrieval`.
- Only relevant when pinned targets exist.

### Expected examples
- “Any negative reviews on them?” → `requiresRetrieval=true`, `vectorSpace=review`, `searchBeyondPinned=false`
- “Show me cheaper alternatives to these” → `requiresRetrieval=true`, `vectorSpace=product`, `searchBeyondPinned=true`
- “Compare these two and recommend under $200” → `requiresRetrieval=false`, `requiresGeneration=true`, `searchBeyondPinned=false`

## New Mode / UI Control
Add one explicit opt-in switch for deep search:

### Option A (preferred): Request mode
UI sends `mode = "navigator_deep"` when the user explicitly clicks “Deep search”.

### Option B: Boolean flag
UI sends `deepSearch = true`

We already have a mode/policy mechanism; prefer Option A to avoid extra request shape changes.

## Backend Behavior (No Heuristics)
In the orchestration policy, define which modes allow expansion:

- `navigator`: **disallow** expansion retrieval beyond pinned targets
- `navigator_deep`: **allow** expansion retrieval beyond pinned targets

### Gating rule
When handling an `INFORMATION` intent:

If all are true:
- pinned targets exist (active attachments and/or resolved targets),
- intent `requiresRetrieval=true`,
- intent `searchBeyondPinned=true`,
- effective mode is **NOT** deep (`navigator_deep`),

Then:
- terminate with `CLARIFICATION_REQUIRED`
- message: “Enable deep search to look beyond pinned items?”
- include a machine-readable hint in `data`, e.g.:
  - `data.deepSearchRequired=true`
  - `data.suggestedMode="navigator_deep"`

Otherwise:
- proceed normally:
  - `requiresRetrieval=true` + `vectorSpace=review/policies` + `searchBeyondPinned=false` is allowed in normal mode

## Prompt Updates (Curated Packs)
Update the curated intent-extraction prompts to set `searchBeyondPinned`:
- When pinned targets exist:
  - If user explicitly asks for “more options/alternatives/other products like these” → set `searchBeyondPinned=true`.
  - If user asks for reviews/policies/warranty/etc about pinned targets → set `searchBeyondPinned=false` and choose the right vectorSpace.

Important:
- Do not rely on hardcoded keyword checks in backend.
- The LLM prompt can include examples, but the backend must only read the boolean.

## Debugging / Observability
Add debug metadata fields:
- `metadata.orchestrationPolicy.modeEffective` (already present)
- `metadata.intentMetadata.searchBeyondPinned` (propagate from intent)
- `metadata.deepSearch.gated` (true/false)
- `metadata.deepSearch.reason` (e.g., `"BROAD_SEARCH_BEYOND_PINNED_REQUIRES_DEEP_MODE"`)

## Tests
### Unit tests
- Intent validation accepts the new field.
- Policy gate triggers CLARIFICATION_REQUIRED only when `searchBeyondPinned=true` and mode is not deep.

### Real API / integration tests
Scenario: pinned targets exist.

1) “compare these” → no RAG, generation-only from pinned.
2) “any negative reviews on them?” → RAG allowed (different vector space).
3) “show me cheaper alternatives to these”:
   - in `navigator` → CLARIFICATION_REQUIRED with deepSearchRequired
   - in `navigator_deep` → RAG executes

## Migration Notes (UI)
- Add a “Deep search” toggle/button that resubmits the user query using `mode="navigator_deep"` (or `deepSearch=true`).
- Default stays `navigator` (normal cost).

