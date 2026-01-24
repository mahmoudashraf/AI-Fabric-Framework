# Chat Capabilities Demo — Framework Assessment & Next Steps

## Purpose

This document captures what the current AI Fabric Framework can do today (as demonstrated by the `Real_Apps/chat-capabilities-demo` conversation) and identifies the next high‑impact framework improvements.

Scope:
- The **chat-capabilities demo app** under `Real_Apps/`.
- The **framework orchestration pipeline** used by the demo.
- The specific multi-turn behaviors shown in the provided chat transcript (recommendation → cart → checkout → cancel → retention offer).

Non-goals:
- Redesigning the demo business logic or data model.
- Implementing changes in code (this document is a design/assessment artifact).

---

## What the demo proves (capabilities that already work)

### 1) End-to-end multi-turn action execution with safety gates

The framework supports a deterministic “agentic action” loop:
1. Extract intent(s) (e.g., ACTION vs INFORMATION).
2. Validate action existence and permission (`validateActionAllowed`).
3. Validate required parameters (clarify if missing).
4. If the action requires confirmation, produce a **CONFIRMATION_REQUIRED** result and persist a pending action.
5. On user confirmation, execute action and return structured result data.

This is visible in the chat transcript:
- “ok add to my cart” → **Confirmation Required** → user confirms → **Action Executed** (added to cart)
- “create purchase order” → **Clarification Needed** (“provide: email”) → user supplies email → **Confirmation Required** → user confirms → **Action Executed**

### 2) Persistent state for “pending confirmation” and “missing parameters”

The framework persists two kinds of conversational state:
- **Pending action** (waiting for confirm/deny).
- **Action draft** (waiting for missing required parameters).

This enables robust continuation:
- The user can answer “email” after being asked, without restating the whole action.
- The user can confirm an action after being asked, without re-issuing the original command.

### 3) App-specific policy injection without forking the core framework

The retention flow demonstrates a clean extension point:
- When the user confirms cancel, the app intercepts and offers a discount first.
- The app can then handle accept/reject of the offer and either proceed or re-ask for cancellation.

This is the right boundary:
- **Framework**: deterministic orchestration, confirmation state machine, action execution contract.
- **App**: business rules (e.g., “offer a retention discount on cancel”).

### 4) UI-ready response contracts

The framework produces responses that are easy to render as UI states:
- `CLARIFICATION_REQUIRED` with missing fields
- `CONFIRMATION_REQUIRED` with a confirmation message and action metadata
- `ACTION_EXECUTED` with structured payload
- `INFORMATION_PROVIDED` with an answer and optional retrieved documents

This enables a front-end to implement the “Confirm/Cancel” UI, missing-field prompts, and structured displays (cart items, totals, order info).

---

## Architecture mapping (how the chat request flows)

### Request entrypoint (demo app)

The demo exposes a chat endpoint that:
- Builds an `OrchestrationContext` (conversationId, sessionId, userId).
- Delegates to the orchestrator: `orchestrator.orchestrate(query, context)`.

### High-level pipeline phases

While pipeline composition can vary by module/config, the demo behavior implies the following phases are in play:

1. **Conversation enrichment**
   - Adds history context.
   - If a pending action exists, injects a “confirmation context” to help the extractor interpret “yes/no”.
   - If an action draft exists, injects an “incomplete action context” to help the extractor interpret the user’s missing parameter reply.

2. **Intent extraction**
   - Produces `MultiIntentResponse` containing one or more intents (ACTION / INFORMATION / CONFIRMATION_POSITIVE / CONFIRMATION_NEGATIVE / etc.).

3. **Confirmation resolution (optional)**
   - Allows app-level and framework-level resolvers to rewrite the intent response based on pending confirmation state.

4. **Intent handling**
   - Routes intents:
     - `ACTION` → find handler, validate permission, validate params, confirm/execute
     - `INFORMATION` → retrieval + optional generation
     - `CONFIRMATION_*` → resolve pending action confirmation

---

## Chat transcript analysis (behavior-by-behavior)

### A) Product recommendation (“I need laptop for gaming”)

