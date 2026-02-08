# RAG Documents: Include `vectorSpace` / `entityType` per Document — Change Plan

## Status
Proposed

## Problem
In orchestrator responses, retrieved documents often show:

- `"type": null`

This is confusing and blocks key UI workflows:
- The UI cannot reliably turn a retrieved document into a follow-up **attachment** (e.g., “Add *this* to cart”) without knowing which **vector space / entity type** the document ID belongs to.
- In multi-space retrieval (fan-out), a top-level `entityType` string (e.g., `"product,order"`) is not sufficient; the UI needs this **per document**.

This also aligns with the attachments + deterministic target resolution philosophy:
- `OrchestrationAttachment` already requires `vectorSpace`.
- Retrieved documents should carry the same identifier so the UI can round-trip references deterministically.

## Goals
- Every retrieved document returned to clients carries an explicit, framework-generic identifier:
  - `vectorSpace` (preferred) or `entityType` (equivalent concept).
- The orchestrator response becomes self-describing for UI selection / follow-up referencing.
- No domain-specific logic or key guessing (no “products/items/orders” heuristics).

## Non-goals
- Introducing a domain model into the framework.
- Changing how vector search ranks documents.
- Expanding payload size unboundedly.

---

## Proposed Contract Change (greenfield)

### Option A (preferred): Add `vectorSpace` to `RAGResponse.RAGDocument`
Add a first-class field:
- `vectorSpace: string`

Rules:
- In single-space retrieval, `vectorSpace` == request/entityType used for retrieval.
- In fan-out retrieval, `vectorSpace` is set per document to the originating space.

This avoids ambiguity around the existing `type` field.

### Option B (compat-friendly but less clear): Populate `type` with vectorSpace
If we want to avoid adding a new field:
- Set `RAGDocument.type = vectorSpace`

This is easy but conflates “document type” with “vector space/entity type”. It also keeps an unclear name in the API.

### Reserved metadata keys (optional)
Even with Option A, we may also include a reserved metadata key for convenience/debug:
- `metadata._vectorSpace = <vectorSpace>`

Constraint:
- Reserved keys must be namespaced (`_vectorSpace`) to avoid collisions with user metadata.

---

## Implementation Plan

### Step 1 — DTO update
Update `com.ai.infrastructure.dto.RAGResponse.RAGDocument`:
- Add `private String vectorSpace;`
- Document the meaning: “knowledge base scope/entity type the document belongs to”.

If Option A is chosen, the existing `type` field should be treated as deprecated and no longer relied on (greenfield: update callers/tests immediately).

### Step 2 — Ensure single-space retrieval tags documents
In the orchestration pipeline’s retrieval path (single space), set `vectorSpace` on every returned document before returning the `OrchestrationResult`.

### Step 3 — Ensure fan-out retrieval tags documents
Fan-out already has the “originating vector space” available; ensure each merged document carries:
- `vectorSpace` (preferred) and optionally `metadata._vectorSpace`.

### Step 4 — Observability
In debug metadata (non-user-facing), record:
- `rag.vectorSpacesUsed[]`
- `rag.documentsTagged=true`

---

## Testing Plan

### Unit tests
- Single-space retrieval:
  - returned docs all have `vectorSpace == intent.vectorSpace`.
- Fan-out retrieval:
  - merged docs preserve correct per-doc vectorSpace.
- If reserved metadata keys are used:
  - ensure `_vectorSpace` exists and is bounded.

### Integration (Real Apps)
- From retrieved docs, UI can build attachments:
  - `attachments[].id = doc.id`
  - `attachments[].vectorSpace = doc.vectorSpace`
- Follow-up “Add it to cart” should deterministically select the correct target when `activeAttachmentIds` is set.

---

## Rollout Notes
- This is a contract improvement. Update Real Apps and demo UI to prefer `doc.vectorSpace` when creating attachments or rendering results.
- Keep payload bounded (do not duplicate large snippets; only add the scalar `vectorSpace` string).

