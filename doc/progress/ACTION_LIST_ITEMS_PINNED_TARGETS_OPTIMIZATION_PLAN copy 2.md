# Action List Results → Pinned Targets (Pronoun Grounding) — Optimization Plan

## Status
Proposed

## Problem (observed)
After a READ/list-style action returns a list of results (via `ActionListPayload` / `_items`), follow-up user turns such as:
- “compare them”
- “buy it”
- “cancel it”
often fail because the LLM has **no deterministic reference** for what “it/them” means.

Today, AI Fabric pins targets from:
- `activeAttachmentIds` (explicit UI selection), and
- short-lived reuse of previously pinned targets stored in chat session metadata.

But action list results are **not** promoted into pinned targets (unless the client sends them back as attachments).

## Goals
- Treat action list results (`ActionListPayload._items`) as a **deterministic “working set”** that can be reused for 2–3 turns for follow-ups (“it/them/these”).
- Keep the solution **domain-agnostic** (no guessing keys like `orders`, `products`, `orderId`, `sku`, etc.).
- Preserve precedence:
  - **Active attachments always override** any pinned targets derived from action results.
- Keep it bounded and safe (size caps, snippet caps, no extra LLM calls).
- Prefer storing **references** (id/vectorSpace) over full content, and rehydrate on demand (fresh, smaller session metadata).

## Non-goals
- Parsing or inferring targets from arbitrary action messages (free text).
- Domain-specific extraction rules for “order id”, “sku”, “product name”, etc.
- Auto-scoping RAG retrieval based on pinned targets derived from action results (unless explicitly enabled later).

---

## Current contracts (baseline)
### Action result payload types (already enforced)
`ActionResult.data` is `ActionPayload` (sealed) and supports:
- `ActionListPayload` (list/search payload; uses reserved keys `_items`, `_count`, etc.)
- `ActionObjectPayload` (object payload; cannot contain reserved list keys)

