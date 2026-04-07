# Chat Sessions: Persist Pinned Targets With Full Content (Including ID‑less Attachments) — Change Plan

## Status
Proposed

## Problem (current behavior)
We support “pinned targets reuse” across turns via chat-session `sessionMetadata.lastResolvedTargets`.

Today this persistence is **ID-driven**:
- We only persist pinned targets when `ResolvedTarget.id` is present.
- ID‑less attachments (pure text/notes/snippets) are **pinned for the current request only** and are lost on the next turn.
- When seeding `resolvedTargets` from session metadata, we currently require `id` and may “rehydrate” missing fields from the vector DB.

This causes real UX issues:
- A user can attach arbitrary context (often without IDs) and expects it to remain usable for a short follow-up window.
- Debug becomes confusing: the stored target’s original source may still be `REQUEST_ATTACHMENTS`, even though it was loaded from session metadata on later turns.
- Rehydration is unnecessary when the UI already provided the full content (and can re-send it), and it adds coupling to vector DB availability.

## Goals
1) Persist pinned targets for a short reuse window **even when `id` is missing** (ID‑less attachments survive).
2) Store pinned targets as **full documents** (bounded by configuration), not just IDs:
   - no rehydration required for reuse
3) Keep the behavior greenfield and framework-safe:
   - explicit configuration knobs
   - bounded storage (count/size)
   - clear observability of “source this turn” vs “origin”

## Non‑goals
- Long-term knowledge-base storage (this is **chat context**, not indexing).
- Cross-session sharing of pinned targets.
- Rehydration from vector DB by default (we can keep it as an optional future mode, but the default should not require it).

---

## Proposed design

### A) Persist “Last Pinned Targets” as documents (session metadata)
Replace the current “ID-only-ish” persistence with a **document persistence** structure.

**Session metadata keys (existing keys kept, schema updated):**
- `lastResolvedTargets` : `List<Map<String,Object>>`
- `lastResolvedTargetsTurnIndex` : `int`

**Each entry in `lastResolvedTargets` may contain:**
- `id?: string` (optional)
- `vectorSpace?: string` (optional)
- `contentText?: string` (optional, may be long)
- `metadata?: object` (optional; scalar values)
- `originSource?: string` (e.g., `REQUEST_ATTACHMENTS`, `WORKING_SET`, `ACTION_RESULT`)
- `storedAtTurnIndex: int` (optional)

**Important:** This list is **replaced** each time we persist; it should not grow unbounded across turns.

### B) Make persistence configurable (count + content size)
Add (or extend) chat-session configuration:

```yaml
ai:
  chat:
    pinned-target-reuse-window-turns: 3
    pinned-target-persistence:
      enabled: true
      max-targets: 8
      max-content-chars: 0   # 0 = unlimited (no extra truncation at persistence layer)
      max-metadata-entries: 12
      max-metadata-value-chars: 120
      store-idless-targets: true
```

Notes:
- “Unlimited” here means: *do not apply an additional persistence truncation*.
  - Attachment normalization may still truncate based on `ai.orchestration.attachments.max-content-text-chars`.
  - Developers can raise that limit if they truly want to persist larger content.
- `max-targets` is always enforced regardless of “unlimited chars”.

### C) Seed `resolvedTargets` from session metadata (including ID‑less)
Update seeding to accept and restore ID‑less entries:
- If the current request has **no new request attachments**, and reuse window allows it:
  - load `lastResolvedTargets` from session metadata
  - convert each entry into a `ResolvedTarget`
  - **do not require `id`**

**Source correctness:**
- Set `ResolvedTarget.source = SESSION_METADATA` for seeded targets (this answers “where did we get it this turn?”).
- Preserve the original origin (if present) as `ResolvedTarget.metadata["originSource"]=...` or an explicit `originSource` field in the stored map.

### D) No rehydration (default)
When we store full content/metadata, rehydration is not required for reuse.

Default behavior:
- Do not call vector DB during seeding.
- The stored content/metadata are the grounding payload.

Optional (future):
- A separate mode could store only refs and rehydrate, but this is explicitly out of scope for this change.

### E) Ensure pinned targets are visible to the LLM on follow-up turns
If pinned targets are reused from session metadata, they must be made visible to:
- intent extraction (to interpret “them/these/it”)
- generation (for grounded answers/actions)

Implementation options:
1) When seeding `resolvedTargets`, also set `PipelineContext.pinnedTargetsContext` using the same “PINNED TARGETS” block renderer.
2) Add a dedicated step (after conversation enrichment) that injects a pinned targets block from `resolvedTargets` **only when** there are no request attachments and `pinnedTargetsContext` is empty.

This prevents “pinned targets exist but extractor sees none”.

---

## Security & privacy
- Persist **processed/normalized** content where possible:
  - request attachments already pass through attachment normalization (and optional PII processing)
- If `PIIDetectionService` is present, optionally run redaction on `contentText`/`metadata` before persistence.
- Do not persist raw provider payloads.

---

## Observability (debug)
Add/adjust debug metadata so behavior is explainable:

- `targetResolution.source`:
  - `REQUEST_ATTACHMENTS` when the request included attachments
  - `SESSION_METADATA` when seeded from session state
- `targetResolution.count`
- Optional: `targetResolution.originSources=[...]` (bounded)

This avoids the confusion where “REQUEST_ATTACHMENTS” appears even when attachments were not sent in the request.

---

## Tests
Unit tests (chat-session module):
1) Persist pinned targets includes ID‑less target when `store-idless-targets=true`.
2) Seed from session metadata restores ID‑less target into `resolvedTargets` within reuse window.
3) Seed sets `source=SESSION_METADATA` and preserves `originSource`.
4) When request includes attachments, seeding is skipped (request attachments remain authoritative).

Pipeline tests (core):
5) Intent extraction sees pinned targets context when seeded from session metadata (no request attachments).

---

## Acceptance criteria
1) An attachment with **no id** can be used, persisted, and referenced for N follow-up turns without re-sending it.
2) No vector DB calls are required for pinned target reuse (default path).
3) Debug clearly shows whether targets came from **request attachments** or **session metadata** on that turn.

