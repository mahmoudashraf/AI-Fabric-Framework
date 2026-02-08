# Unified Intent Contract + Mode Translation (Prompt Simplification) — Plan

## Status
Proposed

## Problem
We currently have two prompt schemas/“contracts”:
- `FULL_CONTRACT` (LLM decides many orchestration flags)
- `MINIMAL_FOR_RAG` (LLM provides less; server forces deterministic behavior)

This creates:
- duplicated prompt maintenance
- ambiguous expectations for `requiresRetrieval` / `requiresGeneration` across modes
- inconsistent behavior when modes change but prompt contract changes too

## Goal
Adopt **one unified JSON contract** for intent extraction, and make the server’s resolved
`OrchestrationPolicy` (profile/mode) decide how to interpret it.

In other words:
- **Prompt contract is stable**
- **Mode translation is server-side and explicit**

## Non-goals
- Adding more LLM calls.
- Domain-specific heuristics.
- Backwards compatibility (greenfield).

---

## A) Unified contract (single JSON schema)
Keep one output schema across all modes. The model always returns these fields (some may be null):

- `type`: `ACTION | INFORMATION | OUT_OF_SCOPE | COMPOUND | CONFIRMATION_POSITIVE | CONFIRMATION_NEGATIVE`
- `intent`: string
- `confidence`: number [0..1]
- `action`: string (if ACTION)
- `actionParams`: object (if ACTION)
- `optimizedQuery`: string (required when INFORMATION and `requiresRetrieval=true`)
- `requiresRetrieval`: boolean (required for INFORMATION)
- `directAnswer`: string (required when INFORMATION and `requiresRetrieval=false`)
- `requiresGeneration`: boolean (optional signal; policy may override)
- `vectorSpace`: string (optional signal; policy/router may override)
- `needsAdvancedRAG`: boolean (optional signal; policy may override)
- `generationInstructions`: string (optional, e.g. post-action generation)
- `metadata.retrievalQueryHint`: string (optional)

Key simplification (LLM guidance):
- If you have enough information to answer **without** the indexed KB → `requiresRetrieval=false` and provide `directAnswer` (1 short sentence).
- Otherwise → `requiresRetrieval=true`, provide `optimizedQuery`, and do **not** provide `directAnswer`.

Strategy ownership + precedence (important):
- The intent-extraction LLM can emit an explicit “plan/strategy” signal (or it can be derived from `type` + `requiresRetrieval` + `requiresGeneration`).
- The orchestrator must treat any LLM-provided plan/strategy as a **hint** only.
- The effective plan is owned by server-side `OrchestrationPolicy` + hard pipeline rules (attachments/targets/confirmations safety), which may override LLM signals.

Grounding priority (to prevent “ignoring attachments/pins”):
- If the request includes **active attachments** and/or **pinned targets**, treat them as the **primary source of truth**.
- First, try to answer the user using only that authoritative context:
  - If sufficient → `requiresRetrieval=false` and respond via `directAnswer`.
  - If insufficient → `requiresRetrieval=true` so the system can retrieve additional KB context.
- Never ignore authoritative context and answer based on unrelated KB retrieval.

---

## B) Policy-driven interpretation (“mode translation”)
Introduce a deterministic translation layer that converts `(intent extraction output + request context)` into
an **effective** retrieval/generation plan, depending on `OrchestrationPolicy`.

### B.1 Translation inputs (authoritative)
- `OrchestrationPolicy.informationMode` (`LLM_DRIVEN` vs `DETERMINISTIC_RAG_GENERATE`)
- `OrchestrationPolicy.profile/mode/position`
- request context signals:
  - `activeAttachmentIdsResolved` (authoritative grounding)
  - pinned targets (session metadata reuse window)
  - pending action/confirmation state

### B.2 Translation outputs (effective plan)
For INFORMATION intents:
- `effectiveRequiresRetrieval` (boolean)
- `effectiveRequiresGeneration` (boolean)
- `effectiveVectorSpace` (string, possibly fan-out)
- `effectiveRetrievalQuery` (string)
- `effectiveGenerationQuery` (string)

### B.3 Mode-specific rules (examples)
#### Navigator (deterministic)
- Default: `effectiveRequiresRetrieval=true` and `effectiveRequiresGeneration=true`.
- Allow **opt-out** for trivial conversational turns:
  - if LLM returns `requiresRetrieval=false` **and** `directAnswer` is present **and**
    there is no pending action flow **and**
    there are no active attachments **that imply KB lookup** (see below) →
    return directAnswer without RAG.
- If `activeAttachmentIdsResolved` exists:
  - do **not** blindly skip RAG.
  - if query is attachment-contained (summarize/compare/specs of the selected target), allow directAnswer path.
  - if query requires KB policy/procedure (returns/refunds/shipping policies), keep RAG enabled.

#### Cart assistant (LLM driven)
- Respect LLM’s `requiresRetrieval` and `requiresGeneration` unless unsafe:
  - if user message is confirmation-related or requires missing params → do not skip required flows.

#### Support resolver (LLM driven, scoped)
- Respect LLM signals but prefer policy vector spaces (e.g. `policies`, `support`) when missing.

---

## C) Prompt cleanup (clear + short)
Update prompt instructions to match the unified contract and translation model:
- Describe `requiresRetrieval` with the simplified rule above.
- Make `directAnswer` mandatory when `requiresRetrieval=false`.
- Add an explicit “AUTHORITATIVE CONTEXT FIRST” rule:
  - Use active attachments / pinned targets first; only request retrieval when you can’t answer from them.
- Clarify that `vectorSpace` is optional and must come from KB overview when provided.
- Clarify that the server policy may override flags (especially in deterministic modes).

Also fix prompt numbering/duplication (currently multiple “9.” lines).

---

## D) Safety/precedence rules (must be consistent)
These rules should be enforced server-side (not left to the LLM):
- Active attachments resolve into `resolvedTargets` (authoritative).
- Stored pinned targets (session metadata reuse) resolve into `resolvedTargets` (authoritative).
- Active attachments override stored pinned targets.
- Active attachments / pinned targets should be treated as **authoritative** during generation; retrieved KB context is secondary.
- Pending confirmations/required parameters override “skip retrieval” signals.
- Deterministic modes override LLM-controlled generation/retrieval unless explicitly allowed (opt-out path).

---

## E) Implementation steps
1) Replace `FULL_CONTRACT` vs `MINIMAL_FOR_RAG` prompt split with one unified schema prompt.
2) Add a translation component (e.g. `InformationPlanResolver`) that produces effective plan fields.
3) Update `IntentHandlingStep.handleInformation` to use the translated plan instead of directly trusting intent flags.
4) Add tests per mode for:
   - trivial acknowledgements (no RAG)
   - attachment summarization (no RAG if sufficient)
   - attachment + policy question (RAG required)
   - pending confirmation turns (no skip)
5) Update curated pack docs to describe intended policy behaviors (navigator/cart/support).

---

## Acceptance criteria
- One prompt contract used across all modes.
- Deterministic mode performs fewer RAG calls for “thanks/ok/hi” and attachment-contained requests.
- Deterministic mode still performs RAG for attachment+policy questions (returns/refunds/shipping).
- When attachments/pinned targets already contain the answer (e.g., comparing priced items against a budget), the model does not “drift” to unrelated KB docs.
- Behavior is policy-driven and testable without prompt rewrites.
