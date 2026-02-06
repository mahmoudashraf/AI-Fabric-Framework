# Compound Results: Treat `CONFIRMATION_REQUIRED` as “Pending”, Not “Failure” — Change Plan

## Status
Implemented

## Problem (Observed)
In compound extraction scenarios (multiple intents in a single user turn), the orchestrator may return:

- top-level: `type=COMPOUND_HANDLED`, `success=false`, `message="Some intents failed"`
- children/results: one or more intents are `type=CONFIRMATION_REQUIRED`, `success=false`

This is **misleading**:
- `CONFIRMATION_REQUIRED` means “blocked waiting for user confirmation”, not “failed”.
- Aggregating it as a failure makes UI/debugging confusing and encourages wrong downstream logic (treating a safe “pending” state as error).

Example (from `Debug/context.txt`):
- two extracted `add_to_cart` actions both returned `CONFIRMATION_REQUIRED`
- yet top-level returned “Some intents failed”

## Goals
1. **Differentiate “pending” from “failed”** at the compound aggregation layer.
2. Make top-level result types/messages match user-visible reality:
   - “Please confirm …” rather than “Some intents failed”
3. Keep the behavior **deterministic** and provider-agnostic.
4. Preserve debuggability: do not hide child outcomes.

## Non-goals
- Changing the confirmation model (LIFO stack, confirmation resolution, etc.).
- Adding domain heuristics.
- Backward compatibility (greenfield), but we should keep API shape stable for consumers.

## Current Behavior (Likely)
The compound aggregator (normalization step / compound result wrapper) treats any child with `success=false` as a failure and sets:
- `COMPOUND_HANDLED.success=false`
- message “Some intents failed”

Because `CONFIRMATION_REQUIRED.success=false` by design, compound results become “failed” even when all children are simply pending.

## Proposed Behavior

### A) Introduce a “pending” classification for orchestration results
Define a deterministic classification rule:

**Pending types:**
- `CONFIRMATION_REQUIRED`
- `CLARIFICATION_REQUIRED`

**Failure types:**
- `ERROR`
- `ACTION_DENIED` (depending on policy; typically treated as failure)

**Success types:**
- `INFORMATION_PROVIDED`
- `ACTION_EXECUTED`
- `COMPOUND_HANDLED` (when it contains at least one success and no failures)

Notes:
- We intentionally **do not** change the `success` boolean on child results.
- We change how the **compound wrapper** interprets the children.

### B) Compound aggregation rules
Given `children/results`:

1) **All pending** (every child is pending; no failures, no successes)
   - **Implemented behavior:** promote the **next pending child** to top-level:
     - `type=CONFIRMATION_REQUIRED` if any child is confirmation-required, else `CLARIFICATION_REQUIRED`
     - `success=false`
     - `message` = the next pending child message (e.g., `"Confirm …?"`)
     - preserve `children` as-is for auditing/debug.
   - Add debug metadata: `metadata.compoundAggregation` with counts + rule applied.

2) **Mix of pending + success (no failures)**
   - Return `type=COMPOUND_HANDLED`
   - `success=true` (because nothing failed; the pipeline did what it could)
   - message: `"Some items completed; some require confirmation/clarification."`
   - Keep `children` with full details.

3) **Any failure present**
   - Return `type=COMPOUND_HANDLED`
   - `success=false`
   - message: `"Some items failed; others may require confirmation/clarification."`
   - Keep full details; add `metadata.compoundAggregation` with counts + rule applied.

### C) UI-Friendly “next pending”
Optional (but recommended for UX):
- If multiple confirmations are extracted, return **only the next confirmation** as the top-level result, and store the rest in the pending-action stack.
- This reduces UI complexity (one confirm prompt at a time).

Implemented: when all children are pending, the normalizer returns a single pending result at top-level (while preserving children).

## Implementation Plan (Phase 1: Aggregation Fix)

1) Locate the compound aggregation logic:
   - likely in `OrchestrationResultNormalizationStep` or a compound wrapper in pipeline.
2) Implement `isPending(resultType)` helper:
   - `CONFIRMATION_REQUIRED`, `CLARIFICATION_REQUIRED`
3) Compute counts:
   - `successCount`, `pendingCount`, `failureCount`
4) Apply the aggregation rules above.
5) Add debug metadata:
   - `compoundAggregation: { successCount, pendingCount, failureCount, topLevelType, ruleApplied }`
6) Update tests:
   - Case: two confirmations → top-level `CONFIRMATION_REQUIRED`, message mentions confirmation required
   - Case: success + confirmation → top-level `COMPOUND_HANDLED`, `success=true`
   - Case: error + confirmation → top-level `COMPOUND_HANDLED`, `success=false`

## Implementation Plan (Phase 2: Optional “single confirmation at a time”)
Only if desired after Phase 1:

1) When compound extraction yields multiple confirmation-required actions:
   - push all pending actions into confirmation stack
   - return only the top-of-stack confirmation prompt
2) Ensure stack order is deterministic (e.g., LIFO of extracted order).
3) Update chat session tests to verify:
   - multiple confirmables → one prompt per turn

## Acceptance Criteria
- A compound response containing only `CONFIRMATION_REQUIRED` children returns a top-level confirmation-oriented result/message (not “Some intents failed”).
- No change is needed in intent extraction; provenance validation continues to work.
- Debug metadata clearly indicates which compound aggregation rule was applied.

## Rollout / Compatibility Notes
- API shape: keep `data.results`/`children` so clients can still render full details.
- Only top-level `type/success/message` changes to be semantically correct.

## Implemented In
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/OrchestrationResultNormalizer.java`
