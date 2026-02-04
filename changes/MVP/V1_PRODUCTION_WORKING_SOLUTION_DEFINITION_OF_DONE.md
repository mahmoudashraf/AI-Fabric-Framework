# V1 Production Working Solution — Definition of Done (DoD)

## Purpose
Deliver a **reliable v1** that supports 3 primary e-commerce AI scenarios with minimal complexity:

1) **Navigator**: product semantic search + summarize + compare (pinned attachments first)
2) **Navigator Deep**: deeper info (reviews/policies) + broader alternatives/options
3) **Copilot**: orders/returns/support/settings with action-first read/write flows

This DoD focuses on observable behavior, minimal contract, and deterministic gating.

---

## Modes (v1 scope)
### 1) `navigator` (default browsing)
Use for: landing, catalog, search.

**Core behaviors**
- Prefer answering from **active attachments** (pinned targets) for:
  - compare/choose among pinned
  - summarize pinned
  - attribute Q&A about pinned (price, availability, features)
- If request needs knowledge not present in pinned targets:
  - run RAG (grounding) and generate answer
- Pinned targets are **not** a scope restriction:
  - “list samsung tablets” should still search normally.

### 2) `navigator_deep` (explicit user/UI opt-in)
Use for: “Deep Search” user intent.

**Core behaviors**
- Everything in `navigator`, plus:
  - allow **candidate expansion beyond pinned** (alternatives/more options)
  - allow multi-pass/advanced retrieval if configured
  - allow bounded attachment-content hinting for retrieval *only* if configured and safe

### 3) `copilot` (action-first)
Use for: cart, checkout, orders, returns, support, settings.

**Core behaviors**
- Prefer **read/write actions** when an action matches the user request.
- Confirmations for write actions must be reliable.
- Follow-ups (“cancel it”, “change address”) work using pinned action results.

---

## Minimal Intent Contract (v1)
The extractor produces only the fields required for routing:

- `type`: `INFORMATION | ACTION | OUT_OF_SCOPE`
- `requiresRetrieval`: boolean
- `requiresTargetResolution`: boolean
- `searchBeyondPinned`: boolean (only meaningful in navigator modes)
- `vectorSpace`: optional (must be from KB overview if provided)
- `action`: string (ACTION only)
- `actionParams`: object (ACTION only, only user-provided values)
- optional: `metadata.retrievalQueryHint` (single retrieval intent only; short; no PII)

**Not required in v1**
- Complex orchestration strategy enums
- Multi-step recommendation objects (keep optional, non-blocking)
- Prompt-driven “advanced rag” toggles (mode/policy decides)

---

## Target Model (v1)
### Authoritative pinned targets
- **Active attachments** (user-selected) are authoritative pinned targets.

### Low-priority recent targets
- Conversation/session “recent targets” are *not* authoritative.
- They are promoted to actionable “resolvedTargets” **only when** `requiresTargetResolution=true`.

### Action results
- WRITE actions MUST return explicit pinnable targets (framework contract).
- READ actions should NOT pin; they may populate a working set for context only.

---

## Deep Search Gating (v1)
Goal: allow grounding retrieval in normal mode but gate broad expansion behind deep mode.

### Distinguish retrieval reasons
Use `searchBeyondPinned`:
- `requiresRetrieval=true` + `searchBeyondPinned=false` → **grounding retrieval** (allowed in `navigator`)
- `requiresRetrieval=true` + `searchBeyondPinned=true` → **expansion retrieval** (requires `navigator_deep`)

### Gate rule
If:
- pinned targets exist,
- `requiresRetrieval=true`,
- `searchBeyondPinned=true`,
- mode is not `navigator_deep`,

Then:
- return `CLARIFICATION_REQUIRED`
- message: “Enable deep search to look beyond pinned items?”
- include `data.deepSearchRequired=true` and `data.suggestedMode="navigator_deep"`

No backend keyword matching.

---

## “Must Work” Scenarios (Acceptance Tests)

### A) Compare pinned products (navigator)
Setup: 2 active attachments.

Query examples:
- “compare these and pick the best”
- “which one should I buy?”

Expected:
- `ragExecuted=false`
- answer generated using pinned targets
- debug indicates pinned targets were used

### B) Single-target attribute from pinned set (navigator)
Setup: 2 active attachments.

Query:
- “what is the price of the bose one?”

Expected:
- `requiresTargetResolution=true`
- `ragExecuted=false` (if price present in pinned metadata/contentText)
- answer includes the correct price

### C) Grounding info from another space (navigator)
Setup: 2 active attachments (products).

Query:
- “any negative reviews on them?”

Expected:
- `requiresRetrieval=true`
- `searchBeyondPinned=false`
- RAG executes against review/policy space (if available)
- no “not in context” when retrieval is possible

### D) Expansion alternatives (navigator → gated)
Setup: 2 active attachments.

Query:
- “show me 3 cheaper alternatives to these”

Expected:
- In `navigator`: `CLARIFICATION_REQUIRED` with `deepSearchRequired=true`
- In `navigator_deep`: RAG executes and returns alternatives

### E) New focus search (navigator)
Setup: pinned targets exist, but user asks a new topic.

Query:
- “list samsung tablets”

Expected:
- `requiresRetrieval=true`
- RAG executes (product space), pinned targets do not block retrieval

### F) Copilot: orders/returns/write actions
Setup: in `copilot` mode.

Flow:
- “create purchase order for SKU …”
- provide missing params
- confirm once
- “cancel it”

Expected:
- write action confirmation executes once
- action result pins an order target (id/orderNumber)
- follow-up “cancel it” resolves to pinned order target
- no RAG needed for the follow-up unless user asks for related KB info

---

## Debug Fields (minimum for v1)
UI/debug tooling should highlight:

### At top-level metadata
- `metadata.orchestrationPolicy.mode` / effective mode
- `metadata.targetResolution.source` and `count` (ACTIVE_ATTACHMENTS vs promoted recent)
- `metadata.attachmentsPrompt.attachmentsCount`, `activeCount`, `storedRecentTargetsCount`
- `metadata.intentMetadata.requiresRetrieval`
- `metadata.intentMetadata.requiresTargetResolution`
- `metadata.intentMetadata.searchBeyondPinned`

### If RAG executed
- `data.ragResponse.query`
- `data.ragResponse.entityType`
- `data.ragResponse.metadata.retrievalQueryHintApplied`
- `data.ragResponse.metadata.optimizedQueryProvided`

### If deep search gating triggered
- `data.deepSearchRequired=true`
- `data.suggestedMode="navigator_deep"`

---

## UI Routing Defaults (v1)
Recommended defaults:
- landing/catalog/search → `navigator`
- cart/checkout/orders/returns/support/settings → `copilot`

Deep mode:
- A “Deep Search” button toggles mode to `navigator_deep` for that request.

---

## Definition of Done
V1 is complete when:
- All “Must Work” scenarios A–F pass against the real app.
- RAG is **not** executed for compare/choose among pinned targets in navigator.
- Cross-space grounding (reviews/policies) works in navigator (no deep required).
- Expansion beyond pinned is gated in navigator and allowed in navigator_deep.
- Copilot actions are reliable (confirm once, follow-up works via pinned action results).
- Debug fields clearly explain which path was taken.

