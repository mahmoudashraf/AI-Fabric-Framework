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

### Storage/precedence model (authoritative vs supporting)
We must keep **pinned targets** (what “this/it/them” refers to) separate from **retrieved docs** (supporting KB context).

Precedence:
- **Active attachments → `resolvedTargets` (authoritative)**
- **Stored pinned targets (short window) → `resolvedTargets` (authoritative)**
- **Retrieved docs (this turn) → working-set docs (NOT authoritative)**

Notes:
- Do not automatically promote RAG search results into `resolvedTargets`. RAG hits are context, not “what the user meant” unless explicitly pinned.
- Active attachments always override stored pinned targets.

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

2) **Short-lived persistence (recommended, turns-based):**
   - Persist the most recent `resolvedTargets` into conversation state (chat session metadata) as `lastResolvedTargets`.
   - When a new turn arrives with **no new attachments** and **no activeAttachmentIds**, the pipeline may reuse `lastResolvedTargets` for a bounded window (e.g., last **3 turns**) to support “this/these/it” follow-ups.
   - Reuse rules must remain deterministic and bounded:
     - only reuse if the current request is target-dependent (see B.1),
     - never reuse when `activeAttachmentIds` is present (active wins),
     - expire on window/TTL or when conversation changes mode/position (if applicable).

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

#### B.1) Structural clarification when attachments exist but no active target is selected
To avoid relying on LLM flags (`requiresTargetResolution`) being set correctly:
- If `attachments[].length > 0` **and** `activeAttachmentIds` is empty/missing **and** the request is target-dependent (e.g., summarization/comparison/referring to “this/these/it”) **and** there is no `lastResolvedTargets` eligible for reuse (A.2), then return `CLARIFICATION_REQUIRED` with a message like:
  - “Select the attachment(s) you want me to use (activeAttachmentIds).”

This is domain-agnostic and contract-driven (“no active target selected”), and prevents the system from silently guessing.

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

### D) Best-effort validation for `attachments[].vectorSpace` (greenfield, extensible)
We should not fail by default when `attachments[].vectorSpace` is missing or unknown, because UI may attach arbitrary future content/doc types.

Implement validation during attachment normalization with best-effort semantics:

1) If `attachments[].vectorSpace` is missing/blank:
   - keep it `null` and continue.

2) If `attachments[].vectorSpace` is provided:
   - If the system can enumerate vector spaces (via `KnowledgeBaseOverviewService`) and the provided value is not in the allowlist:
     - mark it as invalid (warning metadata), and do **not** use it to scope retrieval.
     - still allow attachment grounding via pinned targets + content.

3) Optional strict mode (off by default):
   - in strict mode, invalid `attachments[].vectorSpace` becomes `CLARIFICATION_REQUIRED` and returns:
     - `invalidVectorSpace`
     - `allowedVectorSpaces[]`

---

### E) Update UI/client guidance (docs)
Update the UI migration guide / attachments docs to explicitly say:
- `vectorSpace` = indexed entity type / retrieval space (e.g., `product`)
- domain category belongs in `metadata` (e.g., `metadata.category = "Office Supplies"`)
- `activeAttachmentIds` must reference `attachments[].id`
- when attachments are present, use `activeAttachmentIds` to explicitly select targets for “summarize/compare/this/it” requests (unless relying on short-lived `lastResolvedTargets` reuse).

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

5) Follow-up turns can refer to prior pinned targets for a bounded window (e.g., 2–3 turns) when there are no new attachments:
   - “this/it/these” resolves deterministically via `lastResolvedTargets` reuse when eligible.

---

## Test Plan

### Unit tests
- Target pinning:
  - active attachments produce `resolvedTargets` even if intent doesn’t require target resolution
  - required target resolution still fails-closed when no active targets
- Context building:
  - context includes `id=` for docs
- Attachment validation:
  - invalid vectorSpace yields warnings by default (no failure)
  - strict mode yields CLARIFICATION_REQUIRED with allowlist
  - missing/blank vectorSpace is allowed (best-effort)

### RealAPI / Integration
- Chat capabilities demo:
  - send attachment + `activeAttachmentIds` and ask “summarize this”
  - verify response is grounded to the selected id/sku
  - verify `metadata.targetResolution` exists

---

## Additional Fix (required): standardize on `vectorSpace` end-to-end
We observed documents returned from the vector DB with missing space/type information (e.g., `type: null`), which prevents reliable grounding and scoping.

Greenfield decision:
- Standardize on **`vectorSpace`** as the canonical field name across:
  - vector DB search results
  - RAG documents/metadata
  - prompt/context headers (see C)

This ensures every retrieved document can be labeled and scoped deterministically, which is required for attachment grounding and follow-up resolution.

---

## Implementation Sequence (suggested)
1) Update `TargetResolutionStep` (pin always; fail-closed only when required).
2) Add doc-id to `buildContextFromDocuments(...)`.
3) Add doc `vectorSpace` enrichment (Option A in `RAG_DOCUMENT_VECTORSPACE_ENRICHMENT_CHANGE_PLAN.md`).
4) Standardize `vectorSpace` propagation end-to-end (vector DB → RAG → response metadata).
5) Add attachment vectorSpace validation (best-effort + optional strict mode).
6) Add `lastResolvedTargets` persistence + bounded reuse.
7) Update UI docs.
