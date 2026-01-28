# Attachment Grounding End‑to‑End Fix (Intent → RAG → Generation) — Change Plan

## Status
Proposed

## Trigger (observed failure)
User sends an active attachment and asks:
- “summarize this. specs”

But the assistant replies:
- “The context provided does not contain any specifications for a product with the id=85…”

Even though:
- the attachment exists and was accepted
- RAG retrieved a document whose `id` is `85`

This is a **grounding failure**, not a retrieval failure.

---

## Evidence (from `Debug/context`)

### 1) Attachments are accepted and injected
- `result.metadata.attachments.acceptedCount = 1`
- `result.metadata.attachmentsPrompt.injected = true`

So attachments are not “ignored”.

### 2) But the retrieval/generation query bypasses the injected query
The actual RAG query used is:
- `result.data.ragResponse.originalQuery = "summarize specifications for product with id=85 product specifications, id=85"`

This comes from:
- `intent.optimizedQuery` + `metadata.retrievalQueryHint`

It does **not** include the injected “ATTACHMENTS (authoritative)” block because the orchestrator selects `optimizedQuery` over `processedQuery`.

### 3) The attachment was not promoted into pinned targets
There is no:
- `result.metadata.targetResolution`

Meaning the pipeline did not produce `resolvedTargets` from active attachments for this request.
As a result, the generation context did not include:
- `PINNED TARGETS (authoritative)` block with `id=85` / `sku=...` / `category=...`

### 4) Document context does not include identifiers
`buildContextFromDocuments(...)` currently outputs:
- optional `[vectorSpace]` prefix (only if present in doc metadata)
- `title` + `content`
- **does not include `doc.id`**

So the LLM sees content for the right item, but cannot reliably map “id=85” to that content.

### 5) UI payload swapped `vectorSpace` vs domain category
Request had:
- `attachments[].vectorSpace = "office supplies"`
- `attachments[].metadata.category = "product"`

But in AI Fabric contract, `vectorSpace` must be the **indexed entity type** (e.g., `product`), while “Office Supplies” is a domain category that should live in `metadata`.

This mismatch should be detected and reported deterministically (greenfield).

---

## Root Causes

1) **Attachment grounding is currently “intent‑prompt only”**:
   - We inject attachments into the prompt used for intent extraction, but do not ensure they become authoritative context for **generation**.

2) **Target pinning depends on an LLM flag** (`requiresTargetResolution`):
   - For “summarize this” the LLM often does not set it, so active attachments do not become pinned targets.

3) **Context building drops identifiers**:
   - Retrieval context doesn’t include `doc.id`, causing “I can’t find id=85” failures even when the correct doc is present.

4) **No fail‑closed validation for `attachments[].vectorSpace`**:
   - Invalid vectorSpace is silently accepted, leading to drift and confusing behavior.

---

## Goals

- Active attachments are treated as **authoritative grounding** for the request end‑to‑end:
  - intent extraction
  - retrieval planning/scoping
  - generation prompt/context
- Eliminate reliance on brittle “LLM remembered to set requiresTargetResolution” for attachment grounding.
- Make incorrect UI payloads fail fast with actionable error messages (greenfield).
- Keep the solution **domain‑agnostic** and **bounded** (no hardcoded keys like products/orders).

## Non‑goals
- Heuristic string matching (“if query contains ‘summarize’…”).
- Domain key guessing from action results or metadata.
- Adding more LLM calls for this scenario.

---

## Proposed Solution (comprehensive, production‑ready)

### A) Always pin active attachments into pipeline `resolvedTargets`
Update `TargetResolutionStep` behavior:

1) **Pinning mode (always-on):**
   - If `activeAttachmentIdsResolved` is non-empty, build `resolvedTargets` from those attachments and attach to `PipelineContext`, regardless of intent flags.
   - Record `metadata.targetResolution.source = ACTIVE_ATTACHMENTS` and `count`.

2) **Fail‑closed mode (only when required):**
   - If any intent has `requiresTargetResolution=true` and no active targets can be resolved, terminate with `CLARIFICATION_REQUIRED`.

