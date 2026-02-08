# Write Action Results → Pinned Targets (Conversation State)

## Problem
After a **WRITE** action executes (create order, add to cart, cancel order, change address, …) the user often follows up with a **target‑dependent** message:
- “cancel it”
- “change the address”
- “what’s the status?”

Today, the “pinned targets” in conversation state are typically still the **UI attachments** (products) or the **previously pinned set**, not the newly created/updated entity (order/cart/subscription). This makes follow‑ups unreliable and causes the LLM to ask again for identifiers it should already have.

## Goals
- **Pin WRITE action results** into conversation “pinned targets” when the action explicitly returns pinnable targets.
- When **both** UI attachments (user selection) and a successful WRITE action result exist in the **same turn**, **pin both** (single persisted list, ordered + deduped).
- Keep the framework **domain‑agnostic** (no heuristics like “if map contains orderId/sku…”).
- Keep it **explicit + deterministic** (action must opt‑in by returning pinnable targets using a contract).
- Do **not** pin READ actions by default.
- Pinned targets persisted into conversation state should **replace** the previous pinned list (existing behavior of `lastResolvedTargets`).

## Non‑Goals
- Do not infer pinnable targets from arbitrary `Map<String,Object>` payloads.
- Do not pin “empty” results (success=true but no pinnable targets).
- Do not change RAG query composition in this change.

---

## Contract (Framework‑level)

### 1) Action opt‑in: explicit pinnable targets
Introduce an explicit, typed place for pinnable targets on `ActionResult`:

- `ActionResult.pinnedTargets: List<ActionTargetRef>`

Notes:
- `ActionTargetRef` is already domain‑agnostic and strongly typed (`id`, optional `vectorSpace`, optional `contentText`, optional `metadata`).
- If an action does not return `pinnedTargets`, it does not update conversation pinned targets.

### 2) Pinning eligibility: WRITE only
Only persist pinned targets when the executed action’s metadata indicates:
- `accessMode == WRITE_ONLY` or `accessMode == READ_WRITE`

READ actions remain “helper tools” (their outputs can still be returned to the user, and can still influence working‑set logic, but they **must not** overwrite conversation pinned targets).

---

## Pipeline / Storage Behavior

### A) Where pinning happens
Pinning should happen during **conversation recording** (chat‑session module), because that’s where conversation state is persisted.

Update `ConversationRecordingStep.persistPinnedTargets(...)` to treat “WRITE action pinnedTargets” as **fresh** targets (same as “request attachments”).

### B) Single persisted list (pin both when present)
Persist a **single** `lastResolvedTargets` list, but build it deterministically each turn:
1. **WRITE result pinnable targets** (if a WRITE/READ_WRITE action executed successfully and returned `ActionResult.pinnedTargets`)
2. **UI selection targets** (request attachments / resolved targets)

Rules:
- Preserve **origin source** per target when persisting (e.g., `originSource=ACTION_RESULT_ITEMS` vs `originSource=REQUEST_ATTACHMENTS`).
- **Deduplicate** by stable identity when available (recommended key: `(vectorSpace,id)`; fallback to `(vectorSpace,contentText)` when `id` is missing and `storeIdlessTargets=true`).
- **Order matters**: write-result targets first, then user-selection targets. This keeps follow-up actions (“cancel it”, “change address”) grounded on the latest created/modified entity, while still allowing comparisons over the user’s selected products.

LLM-facing rendering:
- Render the single list as **two labeled groups** in the pinned-targets block using `originSource`:
  - “PINNED TARGETS — Write Result (latest): …”
  - “PINNED TARGETS — User Selection (attachments): …”

This keeps storage simple (one list) without losing the semantic distinction the model needs.

### C) Reuse window (no change)
Conversation reuse window remains controlled by:
- `ai.chat.pinnedTargetReuseWindowTurns`

The existing behavior (“reuse pinned targets for N turns when no new attachments are provided”) continues to work, but now the pinned set can be updated by WRITE actions.

---

## Implementation Tasks

### 1) Core: extend `ActionResult`
- Add `List<ActionTargetRef> pinnedTargets` to `ActionResult`.
- Keep JSON serialization compatible with existing output (new field is additive).

### 2) Chat-session: persist pinned targets from WRITE actions
In `ConversationRecordingStep`:
- Read `actionResult.pinnedTargets` when result type is `ACTION_EXECUTED` and `actionResult.success=true`.
- Read `AIActionMetaData.accessMode` from `OrchestrationResult.data.metadata` to ensure the action is WRITE/READ_WRITE.
- Convert each `ActionTargetRef` into a `ResolvedTarget` (origin `ACTION_RESULT_ITEMS`).
- Merge with request attachment resolved targets (origin `REQUEST_ATTACHMENTS`) when present, then persist into session metadata `lastResolvedTargets` (respect existing persistence limits: maxTargets, maxContentChars, maxMetadataEntries, …).

### 3) Chat-session: enrichment + rendering update
`ConversationEnrichmentStep` continues to load `lastResolvedTargets` into:
- `PipelineContext.resolvedTargets`
- `PipelineContext.pinnedTargetsContext`

Update the pinned-targets renderer to group by the persisted `originSource` (write-result vs user-selection), while still treating the targets as “previously pinned” on subsequent turns.

### 4) Real apps: opt‑in by returning pinned targets
Update WRITE actions that should enable follow‑ups to return `pinnedTargets`, e.g.:
- `create_purchase_order` → pin the created order (orderId/orderNumber as `id`)
- `add_to_cart` → pin the active cart (cartId as `id`) and optionally the added items (if needed)
- `cancel_purchase_order` / `change_delivery_address` / etc → pin the affected order/cart

This is an app‑level choice: the framework only provides the mechanism.

---

## Validation / Tests

### Unit tests (core)
- `ActionResult` serialization/deserialization (Jackson) includes `pinnedTargets`.

### Integration tests (chat-session module)
Scenario:
1) Execute WRITE action that returns pinned target (e.g., create order returns pinned order ref).
2) Next turn user says “cancel it”.
Expected:
- Intent extraction can resolve target via “previously pinned targets”.
- The system does not ask again for `orderNumber` if the pinned target includes it in metadata/content.

### Manual test (chat-capabilities-demo)
1) Create purchase order.
2) Follow-up: “cancel it”.
3) Confirm once.
Expected: cancellation succeeds without asking for order id/number again.

---

## Open Decisions (for review)
1) **Multiple WRITE actions in one turn** (compound):
   - Option A (simplest): persist pinnedTargets from the **last executed** WRITE action that returned pinnedTargets, then append attachments.
   - Option B: merge pinnedTargets from all executed WRITE actions (bounded), then append attachments.
