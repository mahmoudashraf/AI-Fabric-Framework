# Attachments + Metadata + RAG Optimization — Master Change Plan

## Status
Proposed

## Context / Problem (from trials)
We are seeing repeated failures that look like “LLM forgot context”, but are actually **missing signals + retrieval drift**:

1) **UI attachment is not first-class** → the orchestrator does not know what card/item the user is acting on (“buy it”, “add to cart”), so it guesses from prior RAG results (e.g., repeatedly choosing `foot-sneaker-001`).
2) **Metadata is not treated as authoritative context** → IDs/SKUs exist but are not consistently highlighted/consumed, so follow-ups lose grounding.
3) **Retrieval scope drifts across turns** → follow-ups like “compare both” end up mixing unrelated products (phone vs sneakers) because the system cannot resolve references (“both/it/this”) and does wide RAG without a stable “working set”.

This plan makes attachments and metadata **explicit, bounded, and authoritative**, and upgrades retrieval/memory planning so follow-ups stay grounded.

---

## Principles (aligned with `Final_Documentation/Development_Guides/AI_FABRIC_FRAMEWORK_PHILOSOPHY.md`)
- **Greenfield:** no legacy compatibility; remove/replace patterns that rely on heuristics or accidental behavior.
- **Fail-closed:** if target resolution is ambiguous, ask a clarification instead of guessing.
- **Contracts over heuristics:** no domain-key guessing (`products/orders/items`); use explicit schemas and reserved keys.
- **Bounded context:** avoid token bloat; cap attachment sizes and metadata keys/values.
- **Observability:** attach deterministic debug metadata (without polluting user-visible answers).

---

## Goals
- When a user acts on a UI item (“buy it”, “add to cart”), the system uses the **selected attachment** deterministically.
- Attachment **metadata** (id, sku, category, etc.) is included in the LLM context in a structured, bounded way.
- Follow-up queries (“compare both”, “cancel it”, “update address”) remain grounded by using:
  - active attachments
  - a bounded, persisted retrieval “working set”
  - persisted action refs (already added via chat turn metadata / Action Context)
- RAG query and vector spaces are constrained by attachments when present to prevent drift.

## Non-goals
- “Tune the LLM” by prompt hacks or string matching (“if message contains thanks…”).
- Let arbitrary, free-form model metadata influence retrieval.
- Merge action + rag payloads in one response “just in case” (keep orchestration deterministic).

---

## Proposed API Contract (Backend receives UI context explicitly)

### 1) Extend `ChatQueryRequest`
Add an optional `attachments` array and optional “selection” hints:
- `attachments[]`: items the UI is showing / the user referenced
- `activeAttachmentIds[]`: which attachments are currently selected/highlighted in the UI
- `clientContext` (optional): small structured hints from UI (e.g., current screen/module)

### 2) Attachment schema (domain-agnostic)
Each attachment is an “entity reference + bounded context”:
- `id` (string, required)
- `vectorSpace` (string, required) — the retrieval domain/entityType
- `contentSnippet` (string, optional, bounded)
- `metadata` (map<string, scalar>, optional, bounded)
- `source` (string, optional; e.g., `ui-card`, `search-result`)
- `url`/`imageUrl` (optional; not required for LLM context)

Hard rules (server-side validation):
- Reject or truncate oversized payloads (fail-closed per attachment, not whole request).
- Only allow **scalar metadata values** (string/number/boolean). Drop nested objects/lists.
- Apply PII redaction where applicable (reuse PII module).

---

## Pipeline Changes (V5-style, deterministic)

### Step A — AttachmentNormalizationStep (new, early)
Normalize + validate attachments into a safe internal representation:
- cap number of attachments (e.g., 10)
- cap metadata keys per attachment (e.g., 12)
- cap value length (e.g., 120)
- drop unsafe strings (emails, multi-line, whitespace-heavy)

Outputs:
- `context.attachmentsNormalized`
- `context.activeAttachmentIdsResolved` (intersection of declared ids)
- debug: `context.metadata.attachmentsCount`, `attachmentsTruncated=true/false`

### Step B — TargetResolutionStep (new)
Resolve ambiguous references (“it”, “this”, “both”) **without guessing**:
Priority order:
1) active attachments (if present)
2) last-turn retrieval working set (if present)
3) ask `CLARIFICATION_REQUIRED`

