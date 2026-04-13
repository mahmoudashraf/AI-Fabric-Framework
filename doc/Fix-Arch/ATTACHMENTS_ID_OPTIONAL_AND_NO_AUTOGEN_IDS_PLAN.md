# Attachments: ID Optional, No Auto‑Generated IDs, No Join‑By‑IDs (Greenfield) — Change Plan

## Status
Proposed

## Problem (current architecture)
We currently treat `attachments[].id` as a required join key across the pipeline:
- Some steps **drop/skip** attachments when `id` is blank.
- “Active attachments” are represented as a separate list (`activeAttachmentIds*`) that must be joined back to attachments by ID.
- To compensate, the server may generate request-local IDs like `att-auto-N`.

This is the wrong model for a framework and breaks real UI usage:
- The UI can attach **any type of data** (not always an entity with an ID).
- Attachments without IDs should still be usable as grounding context (summarize/compare).
- The server should not silently **invent identifiers**.
- A separate `activeAttachmentIds[]` list is unnecessary coupling; the UI already knows what it attached.

## Goals
1) `attachments[].id` is **optional**.
2) The server **never generates** attachment IDs (remove `att-auto-N` / similar).
3) ID-less attachments are **not dropped**; they are included in:
   - intent extraction context (grounding)
   - generation context (grounding)
   - resolvedTargets/pinned targets (as id-less targets)
4) Remove the “join-by-IDs” design:
   - **No** `activeAttachmentIds[]` contract needed for pinning.
   - The pinned set is simply the `attachments[]` list the UI sends in the request.

## Non‑goals
- Backward compatibility with the old “activeAttachmentIds join” approach (greenfield).
- Server-side heuristics that infer “which attachment is active” from text patterns.

---

## Proposed contract (API)

### Request
`attachments[]` is the only pinned-target input.

Each attachment may include:
- `id?: string` (optional; present when the attachment represents an entity that can drive actions)
- `vectorSpace?: string` (optional; best-effort)
- `contentText?: string` (optional; bounded)
- `metadata?: object` (optional; scalar-only after normalization)
- `source?: string` (optional)

**Removed:**
- `activeAttachmentIds[]` (and server-side `activeAttachmentIdsResolved[]`)

### Meaning
If the UI sends attachments, it is explicitly saying:
> “These are the pinned/selected items for this turn.”

If the user changes selection, the UI sends a new `attachments[]` list reflecting the new pinned set.

---

## Pipeline changes (high level)

### 1) AttachmentNormalizationStep (Order 23)
Update normalization rules:
- Do **not** generate IDs.
- Do **not** drop attachments just because `id` is missing.
- Drop only “empty” attachments where none of these are present:
  - `id` OR non-empty `contentText` OR non-empty scalar `metadata`

Output:
- `attachmentsNormalized[]` retains id-less attachments with `id=null`.

### 2) AttachmentPromptAugmentationStep (Order 26)
Render **all normalized attachments** into the LLM-visible context block (bounded).
- Do not require `id`.
- Include an **order-based reference** (display-only) so the model can refer to items even when id-less:
  - `ref=att#1`, `ref=att#2`, …

Example:
```
ATTACHMENTS (user context; pinned targets):
1) ref=att#1 vectorSpace=product id=30 metadata={sku=...} contentText="..."
2) ref=att#2 contentText="(pasted policy excerpt...)"
```

### 3) TargetResolutionStep (Order 52)
Pin **all** provided attachments into `resolvedTargets` deterministically.
- No join-by-ID logic.
- `ResolvedTarget.id` may be null.
- This enables follow-ups like “compare these” or “summarize this” to stay grounded in the pinned set.

---

## LLM prompt updates (curated prompts)
Update intent extraction rules to reflect the new contract:
- The pinned set is the `ATTACHMENTS` list (no “active IDs”).
- When the user asks to compare/summarize/choose and attachments exist:
  - Prefer answering from the pinned set.
  - Do not “search for something else to compare” unless the user explicitly asks for alternatives and deep mode is enabled.
- For actions requiring identifiers:
  - Use attachment identifiers/metadata when present.
  - If missing, ask for clarification (do not hallucinate).

---

## Real Apps (Chat capabilities demo)
Update request payloads:
- Stop sending `activeAttachmentIds`.
- Always send the currently pinned cards/items in `attachments[]`.

---

## Observability
Keep attachment debug minimal (no extra “defaulted” flags):
- `attachments.providedCount`
- `attachments.acceptedCount`
- `attachments.truncated`

Optionally include:
- `attachmentsPrompt.attachmentsCount`

---

## Tests
Unit tests (core):
- Normalization keeps id-less attachment (no auto-id).
- Prompt augmentation includes id-less attachment and renders `ref=att#N`.
- Target resolution pins id-less attachment into `resolvedTargets`.

Integration (real app / manual):
- Compare multiple pinned attachments without retrieval.
- Summarize a pinned attachment with only contentText (no id) and confirm it is grounded.

---

## Acceptance criteria
1) A request with `attachments[]` where one or more attachments have **no id** is still grounded end-to-end.
2) No code path generates attachment IDs.
3) No step requires `activeAttachmentIds` for pinning; pinning is driven solely by `attachments[]`.

