# Plan: Break Confirmation Loops (Multi‑Step Extractor Supports Confirmation + Optional Pending-Action Injection)

## Status
Implemented (Phase 1 + Phase 2)

## Problem
When an action requires confirmation, the system returns `CONFIRMATION_REQUIRED`.
If the user replies “yes/confirm”, the intent extractor sometimes **does not output** `CONFIRMATION_POSITIVE`.
Instead it emits a fresh `ACTION` intent again (e.g., `add_to_cart`), causing the system to ask for confirmation again → **infinite confirmation loop**.

This happens most often when extraction falls back to the **multi-step** strategy because its `classify.md` schema currently only supports:
`ACTION | INFORMATION | OUT_OF_SCOPE`.

So even if the model “knows” it’s a confirmation reply, the schema prevents it from returning `CONFIRMATION_POSITIVE/NEGATIVE`.

## Goals
1. Make confirmation replies reliably produce `CONFIRMATION_POSITIVE` / `CONFIRMATION_NEGATIVE`.
2. Eliminate confirmation loops without backend string-matching heuristics.
3. Keep the solution provider-agnostic and consistent across extraction strategies.

## Non-goals
- Redesigning confirmation UX beyond “one action at a time” (handled elsewhere).
- Adding backend heuristics such as `if query contains "yes"` or “confirm”.

## Root Cause (Observed)
- `IntentHandlingStep` only executes a pending action when it receives `CONFIRMATION_POSITIVE` and calls `handleConfirmationPositive()` which pops the pending action from `PendingActionStore`.
- If the extractor returns `ACTION add_to_cart` again, `IntentHandlingStep.handleAction()` re-creates a new pending action and returns `CONFIRMATION_REQUIRED` again.
- Multi-step extractor’s classification schema disallows `CONFIRMATION_POSITIVE/NEGATIVE`, so it cannot emit the correct intent type.

## Proposed Solution

### Phase 1 (Required): Add confirmation intent types to multi-step classification contract
Update prompt contract for multi-step classification so it can emit confirmation intents.

**Change**
- Update `prompts/intent-extraction/multi-step/**/classify.md` schema to include:
  - `CONFIRMATION_POSITIVE`
  - `CONFIRMATION_NEGATIVE`
  - (Optional) `COMPOUND` if the strategy supports it today

**Rules update (prompt only, not backend matching)**
- If a pending confirmation exists (conversation context indicates pending action) AND the user message is primarily confirming/rejecting → emit `CONFIRMATION_POSITIVE` or `CONFIRMATION_NEGATIVE`.
- When emitting confirmation intents, do **not** emit a new `ACTION` intent in the same response unless the user clearly requested a new action as well.

**Why this is not string matching**
- The model reasons over:
  - pending-action context (see Phase 2 optional), and/or
  - conversation history messages (assistant asked a confirmation question)
  - user reply semantics (“confirm/reject”)
- The backend does not implement `yes/no` heuristics.

### Phase 2 (Optional but recommended): Inject pending-action context into extractor input
To reduce reliance on long history windows (and reduce flakiness), inject a small, bounded “pending action” block to the extractor when `PendingActionStore.peekPendingAction()` exists.

**Change**
- In `ConversationEnrichmentStep` (or a new small step right after it), when conversation exists:
  - `peekPendingAction(conversationId, ownerId)`
  - If present: add a structured snippet to `PipelineContext.pinnedTargetsContext` or a dedicated `PipelineContext.pendingActionContext` field.

**Proposed content (bounded)**
```
PENDING ACTION (requires confirmation):
- action=add_to_cart
```

**Notes**
- Keep this PII-safe. Do not include raw param values or free-form descriptions/addresses/emails.
- This is *state*, not “instructions”; it should be included alongside user context so the extractor can classify correctly.

### Phase 3 (Validation): Add deterministic tests for loop prevention
Add tests that fail if “confirm” produces `ACTION` again.

**Integration / unit tests**
- Simulate:
  1. user: “add to cart”
  2. assistant: `CONFIRMATION_REQUIRED` (pending action stored)
  3. user: “yes”
  4. expected: `ACTION_EXECUTED` (pending popped and executed once)

**Test matrix**
- Extractor path: compound strategy
- Extractor path: multi-step fallback strategy (the key regression case)

## Implementation Details (Files)

### Prompts
- `ai-infrastructure-module/curated/ai-curated-default/src/main/resources/prompts/intent-extraction/multi-step/v1/classify.md`
- Any curated overlays that ship their own `classify.md`:
  - commerce/catalog/support variants (if present)

### Optional context injection
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/pipeline/steps/PendingActionPromptAugmentationStep.java`
  - Peeks pending action and injects a bounded `PENDING ACTION` block into `PipelineContext.pinnedTargetsContext`.

### Tests
- Unit tests:
  - `ai-infrastructure-module/ai-infrastructure-core/src/test/java/com/ai/infrastructure/intent/extraction/MultiStepIntentExtractionStrategyTest.java`
  - `ai-infrastructure-module/ai-infrastructure-core/src/test/java/com/ai/infrastructure/intent/orchestration/pipeline/steps/PendingActionPromptAugmentationStepTest.java`

## Rollout / Config
- Phase 1 is always-on (prompt contract fix).
- Phase 2 injection should be configurable (default on for chat-session enabled):
  - `ai.chat.confirmationContext.enabled=true`
  - `ai.chat.confirmationContext.maxChars=...`

## Acceptance Criteria
- After an action returns `CONFIRMATION_REQUIRED`, replying “yes/confirm” executes the pending action exactly once (no repeated confirmation prompts).
- Multi-step fallback strategy can emit confirmation intents (schema + rules).
- No backend string matching for confirmation detection.