Output:
- `context.resolvedTargets[]` (entity refs)
- debug: `resolvedTargetsSource=attachments|workingSet|clarification`

### Step C — PromptAugmentationStep (update existing prompt builder)
Make attachments the **authoritative context** by injecting a dedicated section BEFORE history:

```
ATTACHMENTS (authoritative):
1) vectorSpace=product id=78 metadata={sku=SKU-..., category=Electronics, ...}
...
```

Rules:
- Always include `id` and `vectorSpace`.
- Include metadata as bounded `key=value` pairs.
- Keep it structured and minimal; no prose.

### Step D — RetrievalPlanning changes (no drift)
When `resolvedTargets` or `active attachments` exist:
- **Restrict vectorSpaces** to those attachment vectorSpaces (unless user explicitly asks broader search).
- Optionally treat active attachments as “pinned docs”:
  - include their `contentSnippet + metadata` directly in the generation context
  - still run retrieval if the intent requires it (but scoped)

### Step E — Retrieval query composition (safe augmentation)
Base query selection remains:
1) `intent.optimizedQuery` if present
2) else pipeline `effectiveQuery`

Augment query with **deterministic hints** derived from attachments:
- append tokens like: `ref:id=78 ref:sku=SKU-HEA-12125`
- only from scalar metadata keys the UI explicitly sent
- bounded token count

Related/optional: also support the explicit extractor metadata key described in:
- `changes/INTENT_METADATA_RAG_QUERY_AUGMENTATION_CHANGE_PLAN.md`

But: only apply that hint when the response has exactly **one** retrieval intent (avoid cross-intent contamination).

---

## Memory / Working Set (follow-up stability)

### 1) Persist retrieval working set per turn (new)
When RAG runs, store bounded refs in chat turn metadata:
- `retrieval.vectorSpacesUsed[]`
- `retrieval.topDocumentIds[]` (bounded)
- optional: `retrieval.topDocumentRefs[]` (id + vectorSpace + score)

### 2) Follow-ups reuse working set
If user follow-up is ambiguous and no active attachments:
- prefer searching within the working set (re-rank/refine) before broad retrieval

This prevents “compare both” from pulling in unrelated categories.

---

## Action Execution Improvements (grounding)
We already improved chat history by persisting action refs into turn metadata and emitting an `Action Context:` line into the LLM prompt history.

Next (in this plan):
- Action param resolvers should prefer `resolvedTargets` / active attachments to fill identifiers (sku/id) deterministically.
- Confirmation/missing-param steps should include:
  - last action refs
  - pending action stack
  - resolvedTargets (if present)

Fail-closed: if user says “cancel it” and there are multiple plausible targets, ask which one.

---

## UI Guidance (to stop guessing)
UI must send:
- attachments shown on screen (or at least the referenced ones)
- active selection (highlighted card)
- when user clicks action buttons (buy/add-to-cart), include the specific attachment id(s)

This is the only reliable way to avoid the “picked sneaker again” issue without heuristics.

---

## Testing Plan

### Unit tests
- Attachment normalization (limits, truncation, scalar-only metadata)
- Target resolution (attachments vs working set vs clarification)
- Query augmentation (tokens appended deterministically; bounded)

### Integration tests (chat realapi)
- “add to cart” with active attachment always uses that SKU/id
- “compare both” uses 2 active attachments; does not pull unrelated items
- follow-up after action uses action refs from history (cancel/update flows)

### Observability
Add deterministic debug metadata:
- `rag.queryUsed` (or hash) and `rag.hintApplied=true/false`
- `resolvedTargetsSource`
- `attachmentsUsedForRetrieval=true/false`

---

## Rollout (greenfield)
1) Add request schema + internal attachment model (break API if needed; update demo UI + lovable frontend).
2) Add AttachmentNormalizationStep + prompt augmentation (no behavior change yet besides better grounding).
3) Add TargetResolutionStep + retrieval scoping.
4) Add working set persistence + reuse.
5) Tighten fail-closed behaviors (clarify instead of guessing).

---

## Acceptance Criteria
- “add to cart / buy it” on a highlighted UI item always targets that item (no cross-item leakage).
- “compare both” compares the two selected attachments, not historical unrelated products.
- Metadata (id/sku) is visible to the model in a structured way and can be used to route actions reliably.
- Retrieval does not drift across turns when attachments/working set exist.
- All logic remains domain-agnostic and contract-based.

