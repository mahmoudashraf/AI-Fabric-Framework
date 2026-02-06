# Actions: LLM‑Driven Param Extraction + Provenance Validation (No Backend Auto‑Fill) — Change Plan

## Status
Implemented

## Problem
We want a single, clear source of truth for action parameter extraction:
- **The LLM** proposes `action` + `actionParams`.
- The backend must remain production-safe and prevent execution on hallucinated/unsafe params.

Today, the pipeline can:
- Auto-fill missing params from pinned targets (`resolvedTargets`) in the backend.
- Validate “evidence” by checking whether param values appear in `processedQuery` (which may not include pinned context).

This creates:
- multiple sources of extraction (LLM + backend auto-fill),
- confusing behavior (“sku provided but still missing”),
- coupling to how prompts are serialized.

## Goals
1) **LLM is the only source of action param extraction.**  
   - Remove backend “auto-fill from pinned targets” behavior.
2) Backend remains safe and deterministic by enforcing:
   - required params are present
   - params have valid provenance: they must originate from
     - (a) the user’s message/history, or
     - (b) pinned targets/attachments (metadata/content)
3) Do not add shared “safe literal pattern” validation in core.
   - Action handlers own validation of param formats/constraints.

## Non‑goals
- Backward compatibility with prior auto-fill behavior (greenfield).
- Regex heuristics for emails/addresses/SKUs in framework core.
- Auto-resolving “which pinned target” when multiple are present.

---

## Proposed behavior

### 1) LLM proposes action + params
Intent extraction returns:
- `action` name (registered)
- `actionParams` map (may be partial)

The LLM prompt should explicitly instruct:
- Use pinned targets as authoritative context.
- When an action requires an identifier (sku/orderNumber/etc.), extract it from:
  - the user message/history **or**
  - pinned targets’ metadata/content
- Do not fabricate missing required params.

### 2) Backend enforces required params present
Before executing an action:
- If any required param is missing or blank:
  - return `CLARIFICATION_REQUIRED`
  - include `missingRequiredParameters`
  - preserve `providedParameters` for transparency

### 3) Backend enforces provenance (no “processedQuery contains it”)
Replace the current evidence check with a provenance check driven by explicit sources:

Accept a required string param value iff it is found in at least one of:
- **User text evidence:**
  - `originalQuery` (current user turn)
  - `historyMessages` (bounded chat window)
- **Pinned evidence:**
  - `attachmentsNormalized[].contentText`
  - `attachmentsNormalized[].metadata` (stringified values)
  - `resolvedTargets[].contentText`
  - `resolvedTargets[].metadata`

If a value is **not present** in any evidence source, treat it as missing and ask the user.

Notes:
- This is not “format validation”; it is “did this value appear in an allowed evidence source”.
- This removes reliance on `processedQuery` representation entirely.

### 4) Action handler validates formats/constraints
After provenance/required presence passes, the action handler is responsible for domain validation:
- invalid email/address/sku/quantity constraints
- referential existence (SKU exists, order exists, etc.)

If invalid:
- handler returns a structured `ActionResult` (or throws a typed exception) that the orchestrator maps to `CLARIFICATION_REQUIRED`
  - include `invalidParameters` + human message

---

## Implementation steps (framework)

### A) Remove backend auto‑fill from pinned targets
- Delete/disable `mergeResolvedTargetsIntoActionParams(...)` usage for required params.
- Ensure action execution uses only `intent.actionParams` (plus any explicit non-LLM additions like authentication context).

### B) Replace “evidenceLower.contains(value)” with provenance sources
Introduce a helper that builds a bounded `EvidenceBundle`:
- `userEvidenceText` (original query + recent history text)
- `pinnedEvidenceText` (attachments + resolvedTargets stringified)

Then for each required string param:
- reject placeholders/descriptions (keep existing placeholder guardrails)
- enforce `value` appears in either evidence blob

### C) Observability
Add debug metadata for action param validation:
- `actionParamValidation.missing=[...]`
- `actionParamValidation.provenanceMissing=[...]`
- `actionParamValidation.sourcesUsed={user:true,pinned:true,history:true}`

Keep it bounded and do not log full evidence text.

---

## Prompt updates
Update default/commerce curated prompts:
- Make it explicit that pinned targets/attachments are valid sources for parameter extraction.
- Remove guidance that implies backend will fill missing params.
- Encourage leaving params blank/omitted if not present in evidence.

---

## Tests
Unit tests:
1) When LLM returns an action with `sku` not present in user/history/attachments → `CLARIFICATION_REQUIRED` and `sku` missing.
2) When LLM returns `sku` and it exists in pinned attachments metadata/content → allow execution (provenance satisfied).
3) Placeholder values (“Shipping address (required)”, “email”, param name echoes) are treated as missing.

Integration (Real Apps):
4) “buy this” with a pinned product attachment containing sku → LLM must extract sku; backend accepts if value exists in pinned evidence.
5) Follow-up “my address is …” fills shippingAddress; backend accepts when it exists in user text evidence.

---

## Acceptance criteria
1) Backend does not modify action params (no auto-fill); the LLM is the only extractor.
2) Required params are enforced with provenance rules based on user/history + pinned targets.
3) No shared “safe literal pattern” heuristics exist in core; action handlers validate formats.