Observed:
- The assistant picks a gaming laptop from the provided context and justifies it.

What this shows:
- The framework supports “context-grounded” generation (a retrieval/generation or “context provided” mode).

What it does not show:
- Constraint handling (“any below price”) is not satisfied because the context contains only expensive options and no alternative inventory.

### B) Price constraint follow-up (“these are very expensive any below price.”)

Observed:
- The assistant responds that there are no other gaming laptops in context; no budget is provided; cannot identify options.

Interpretation:
- The current setup behaves like “answer from available context only”.
- There is no deterministic “budget clarification policy” for this type of request, and/or the knowledge base doesn’t contain cheaper options.

Opportunity:
- For commerce-like domains, the best UX is to ask:
  - “What is your max budget and currency?”
  - “Any preferred screen size / GPU tier?”
  - Then run a **catalog search** (not only narrative RAG).

### C) Switching intent (“I need laptop for programming.”)

Observed:
- The assistant selects the non-gaming laptop as better value for programming.

What this shows:
- Intent switching works and does not force the user to remain on the previous intent.

### D) Confirmable cart action (“ok add to my cart”)

Observed:
- Confirmation required → user confirms → action executed → cart totals returned.

What this shows:
- **Action confirmation gating** works.
- The action returns structured fields that are UI-friendly.

### E) Missing parameter completion (“create purchase order” → needs email)

Observed:
- Clarification needed: “provide email.”
- User provides email.
- Framework asks confirmation and proceeds.

What this shows:
- **Action draft** + **conversation enrichment** can close missing slots over multiple turns.

### F) Cancel order + retention offer

Observed:
- User asks to cancel.
- System asks for `orderNumber`.
- User provides “2” and the system proceeds to confirmation.
- After confirm, system offers a 10% discount retention flow.

What this shows:
- The app can inject business policy via a resolver (retention offer).

Key nuance:
- “order number” vs “order id” ambiguity exists; the assistant asked for “orderNumber”, but the user replied with “2”.
  - The system can still proceed if the extractor maps “2” into an acceptable identifier field.
  - However, the underlying metadata requirements should ideally express “orderNumber OR orderId”.

---

## Key gaps & why they matter

### 1) “AnyOf” required parameter semantics (A OR B)

Problem:
- Some actions accept multiple alternative identifiers:
  - Example: cancel order by `orderNumber` (PO-...) **or** `orderId` (numeric).
- Today, the framework’s metadata supports only a flat `requiredParameters` set, which encodes **AND** semantics.

Impact:
- The assistant may ask for the “wrong” required field even when the user already provided a valid alternative.
- This creates unnecessary clarification loops and reduces trust.

### 2) Constraint follow-up for commerce recommendations (budget, filters)

Problem:
- “Any below price?” is a high-frequency follow-up in commerce scenarios.
- If the system can’t find cheaper items, it should ask for a budget and then search with filters (or explain inventory constraints).

Impact:
- The experience feels like a dead-end even though the correct next step is simple (ask budget; filter inventory).

### 3) Parameter validation and normalization

Problem examples from the transcript:
- Email typo: `engmahmoud@gamil.com` (likely intended `gmail.com`) is accepted.
- Identifier ambiguity: “2” could be order id; “PO-...” is order number.

Impact:
- The system can execute actions with invalid inputs or ambiguous identifiers.
- Downstream services must handle avoidable validation errors.

---

## Proposed framework enhancements (detailed)

### Enhancement 1: Add “requiredAnyOf” groups to action metadata

Goal:
- Support “at least one of these params must be provided” in a deterministic, provider-agnostic way.

Proposed metadata model (minimal additive change):
- Keep existing:
  - `requiredParameters`: Set<String> (AND semantics)
- Add:
  - `requiredAnyOf`: List<List<String>> where each inner list is an OR group

Examples:
- Cancel order:
  - requiredAnyOf: `[["orderNumber", "orderId"]]`
- Track shipment:
  - requiredParameters: `["carrier"]`
  - requiredAnyOf: `[["trackingNumber", "orderNumber"]]`