Reference:
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/action/ActionPayload.java`
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/action/ActionListPayload.java`
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/action/ActionResultContracts.java`

### Pinned targets (current)
Pinned targets are represented as `ResolvedTarget` and come from:
- active attachments → `resolvedTargets` (authoritative)
- session metadata reuse (short window) → `resolvedTargets` (authoritative)
- working-set seeding (only when LLM marks `requiresTargetResolution=true`)

---

## Proposed solution

### A) Define a deterministic “pinnable list item” contract (domain-agnostic)
To promote action list items into pinned targets, the orchestrator needs an identifier that is stable and explicit.

**Contract for a pinnable item (minimum)**
- `id`: required (stringifiable, non-blank)
- `vectorSpace`: optional (stringifiable, non-blank)
- `contentText`: optional (stringifiable, bounded)
- `metadata`: optional (map)

**Key rule**
The framework will **only** pin action list items when it can deterministically extract an `id`.
No guessing from domain keys like `orderId` / `sku` / `orderNumber`.

**Developer ergonomics**
Provide a small helper DTO (or builder) for action handlers to return pinnable items without hand-writing maps.
Example direction (names illustrative):
- `ActionTargetRef` (id, vectorSpace?, contentText?, metadata?)
- `ActionResultContracts.pinnableItems(List<ActionTargetRef>)`

This keeps the behavior deterministic and avoids domain coupling.

---

### B) Promote list results to pinned targets during conversation recording
When the current turn’s result is `ACTION_EXECUTED` (or other action outcomes where it makes sense) and the action returns:
- `ActionResult.data instanceof ActionListPayload`
- and `_items` contains pinnable items (per contract),
then persist those as `lastResolvedTargets` in session metadata with source:
- `ResolvedTargetSource.ACTION_RESULT_ITEMS` (new enum value)

**Caps (bounded)**
- Max pinned targets stored from action list: `N` (reuse existing limit like 8)
- Max snippet chars: reuse attachment/snippet limits (or a small dedicated cap)
- Max metadata keys: reuse existing cap logic (domain-agnostic)

**Important precedence behavior**
- If the request includes `activeAttachmentIdsResolved` (active attachments selected), those remain authoritative.
- Pinned targets derived from action list items are only used when **no active attachments are selected** on subsequent turns.

---

### C) Reuse rules on follow-up turns (pronoun grounding)
Reuse the existing short-lived pinned-target reuse mechanism:
- If a new turn arrives with **no attachments** and **no activeAttachmentIds**, seed `resolvedTargets` from `lastResolvedTargets` for `pinnedTargetReuseWindowTurns` (default 3).

Because active attachments are authoritative, they naturally override:
- The enrichment step must continue to skip seeding from metadata when active attachments are present.

---

### D) Do not scope retrieval based on action-result pinned targets (by default)
Action-result pinned targets are for:
- grounding the conversation (“it/them”) and
- improving action parameter filling (indirectly, via prompt context / target resolution steps).

They should **not** narrow RAG vectorSpace by default, because:
- many actions return operational data that is not indexed
- scoping RAG can unintentionally hide relevant context

If we later want scoping, it should be a separate, explicit feature flag.

---

### E) Store only refs and rehydrate on follow-up turns (recommended)
To keep conversation state small and avoid staleness, do not persist full documents from action results.

**Store**
- `id` (entityId; required)
- `vectorSpace` (entityType; optional but preferred)
- optional `vectorId` as a cache hint (must be treated as non-authoritative; reindex can change it)
- optional minimal metadata needed for UX (bounded)

**Rehydrate (best-effort)**
When seeding `resolvedTargets` from session metadata and the target lacks a `contentText` or useful metadata:
- If `vectorSpace` + `id` are present, try `VectorDatabaseService.getVectorByEntity(vectorSpace, id)` to fetch the latest content/metadata for prompt grounding.
- If only `vectorId` is present, optionally try `VectorDatabaseService.getVector(vectorId)` as a fallback.
- If rehydration fails, keep the target as an `id`-only pin (still useful for deterministic referencing).

**Precedence**
- If the current request has `activeAttachmentIdsResolved`, skip rehydration/seed-from-metadata entirely (active attachments are authoritative).

---

## Implementation outline (high-level)

1) **Add a new `ResolvedTargetSource` value**
   - `ACTION_RESULT_ITEMS`

2) **Add an action-item → ResolvedTarget extractor**
   - Input: `ActionListPayload`
   - Output: bounded `List<ResolvedTarget>`
   - Deterministic extraction rules:
     - If item is `Map`: read `id`, `vectorSpace`, `contentText`, `metadata`
     - If item is a POJO: best-effort convert to map (bounded), then apply same rules
     - Skip items with missing/blank `id`

3) **Persist extracted targets into chat session metadata**
   - Store under the same key used for pinned-target reuse (e.g., `lastResolvedTargets`)
   - Only store if:
     - no active attachments are selected for the request, OR
     - you intentionally want “most recent wins” and allow action results to replace previous pins
   - Ensure the source is captured so debugging can show where pins came from

4) **Ensure attachments override**
   - Confirm reuse step does nothing when `activeAttachmentIdsResolved` is present.
   - Confirm target resolution logic always prefers active attachments.

5) **Add optional rehydration step**
   - When `resolvedTargets` are seeded from session metadata (not active attachments), rehydrate missing snippet/metadata via `VectorDatabaseService`.
   - Keep it bounded (max targets, max chars).

---

## Test plan (deterministic, no LLM dependency)

### Unit tests
- Given `ACTION_EXECUTED` with `ActionListPayload` containing two pinnable items (`id` present):
  - session metadata stores `lastResolvedTargets` with 2 entries and source `ACTION_RESULT_ITEMS`
- Given `_items` with no valid `id` fields:
  - no targets are stored (or stored list is empty)
- Given a follow-up request with no attachments and within reuse window:
  - `ConversationEnrichmentStep` seeds `resolvedTargets` from session metadata
- Given a follow-up request with `activeAttachmentIdsResolved`:
  - session metadata seeding is skipped and active attachments remain authoritative
- Given stored action-result targets with only `id` + `vectorSpace` and the vector exists:
  - rehydration populates snippet/metadata using `VectorDatabaseService.getVectorByEntity`
- Given stored action-result targets but vector lookup misses:
  - targets remain usable as `id`-only pins (no failure)

### Real app smoke check (manual)
- Call a READ action that returns `ActionListPayload` (e.g., list products) where each item includes `id`.
- Next message: “compare them” / “summarize them”
  - Verify pinned targets are present and reused (metadata shows source and count).

---

## Notes / Open questions (for review)
1) Should action-result derived targets share the same `lastResolvedTargets` slot (most recent wins),
   or be stored separately (e.g., `lastActionResultTargets`) and merged with attachments?  
   Recommendation: **reuse one slot** but ensure active attachments always override.

2) Should we also support pinning for `ActionObjectPayload` (single-item actions)?  
   Recommendation: yes, but as a follow-up optimization after list pinning is stable.
