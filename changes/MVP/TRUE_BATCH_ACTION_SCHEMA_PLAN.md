# True Batch Action Schema (Multi-Target Actions)

## Problem
When the UI pins multiple targets (attachments / stored pinned targets) and the user asks for an ACTION that can apply to *all* of them (example: `add_to_cart`), we observed:
- The LLM sometimes emits **one ACTION per target** (compound) instead of a single batch action.
- The LLM sometimes emits a **single batch action** but only includes **one** item in the batch parameter.
- The user experience becomes confusing (multiple confirmations, repeated confirmations, or “confirmed 1 but outcome looks like 2”).

This is especially common for “apply-to-all” actions when the user didn’t explicitly narrow scope.

## Goals
- Support “true batch” actions using a **schema-driven** contract (no domain heuristics).
- Ensure multi-target action requests produce **one confirmation + one execution** when the action is batch-capable.
- Keep the system **greenfield** and **provider-agnostic**.

## Non-Goals
- Do not scan arbitrary result maps for domain keys like `sku`, `orderId`, `items`, etc.
- Do not introduce string-matching heuristics for “compare/add/buy” keywords in the backend.

---

## Contract (Framework-level)
Actions can declare a **batch-capable array parameter** via parameter schema:
- Action param is `type=ARRAY`
- Param schema has `batchTargets=true`
- `items` schema defines the object shape for one target

In annotations this is expressed via:
- `@Param(batchTargets=true)` on an array/collection param.

The LLM is instructed (prompt rule 7b) that:
- If multiple pinned targets exist and the action has a `[batchTargets]` array param, it **must** return a **single ACTION** intent and populate that array with one element per pinned target by default.

---

## Runtime Optimizations (Production-safe)

### 1) Compound → Batch Coalescing (schema-driven)
If the model emits multiple ACTION intents for the same action, and the action exposes a batch-capable array param:
- Coalesce them into **one ACTION intent**
- Concatenate the batch param list across the intents
- Run **one** confirmation/execution

This is implemented in `IntentHandlingStep.handleCompoundIntents(...)` by calling `coalesceBatchActionIntents(...)` before processing child intents.

### 2) Batch Param Defaulting / Expansion (schema-driven)
If an ACTION has a batch-capable array param and resolved targets exist:
- Expand the batch param list using `PipelineContext.resolvedTargets`
- Map each resolved target’s `metadata` (case-insensitive key match) into the item schema
- Default numeric `quantity` fields to `1` when absent
- Deduplicate by item key computed from the item schema property names/values

This is implemented in `IntentHandlingStep.applyBatchTargetsDefaulting(...)` and runs before required-param validation.

Outcome:
- If the LLM only provided one batch element but multiple targets exist, we still converge to a full batch list *without* domain heuristics.
- The user sees the full batch reflected in the confirmation message (action handler responsibility).

---

## How to Test (Manual)
Chat-capabilities demo:
1) Pin 2+ products as UI attachments.
2) Send: “add to cart”.
Expected:
- Confirmation message mentions multiple items (count + a short list).
- Accept once → action executes once.
- Cart reflects all pinned items.

## How to Test (Automated)
Core unit tests:
- `IntentHandlingStepBatchTargetsTest`:
  - Expands a single-item batch param to all resolved targets.
  - Coalesces multiple ACTION intents into one execution with a merged batch list.