This keeps fail‑closed semantics, while making “active attachment selected” work consistently even if the LLM does not request target resolution.

**Impact:** `IntentHandlingStep.prependPinnedTargetsContext(...)` starts working for summarization and “this/it” flows without needing LLM flags.

---

### B) Make generation and retrieval respect pinned targets deterministically
Introduce a small, deterministic rule in information handling:

- When `resolvedTargets` are present:
  - Always prepend the `PINNED TARGETS (authoritative)` block to generation context (already implemented).
  - Optionally (recommended): scope retrieval to the pinned target vector spaces:
    - if targets include spaces, set/override `intent.vectorSpace` to their vectorSpaces (unique, comma-separated)
    - this prevents “wide” retrieval drift on follow-ups

This is contract-driven: the UI explicitly selected the attachment(s).

---

### C) Include doc identifiers in the RAG context string
Update `buildContextFromDocuments(...)` to include a small header per doc:
- `id=<doc.id>`
- `vectorSpace=<doc.vectorSpace>` (or derive from response entityType when single-space)

Example (conceptually):
```
[vectorSpace=product id=85]
<content>
---
```

This is domain-agnostic and prevents “can’t find id=85” failures.

Related plan:
- `changes/RAG_DOCUMENT_VECTORSPACE_ENRICHMENT_CHANGE_PLAN.md`

---

### D) Fail‑closed validation for `attachments[].vectorSpace`
Add a validation rule during attachment normalization:

- If the RAG module can enumerate entity types (via `KnowledgeBaseOverviewService`), then:
  - `attachments[].vectorSpace` must be one of the known entity types.
  - If not, return `CLARIFICATION_REQUIRED` (or `ERROR`) with:
    - `invalidVectorSpace`
    - `allowedVectorSpaces[]`

This prevents silent misuse like:
- `vectorSpace="office supplies"` (domain category)

and guides UI developers to send:
- `vectorSpace="product"`
- `metadata.category="Office Supplies"`

Greenfield: do not attempt to “guess/fix” swapped fields.

---

### E) Update UI/client guidance (docs)
Update the UI migration guide / attachments docs to explicitly say:
- `vectorSpace` = indexed entity type (e.g., `product`)
- domain category belongs in `metadata.category`
- `activeAttachmentIds` must reference `attachments[].id`

Docs to update/extend:
- `Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md`
- `changes/ATTACHMENTS_METADATA_AND_RAG_OPTIMIZATION_MASTER_PLAN.md` (cross-ref)

---

## Acceptance Criteria

1) With a selected attachment, “summarize this/specs” always produces a grounded summary of that attachment:
   - even when `intent.optimizedQuery` is present
   - even when the LLM does not set `requiresTargetResolution`

2) The pipeline shows deterministic grounding metadata:
   - `metadata.attachments.acceptedCount > 0`
   - `metadata.targetResolution.source = ACTIVE_ATTACHMENTS`

3) The context given to generation includes identifiers:
   - `id=<...>` per document in RAG context and/or pinned targets

4) Invalid attachment vectorSpace produces a clear, deterministic client-visible failure:
   - no silent fallback to unrelated spaces

---

## Test Plan

### Unit tests
- Target pinning:
  - active attachments produce `resolvedTargets` even if intent doesn’t require target resolution
  - required target resolution still fails-closed when no active targets
- Context building:
  - context includes `id=` for docs
- Attachment validation:
  - invalid vectorSpace yields CLARIFICATION_REQUIRED with allowlist

### RealAPI / Integration
- Chat capabilities demo:
  - send attachment + `activeAttachmentIds` and ask “summarize this”
  - verify response is grounded to the selected id/sku
  - verify `metadata.targetResolution` exists

---

## Implementation Sequence (suggested)
1) Update `TargetResolutionStep` (pin always; fail-closed only when required).
2) Add doc-id to `buildContextFromDocuments(...)`.
3) Add doc `vectorSpace` enrichment (Option A in `RAG_DOCUMENT_VECTORSPACE_ENRICHMENT_CHANGE_PLAN.md`).
4) Add attachment vectorSpace validation (fail-closed with allowlist).
5) Update UI docs.