Execution semantics:
- Missing list = missing all `requiredParameters` plus any OR groups where none are supplied.
- Clarification message should present OR groups naturally:
  - “To proceed, please provide: orderNumber or orderId.”

Backwards compatibility:
- Existing actions remain unchanged.
- Existing extractors can ignore `requiredAnyOf` until upgraded; the framework can still enforce it deterministically.

Testing plan:
- Unit tests for:
  - Missing OR group → CLARIFICATION_REQUIRED
  - Providing either member → no missing
  - Placeholder “metadata description copied into params” still treated as missing

### Enhancement 2: Add typed parameter constraints (optional, phased)

Once `requiredAnyOf` exists, the next common need is type/format validation:
- Regex pattern: `orderNumber` matches `^PO-[A-Za-z0-9-]+$`
- Numeric range: `quantity >= 1`
- Email format: basic RFC-like validation (or “@” + domain)

Where to enforce:
- Prefer enforcement at the framework boundary to avoid inconsistent handler behavior.
  - Option A: extend `AIActionMetaData` with `parameterSchema` (JSON-schema-like)
  - Option B: add a new interface `ActionParamValidator` (per action) that the framework calls before confirmation/execution

Recommendation:
- Start with Option B (simpler and less schema machinery), then evolve to schema if needed.

### Enhancement 3: Budget-aware follow-up for commerce-like INFORMATION intents

Goal:
- When a user asks for “cheaper/below price/under budget” but provides no number, the assistant should ask for a budget.

Implementation options:
1. **Policy in core IntentHandlingStep**
   - Deterministic keyword-based check on the user query.
   - Emit `CLARIFICATION_REQUIRED` asking for max budget and currency.
   - Pros: simple, immediate UX win.
   - Cons: domain-specific; might not belong in core.

2. **Pluggable “clarification policy” interface**
   - `ClarificationPolicy` with a priority order, similar to resolvers.
   - App can register commerce policies (budget, preferred brand, etc.).
   - Pros: keeps core generic; encourages app-specific UX tuning.
   - Cons: slightly more framework surface area.

Recommendation:
- Prefer option (2) long-term, but option (1) can be a fast prototype if you accept a small amount of domain logic in core.

### Enhancement 4: Catalog search as an action (filtered inventory), not only narrative RAG

For shopping flows, “recommendation” should often be:
- deterministic product search + structured results
- optionally followed by generation (“explain why these match”)

Proposed pattern:
- `search_products` action accepts:
  - `query` (keyword)
  - `maxPrice` (optional)
  - `minPrice` (optional)
  - `category` (optional)
  - `limit` (optional)
- The assistant uses the action result as the source of truth for what’s in inventory.

This avoids the “context is incomplete” dead end.

---

## Suggested implementation plan (phased)

### Phase 1 (high value, low risk)
- Add `requiredAnyOf` to metadata + enforcement in missing-param detection.
- Update demo handlers that need OR semantics (cancel order, order details, tracking).
- Add unit tests.

### Phase 2 (improve data quality & trust)
- Introduce parameter validation hooks (email, ids, patterns).
- Add “normalization helpers” (e.g., interpret “2” as orderId if numeric).

### Phase 3 (commerce UX)
- Add clarifications as a pluggable policy.
- Add filtered catalog search action parameters.
- Add vector-db metadata filtering support (if using embeddings for catalog search).

---

## Acceptance criteria

When the user says “cancel this order” after “Order Id: 2” was shown:
- The assistant should ask for “orderNumber or orderId” (not strictly “orderNumber”).
- If user replies “2”, the action should proceed without additional clarification.

When the user says “any cheaper?” without a number:
- The assistant should ask “What’s your max budget and currency?”
- After budget is provided, it should search/filter inventory accordingly (action-driven, not hallucinated).

---

## Notes

The demo already demonstrates the most important foundation: deterministic orchestration with stateful confirmation and clarification flows, plus a clean app-policy injection point. The next improvements are mostly about expressing richer action parameter requirements and handling common commerce constraints (budget/filters) in a structured way.

