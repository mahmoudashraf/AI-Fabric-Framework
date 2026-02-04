# Confirmation Context Gating + Disambiguation — Change Plan

## Status
Proposed

## Problem
In chat mode, the system may have a **pending action** awaiting confirmation (yes/no). To help the LLM, the chat pipeline injects a “CONFIRMATION CONTEXT” block into the processed query.

In practice, this can cause “context creep” where unrelated user requests get misclassified as confirmation intents, especially when the user’s message includes words like:
- “cancel”, “no”, “stop”, “abort”

Example failure:
- A pending action exists (e.g., “apply discount?”).
- User later says: “cancel order 2”.
- LLM interprets this as `CONFIRMATION_NEGATIVE` (cancel the pending confirmation) instead of selecting `cancel_purchase_order`.
- The orchestrator responds with a generic “Okay — all sorted…” and never cancels the order.

This looks like looping or ignoring the user, and it becomes more frequent as systems add more write actions and confirmations.

---

## Goals
- Prevent confirmation bias from overriding legitimate new requests.
- Make confirmation handling deterministic and safe:
  - confirm/cancel pending actions when the user is truly responding to the pending prompt
  - otherwise interpret the message normally (actions + RAG)
- Add a clear disambiguation UX when a message could reasonably mean either:
  - “cancel the pending action”, or
  - “cancel an order / cancel a subscription / cancel something else”

## Non-goals
- Rewriting intent extraction overall.
- Adding a full agent loop.
- Changing business semantics of actions (only improve routing/clarification).

---

## Current behavior (why it happens)
### Confirmation enrichment
When a pending action exists, the chat pipeline injects a confirmation-focused prompt block to the LLM. This heavily biases intent extraction toward `CONFIRMATION_*` types.

### Generic CONFIRMATION_NEGATIVE response
When a `CONFIRMATION_NEGATIVE` is processed, the orchestrator returns a generic message (“All sorted…”), even if the user’s message was actually a new request (e.g., “cancel order 2”).

---

## Proposed solution (layered)

### 1) Gate confirmation intents by “reply-like” heuristics (fail-closed)
Treat `CONFIRMATION_POSITIVE` / `CONFIRMATION_NEGATIVE` as valid only when:
- there is a pending action, AND
- the user message is a short reply-like confirmation (examples):
  - “yes”, “yep”, “confirm”, “do it”
  - “no”, “cancel”, “don’t”, “stop”
AND crucially:
- the message does **not** include additional domain arguments that suggest a new task:
  - identifiers/numbers (“order 2”, “PO-…”, “subscription 13”)
  - entity nouns (“order”, “shipment”, “ticket”, “coupon”)
  - extra clauses (“cancel order 2 and show my orders”)

If the message is not reply-like, do **not** treat it as confirmation even if the LLM output says so.

Result: “cancel order 2” should route to an ACTION like `cancel_purchase_order`, not to confirmation handling.

### 2) When ambiguous: ask a targeted disambiguation question
If there is a pending action and the user’s message includes “cancel/no/stop” *plus* domain arguments, respond with:
- “Do you want to cancel the pending action `<pendingAction>` or cancel order `<orderId>`?”

This is safer than guessing.

### 3) Improve confirmation context injection prompt
Adjust the confirmation context guidance to explicitly say:
- “If the message mentions cancelling an order/subscription/etc., treat it as a normal request; do not treat it as confirmation unless it is a direct yes/no reply.”

This reduces LLM misclassification frequency.

### 4) Clarify “pending action lifetime”
Confirmations should not linger indefinitely:
- keep the existing timeout (default 5 minutes) and ensure it’s clearly documented
- optionally surface a message when a pending action expired:
  - “That confirmation expired. What would you like to do now?”

### 5) Observability (debuggable behavior)
Add metadata/debug fields (behind a config flag) so product teams can see what happened:
- `chat.confirmationContextInjected=true|false`
- `chat.pendingAction=<name>`
- `chat.confirmationGateOutcome=ACCEPTED|REJECTED|DISAMBIGUATION`
- `chat.originalIntentType=<LLM output type>`

This makes “it ignored me” issues quickly diagnosable.

---

## Suggested UX patterns (optional but effective)
- When `CONFIRMATION_REQUIRED`, present explicit UI controls:
  - “Confirm” / “Cancel”
- Treat any non-button typed message as a new request unless it’s a strict yes/no reply.

This reduces accidental confirmations dramatically.

---

## Test plan
### Unit tests
Cases with a pending action:
- “yes” → confirms pending action
- “no” → cancels pending action
- “cancel order 2” → NOT confirmation; routes to normal intent handling
- “no cancel order 2” → triggers disambiguation (or normal handling), not silent cancel
- “cancel that” (no args) → cancels pending action

Cases without pending action:
- `CONFIRMATION_*` outputs should not produce “all sorted”; should be treated as a normal request or OUT_OF_SCOPE with guidance.

### Integration tests
End-to-end chat session:
- create pending action → send “cancel order 2” → ensure order cancel action executes (or asks required confirmation for that action)

---

## Rollout
1) Ship behind a feature flag (default off) to avoid behavior surprises:
   - `ai.chat.confirmation-gating.enabled=false`
2) Enable in one Real App demo and validate transcripts.
3) Document recommended UI patterns for confirmation actions.

---

## Acceptance criteria
- “cancel order 2” is no longer treated as a generic confirmation cancel when a pending action exists.
- Confirmation works reliably for short yes/no replies.
- Ambiguous messages trigger a clear disambiguation prompt.
- Debug metadata makes confirmation routing explainable.

